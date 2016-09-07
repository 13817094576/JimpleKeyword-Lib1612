package edu.fudan.JimpleKeyword;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.util.queue.QueueReader;

/**

	This class contains code for inspecting keywords in Jimple statement.
	
	If the Jimple statement is the one we interested in,
	related information will be recorded for later use.

 */

class KeywordInspector 
{
	//
	// Data list for Jimple statement inspection
	private KeywordList keywordList;
	private InterestedApiList interestedApiList;
	private LibrariesList librariesList;
	
	//
	// Some utilities for keyword inspection
	private WordSplitter wordSplitter;
	private PorterStemmer porterStemmer;
	
	//
	// Output statistic information
	
	// We use List since Jimple statements doesn't seem to duplicate
	private List<String> jimpleWithKeywords;
	// We use Set to avoid duplicated keywords
	private Set<String> keywordsHit;
	// We use Set to avoid duplicated <package, keyword> pair
	private Set<String> keywordsInPackage;
	// Jimple doesn't seem to duplicate
	private List<String> jimpleUsingHashMap;
	// Record info on Jimple statement hit for inspecting root classes
	private List<JimpleHit> jimpleHit;
	
	// Data block statements for output
	private List<String> dataBlockStatement;
	// Data block raw statements for further processing
	private List<DataBlockRawStat> dataBlockRawStat;
	// The raw statements in data blocks which contain keywords
	private List<DataBlockRawStat> dataBlockWithKeywordsRawStat;
	// Data block with keywords may hit multiple times.
	// There may be multiple statements in a data block containing keywords
	private Set<String> dataBlockWithKeywordsIds;
	
	private Set<String> libraryPackageName;
	
	private Set<String> keywordsInAppPackage;
	private Set<String> keywordsInLibPackage;
	
	//
	// Enumeration on Jimple statement status
	// on initial quick judgement
	enum JimpleInitialJudgeStatus
	{
		JIMPLE_NOT_INTERESTED,
		JIMPLE_NEED_DETAIL_INSPECTION,
		JIMPLE_DEFINITE_HIT
	}
	
	/**

	 	Extract string constants in a given jimple statement
	 	
	 	If there is no string constants, an empty list is returned.

	 */
	private List<String> extractStringConst(String jimpleInString)
	{
		//
		// Scan the Jimple statement in String
		
		int stringConstBegin = 0;
		int stringConstEnd = 0;
		List<String> stringConsts = new ArrayList<String>();
		
		for (int i=0; i<jimpleInString.length(); i++)
		{
			//
			// Double quote indicate either begin or end of a string constant
			if (jimpleInString.charAt(i) == '\"')
			{
				// Record the beginning of a string constant
				if (stringConstBegin == 0)
				{
					stringConstBegin = i;
				}
				// This is an end of a string constant
				else
				{
					stringConstEnd = i;
					
					// Extract the string constant
					// Here we use begin+1 to skip to leading '\"'
					// The trailing '\"' is excluded by String.substring method
					String stringConst = jimpleInString.substring(stringConstBegin + 1, stringConstEnd);
					
					stringConsts.add(stringConst);
					
					// Reset string const begin variable
					stringConstBegin = 0;
				}
			}
			//
			// Backslash indicates escape character
			else if (jimpleInString.charAt(i) == '\\')
			{
				//
				// Ensure that char i+1 also exists
				assert i+1 < jimpleInString.length();
				
				// An \" escape sequence is encountered
				if (jimpleInString.charAt(i+1) == '\"')
				{
					// We should skip the escaped double quote char
					i++;
				}
			}
			else
			{
				// No special action is needed for other char.
				// Inspect next char directly
			}
		}
		
		return stringConsts;
	}
	
	private String canonicalizeStringConst(String stringConst)
	{
		//
		// Process string const with words splitting
		List<String> wordsInStrConst = wordSplitter.splitWords(stringConst);
		
		//
		// Use Porter Stemmer to do stemming
		for (int i=0; i<wordsInStrConst.size(); i++)
		{
			// Only stem word longer than 4 chars
			String word = wordsInStrConst.get(i);
			String stemmedWord = word.length() > 4 ?
					porterStemmer.stripAffixes(word) : word;
					
			// Save stemmed word
			wordsInStrConst.set(i, stemmedWord);
		}
		
		// Re-join words to build canonicalized string const
		String canonicalizedStrConst = StringUtil.joinString(wordsInStrConst, ' ');
		
		//
		// Convert canonicalized str const to lower case to ignore case
		canonicalizedStrConst = canonicalizedStrConst.toLowerCase();
		
		return canonicalizedStrConst;
	}
	
	private String figureOutKeywordInStrConst(String stringConst)
	{
		//
		// Canonicalize string const with word splitting, stemming, etc.
		// The string const is converted to lower case to ignore case
		String canonicalizedStrConst = canonicalizeStringConst(stringConst);
		
		// Check if current string const contains keyword
		String keywordInStringConst = keywordList.figureOutKeyword(canonicalizedStrConst);
		if (keywordInStringConst != null)
		{
			//
			// Check that the keyword is an complete word
			// instead of char sequence appear in the middle of
			// another word
			// The string const has already converted to lower case
			int locOfKeyword = canonicalizedStrConst.indexOf(keywordInStringConst);
			if (locOfKeyword == 0 						// Either the keyword is at the beginning of the string
				|| canonicalizedStrConst.charAt(locOfKeyword - 1) == ' ')		// Or it's an complete word
			{
				return keywordInStringConst;
			}
		}
		
		//
		// Current string const doesn't contain any keyword
		// or keyword is the one we don't want.
		return null;
	}
	
	/**
	 
		Figure out if given Jimple statement contains keyword we interested
		Currently, we only focused on keyword in String constant.

	 */
	private String figureOutKeywordInJimple(String jimpleInString)
	{
		// We only interested in keywords in string constants.
		List<String> stringConsts = extractStringConst(jimpleInString);
		
		//
		// Check each string const
		// CORNER CASE: when the list is empty, the code is still correct
		for (String stringConst : stringConsts)
		{			
			String keywordInStrConst = figureOutKeywordInStrConst(stringConst);
			if (keywordInStrConst != null)
			{
				return keywordInStrConst;
			}
		}
		
		// None of the string consts in current Jimple statement
		// contains keyword
		return null;
	}
	
	/**

		Judge if a given invoke stmt related to key-value operations.
		
		We check that if the invoked method has 2 parameters and 
		the type of the first parameter is String.

	 */
	private boolean isInvokeStmtContainKeyValue(Unit unit)
	{
		//
		// Check if current invoke statement has 2 arguments
		// and the first one is string.
		// If so, we think this statement is interesting
		InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
		
		//
		// Skip special invoke statements
		// Key-Value pair doesn't likely appear in special invokes
		if (invokeExpr instanceof SpecialInvokeExpr)
		{
			return false;
		}
		
		// Check if the method invoked has 2 arguments
		if (invokeExpr.getArgCount() == 2)
		{
			// Check if the type of first argument is String
			String typeOfFirstArg = invokeExpr.getArg(0).getType().toString();
			if (typeOfFirstArg.equals("java.lang.String"))
			{
				return true;
			}
		}
		
		//
		// Current invoke stmt doesn't satisfy specified conditions
		return false;
	}
	
	/**
	 
		This function performs inital Jimple statement filtering.
		
		The filter phases in this function must perform quickly,
		since this function will check every Jimple statement in the app.

	 */
	private JimpleInitialJudgeStatus judgeJimpleInitially(Unit unit)
	{
		//
		// We only interested in Jimple statement
		// which invokes certain API 
		if (!(unit instanceof InvokeStmt))
		{
			// Skip non-invoke Jimple statement
			return JimpleInitialJudgeStatus.JIMPLE_NOT_INTERESTED;
		}		
		
		//
		// Check if current invoke statement has 2 arguments
		// and the first one is string.
		// If so, we think this statement is interesting
		if (isInvokeStmtContainKeyValue(unit))
		{
			return JimpleInitialJudgeStatus.JIMPLE_DEFINITE_HIT;
		}
		
		String unitInString = unit.toString();
		
		//
		// Check if current Jimple statement invokes
		// API we interested in
		if (Config.interestedApiOnly)
		{
			if (!interestedApiList.containInterestedApi(unitInString))
			{
				// Skip Jimple statement that doesn't contain interested API
				return JimpleInitialJudgeStatus.JIMPLE_NOT_INTERESTED;
			}
		}
		
		//
		// Given Jimple statement has passed initial quick screening conditions
		return JimpleInitialJudgeStatus.JIMPLE_NEED_DETAIL_INSPECTION;
	}
	
	/**

		This function performs slow steps of Jimple statement filtering.
		
		The Jimple statement passed in should be filtered in some extent in advance.
		If using this function to check every Jimple statement,
		the program will run very slow.

	 */
	private boolean judgeJimpleInDetail(String unitInString)
	{
		//
		// Check if current API invoked is in the libraries list
		// The libraries list is long,
		// so the matching process is time consuming.
		if (Config.apiInLibrariesOnly)
		{
			if (!librariesList.containLibPackageName(unitInString))
			{
				// Skip Jimple statement that invokes API not in libraries list
				return false;
			}
		}
		
		//
		// Given Jimple statement has passed all screening conditions
		return true;
	}
	
	private boolean isStatementUsingHashMap(String unitInString)
	{
		//
		// Here we only use "HashMap" since HashMap is an interface
		// There LinkedHashMap etc.
		if (unitInString.contains("HashMap")
				&& (unitInString.contains("put(") || unitInString.contains("get(")))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	// Integer in string matcher
	// Not needed to initialize multiple times
	Pattern intPattern = Pattern.compile("[1-9][0-9]*");
	
	/**

		Given statement contains key-value pair operation.
		
		Record the given statement in corresponding data block.
		and return the IDs of data block objects.

	 */
	private List<String> recordStatementInDataBlock(Unit curUnit, String curUnitInString, String curClassName, int unitNum)
	{
		//
		// Get this parameter of invoke expression
		InvokeExpr invokeExpr = ((InvokeStmt)curUnit).getInvokeExpr();
		Value thisArg = null;
		if (invokeExpr instanceof InstanceInvokeExpr)
		{
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr)invokeExpr;
			thisArg = instanceInvokeExpr.getBase();
		}
		else
		{
			//
			// Skip invoke statement without this pointer
			return null;
		}
		
		//
		// Find out the potential values of this argument
		PointsToAnalysis pointToAnalysis = Scene.v().getPointsToAnalysis();
		PointsToSet thisValue = null;
		if (thisArg instanceof Local)
		{
			thisValue = pointToAnalysis.reachingObjects((Local)thisArg);
		}
		else if (thisArg instanceof SootField)
		{
			thisValue = pointToAnalysis.reachingObjects((SootField)thisArg);
		}
		
		if (thisValue == null || thisValue.isEmpty())
		{
			return null;
		}
		
		//
		// Extract the alloc node num from PointsToSet
		List<String> thisObjIdList = new ArrayList<String>();
		String thisInStr = thisValue.toString();
		Matcher intMatcher = intPattern.matcher(thisInStr);
		while (intMatcher.find())
		{
			// Find out the object ID of "this" pointer
			String thisObjId = intMatcher.group();
			
			// Record the data block object ID
			// and it will be returned later
			thisObjIdList.add(thisObjId);
			
			// Record current statement in data block statement list
			String statement = String.format("%s,%d,%s,%s", 
					thisObjId, unitNum, curClassName, curUnitInString);		
			dataBlockStatement.add(statement);

			// Record raw statement for further processing
			DataBlockRawStat rawStat = new DataBlockRawStat();
			rawStat.dataBlockId = thisObjId;
			rawStat.statement = curUnit;
			dataBlockRawStat.add(rawStat);
		}
		
		return thisObjIdList;
	}
	
	/**

		Inspect Jimple statements using HashMap class
		and record relating information

	 */
	private void inspectHashMapStatement(Unit curUnit, String curUnitInString)
	{
		//
		// Skip parameters validation currently.
		
		//
		// Record statement using HashMap if needed
		if (Config.recordJimpleUsingHashMap)
		{
			jimpleUsingHashMap.add(curUnitInString);
		}
	}
	
	/**
	 
		Inspect given Jimple statement
		and record relating information if
		the statement is the one we interested in

	 */
	private void inspectJimpleStatement(Unit curUnit, SootClass curClass)
	{		
		//
		// Check the Jimple statement is the one
		// we interested in initially and quickly
		JimpleInitialJudgeStatus initialJudgeStatus = judgeJimpleInitially(curUnit);
		if (initialJudgeStatus == JimpleInitialJudgeStatus.JIMPLE_NOT_INTERESTED)
		{
			// We don't interested in current Jimple statement
			// Skip it
			return;
		}
		
		String curUnitInString = curUnit.toString();
		
		// Record current Jimple statement
		IntTag unitNumTag = (IntTag)(curUnit.getTag("unitNum"));
		
		//
		// Perform extra actions on statements using HashMap
		if (isStatementUsingHashMap(curUnitInString))
		{
			inspectHashMapStatement(curUnit, curUnitInString);
		}
		
		//
		// Record key-value invocation in on the same data block instance
		List<String> dataBlockObjIdList = null;
		if (isInvokeStmtContainKeyValue(curUnit))
		{
			dataBlockObjIdList = recordStatementInDataBlock(curUnit, curUnitInString, curClass.getName(), unitNumTag.getInt());
		}
		
		// Check if current statement contains any known keyword
		String keywordInUnit = figureOutKeywordInJimple(curUnitInString);
		if (keywordInUnit == null)
		{
			// Skip Jimple statement without keyword
			return;
		}
		
		//
		// Supplement detailed inspection
		if (initialJudgeStatus == JimpleInitialJudgeStatus.JIMPLE_NEED_DETAIL_INSPECTION)
		{
			if (!judgeJimpleInDetail(curUnitInString))
			{
				// Skip statements not pass detailed inspection 
				return;
			}
		}
		
		// Jimple with keywords line format:
		// Jimple ID, keyword, package name, Jimple statement
		String jimpleWithKeywordsLine = String.format(
				"%d,%s,%s,%s", 
				unitNumTag.getInt(), 
				keywordInUnit, 
				curClass.getPackageName(), 
				curUnitInString);
		
		jimpleWithKeywords.add(jimpleWithKeywordsLine);
		
		JimpleHit jimpleHitInst = new JimpleHit();
		jimpleHitInst.jimple = curUnit;
		jimpleHitInst.keyword = keywordInUnit;
		jimpleHitInst.keywordUnitNum = unitNumTag.getInt();
		jimpleHit.add(jimpleHitInst);
		
		// Record current keyword
		keywordsHit.add(keywordInUnit);
		
		//
		// Record the keywords and its corresponding package
		// Here we record package name as key
		// since a keyword may appears in multiple packages
		String curPackageName = curClass.getPackageName();
		String keywordInPackageLine = curPackageName + ',' + keywordInUnit;
		keywordsInPackage.add(keywordInPackageLine);
		if (curPackageName.startsWith(Main.apkCompanyId))
		{
			keywordsInAppPackage.add(keywordInPackageLine);
		}
		else
		{
			keywordsInLibPackage.add(keywordInPackageLine);
		}
		
		//
		// Record the statements with keywords in data blocks
		if (dataBlockObjIdList != null)
		{
			dataBlockWithKeywordsIds.addAll(dataBlockObjIdList);
		}
	}

	/**
		 
		Scanning the Jimple statements which are though reachable by FlowDroid,
		and record the information we care.
		
		THIS METHOD IS OBSOLETE.
		Inspect the code of this method carefully before using.
	
	 */
	private void scanJimpleReachableOnly()
	{
		//
		// Check assumptions
		assert keywordList != null;		
		
		//
		// Traverse the reachable method in APK
		QueueReader<MethodOrMethodContext> methodIter = Scene.v().getReachableMethods().listener();
		while (methodIter.hasNext())
		{
			SootMethod m = methodIter.next().method();
				
			// Skip method without active body
			if (!SootUtil.ensureMethodActiveBody(m))
			{
				continue;
			}
			
			//
			// Traverse the statements in a method
			Iterator<Unit> unitIter = m.getActiveBody().getUnits().iterator();
			while (unitIter.hasNext())
			{
				Unit curUnit = unitIter.next();

				// Inspect current Jimple statement
				// and recording relating info if we interested in
				inspectJimpleStatement(curUnit, m.getDeclaringClass());
			}
		}
	}
	
	//
	// Some fixed system packages to exclude
	static String[] fixedSystemPackages = { "java.", "dalvik.", "android.", "javax." };
	
	private boolean isSystemPackage(String packageName)
	{
		//
		// Check if given package name starts with
		// some known system package name
		for (String curSystemPackages : fixedSystemPackages)
		{
			if (packageName.startsWith(curSystemPackages))
			{
				return true;
			}
		}
		
		//
		// Given package name doesn't start with
		// any known system package name
		return false;
	}
	
	/**
	 
		Inspect the package name record relating info
	
	*/
	private void inspectPackageName(String packageName)
	{
		//
		// Canonicalize package name
		packageName = packageName.trim();
		
		//
		// Skip empty package name
		if (packageName.isEmpty())
		{
			return;
		}
		
		//
		// Skip package from the same company of the app
		if (packageName.startsWith(Main.apkCompanyId))
		{
			return;
		}
		
		//
		// We only expect the leading 3 parts of package name
		String libPackage = SootUtil.getLeadingPartsOfName(packageName, 3);
		
		//
		// Record package name
		libraryPackageName.add(libPackage);
	}
	
	/**
	 
		Scanning the classes with FlowDroid
		and record the information we care.

	 */
	private void scanJimple()
	{
		//
		// Check assumptions
		assert keywordList != null;		
		
		//
		// Traverse the classes in APK
		int unitNum = 0;					// Unique ID for each Jimple statement
		
		Iterator<SootClass> classIter = Scene.v().getClasses().iterator();
		while (classIter.hasNext())
		{
			SootClass curClass = classIter.next();
			
			//
			// Skip system packages
			String curPackageName = curClass.getPackageName();
			if (isSystemPackage(curPackageName))
			{
				continue;
			}
			
			//
			// Record package name for statistics on package in app
			inspectPackageName(curPackageName);
			
			//
			// Traverse the methods in a class
			Iterator<SootMethod> methodIter = curClass.getMethods().iterator();
			while (methodIter.hasNext())
			{
				SootMethod m = methodIter.next();
					
				// Skip method without active body
				if (!SootUtil.ensureMethodActiveBody(m))
				{
					continue;
				}
				
				//
				// Traverse the statements in a method
				Iterator<Unit> unitIter = m.getActiveBody().getUnits().iterator();
				while (unitIter.hasNext())
				{
					Unit curUnit = unitIter.next();
					
					//
					// Set unitNum tag for current Jimple statement
					curUnit.addTag(new IntTag("unitNum", unitNum));
					unitNum++;

					// Inspect current Jimple statement
					// and recording relating info if we interested in
					inspectJimpleStatement(curUnit, curClass);
				}
			}
		}
	}
	
	/**

		Initialize InterestedApiList class instance.
		
		If Config.interestedApiOnly is turned off, or 
		config file doesn't exist, null is returned.

	 */
	private InterestedApiList initInterestedApiList()
	{
		if (Config.interestedApiOnly)
		{
			//
			// Check if InterestedAPIs.txt exists
			File interestedApiListFile = new File(Config.CONFIG_FILE_INTERESTED_API);
			if (interestedApiListFile.isFile())
			{
				// Return initialized InterestedApiList instance
				return new InterestedApiList();
			}
			else
			{
				// Interested API list doesn't exist, 
				// Turn off Config.interestedApiOnly switch
				Config.interestedApiOnly = false;
				
				// Print warning message
				System.err.println("[WARN] Interested API list is missing, API filtering is disabled: "
									+ Config.CONFIG_FILE_INTERESTED_API);
				
				return null;
			}
		}
		else
		{
			// Config.interestedApiOnly switch is turned off
			return null;
		}
	}
	
	/**
	
		Initialize LibrariesList class instance.
		
		If Config.apiInLibrariesOnly is turned off, or 
		config file doesn't exist, null is returned.
	
	 */
	private LibrariesList initLibrariesList()
	{
		if (Config.apiInLibrariesOnly)
		{
			//
			// Check if CommonLibraries.txt exists
			File librariesListFile = new File(Config.CONFIG_FILE_LIBRARIES_LIST);
			if (librariesListFile.isFile())
			{
				// Return initialized LibrariesList instance
				return new LibrariesList();
			}
			else
			{
				// Libraries list doesn't exist, 
				// Turn off Config.apiInLibrariesOnly switch
				Config.apiInLibrariesOnly = false;
				
				// Print warning message
				System.err.println("[WARN] Libraries list is missing, API in libraries only filtering is disabled: "
									+ Config.CONFIG_FILE_LIBRARIES_LIST);
				
				return null;
			}
		}
		else
		{
			// Config.apiInLibrariesOnly switch is turned off
			return null;
		}
	}

	private List<DataBlockRawStat> pickOutRawStatInDataBlocksWithKeywords()
	{
		// Initialize result list
		List<DataBlockRawStat> rawStatList = new ArrayList<DataBlockRawStat>();
		
		//
		// Scan the data block statements list
		// and pick out the statements with keywords
		for (DataBlockRawStat dataBlockStat : dataBlockRawStat)
		{
			for (String dataBlockWithKeywordsId : dataBlockWithKeywordsIds)
			{
				if (dataBlockStat.dataBlockId.equals(dataBlockWithKeywordsId))
				{
					rawStatList.add(dataBlockStat);
				}
			}
		}
		
		return rawStatList;		
	}
	
	KeywordInspector(KeywordList keywordList)
	{
		
		//
		// Initialize data list for Jimple inspection
		this.keywordList = keywordList;
		interestedApiList = initInterestedApiList();
		librariesList = initLibrariesList();

		//
		// Initialize utilities
		wordSplitter = new WordSplitter(keywordList.getDictForWordSplit());
		porterStemmer = new PorterStemmer();
		
		//
		// Initialize output information variables
		keywordsInPackage = new HashSet<String>();
		jimpleWithKeywords = new ArrayList<String>();
		jimpleHit = new ArrayList<JimpleHit>();
		keywordsHit = new HashSet<String>();
		jimpleUsingHashMap = new ArrayList<String>();
		libraryPackageName = new HashSet<String>();
		
		keywordsInAppPackage = new HashSet<String>();
		keywordsInLibPackage = new HashSet<String>();
		
		dataBlockStatement = new ArrayList<String>();
		dataBlockRawStat = new ArrayList<DataBlockRawStat>();
		dataBlockWithKeywordsIds = new HashSet<String>();
		
		//
		// Scan Jimple statements
		// and record the information we interested in
		scanJimple();
		
		//
		// Pick out raw statements in data blocks which have keywords
		// for further processing
		dataBlockWithKeywordsRawStat = pickOutRawStatInDataBlocksWithKeywords();
		
		//
		// Process output info
		Collections.sort(dataBlockStatement);
	}
	
	//
	// Output information access methods
	
	List<String> getJimpleWithKeywords()
	{
		return jimpleWithKeywords;
	}
	
	List<JimpleHit> getJimpleHit()
	{
		return jimpleHit;
	}
	
	Set<String> getKeywordsHit()
	{
		return keywordsHit;
	}
	
	Set<String> getKeywordsInPackage()
	{
		return keywordsInPackage;
	}
	
	List<String> getJimpleUsingHashMap()
	{
		return jimpleUsingHashMap;
	}
	
	Set<String> getLibraryPackageName()
	{
		return libraryPackageName;
	}
	
	Set<String> getKeywordsInAppPackage()
	{
		return keywordsInAppPackage;
	}
	
	Set<String> getKeywordsInLibPackage()
	{
		return keywordsInLibPackage;
	}
	
	List<String> getDataBlockStatement()
	{
		return dataBlockStatement;
	}
	
	List<String> getDataBlockWithKeywords()
	{
		// Initialize result list
		List<String> dataBlockStatWithKeywords = new ArrayList<String>();
		
		//
		// Scan the data block statements list
		// and pick out the statements with keywords
		for (String statement : dataBlockStatement)
		{
			for (String dataBlockWithKeywordsId : dataBlockWithKeywordsIds)
			{
				if (statement.startsWith(dataBlockWithKeywordsId))
				{
					dataBlockStatWithKeywords.add(statement);
				}
			}
		}
		
		return dataBlockStatWithKeywords;
	}

	/**
	 
		Return the raw key argument of a key-value pair operation

	 */
	private String extractKeyArgOfStat(Unit unit)
	{
		//
		// Here we assume that "unit" is an invocatin of
		// key-value pair operation
		
		InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
		String firstArgInStr = invokeExpr.getArg(0).toString();
		
		return firstArgInStr;
	}
	
	/**
	 
		Extract the value of key argument and 
		return the value only if the value is a keyword

	 */
	private String extractValidKeyArgOfStat(Unit unit)
	{
		String firstArgInStr = extractKeyArgOfStat(unit);
		
		//
		// Remove the empty space in argument
		firstArgInStr = firstArgInStr.trim();
		
		//
		// Check that if the arg is a constant
		if (firstArgInStr.charAt(0) == '$')
		{
			return null;
		}

		//
		// If the argument contain comma,
		// The it's much likely to be a sentence instead of a keyword
		if (firstArgInStr.contains(","))
		{
			return null;
		}
		
		//
		// Check if the argument is an empty string
		if (firstArgInStr.isEmpty())
		{
			return null;
		}
		
		return firstArgInStr;
	}
	
	/**

		Generate the statistics on the keywords used by data blocks.
		
		And output the result list in descending order.

	 */
	Map<String, Integer> getKeywordsInDataBlocks()
	{
		//
		// We assume that the dataBlockWithKeywordsRawStat list is initialized
		//
		// This list should be initialized in the constructor of this class
		assert dataBlockWithKeywordsRawStat != null;
		
		//
		// Initialize output statistic variables
		Map<String, Integer> keywordsStat = new HashMap<String, Integer>();
		
		for (DataBlockRawStat rawStat : dataBlockWithKeywordsRawStat)
		{
			//
			// Extract the first argument in statement
			// Here we assume that the statement are all key-value pair operations
			//
			// We only interested in constant value key
			String firstArgInStr = extractValidKeyArgOfStat(rawStat.statement);
			if (firstArgInStr == null)
			{
				continue;
			}
			
			//
			// Increment the counter for keywords
			if (keywordsStat.containsKey(firstArgInStr))
			{
				Integer keywordCounter = keywordsStat.get(firstArgInStr);
				keywordCounter++;
				keywordsStat.put(firstArgInStr, keywordCounter);
			}
			else
			{
				keywordsStat.put(firstArgInStr, 1);
			}
		}
		
		//
		// Sort the keywords stat in descending order
		
		List<Entry<String, Integer>> keywordsStatEntryList = new ArrayList<Entry<String, Integer>>();
		keywordsStatEntryList.addAll(keywordsStat.entrySet());
		
		Collections.sort(keywordsStatEntryList, new Comparator<Entry<String, Integer>>() {

			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) 
			{
				// We sort the list in descendant order
				return -(o1.getValue() - o2.getValue());
			}
			
		});
		
		//
		// Put sorted keywords stat to a new map
		
		Map<String, Integer> resultKeywordsStat = new LinkedHashMap<String, Integer>();
		for (Entry<String, Integer> keywordsStatEntry : keywordsStatEntryList)
		{
			resultKeywordsStat.put(
					StringUtil.unescapeString(keywordsStatEntry.getKey()), 
					keywordsStatEntry.getValue());
		}
		
		return resultKeywordsStat;
	}
	
	/**

		Generate the content of Simplified Data Blocks section.
		
		The Simplified Data Blocks section only contains the object ID of data blocks,
		the package name and the keywords in data blocks.

	 */
	Set<String> getSimplfiedDataBlocks()
	{
		//
		// The values will be sorted by TreeSet automatically
		Set<String> dataBlockStat = new TreeSet<String>();
		
		//
		// Scan all raw statements in data blocks with keywords
		for (DataBlockRawStat rawStat : dataBlockWithKeywordsRawStat)
		{
			// We only  interested in the key argument of
			// key-value pair operation
			String constKey = extractValidKeyArgOfStat(rawStat.statement);
			if (constKey == null)
			{
				continue;
			}
			
			// Unescape the key
			constKey = StringUtil.unescapeString(constKey);
			
			String stat = rawStat.dataBlockId + ',' + constKey;
			dataBlockStat.add(stat);
		}
		
		return dataBlockStat;
	}
}

/**

	Data class used for recording info on Jimple statement hit.
	In order to further investigate the root caller of each Jimple statement.

 */
class JimpleHit
{
	Unit jimple;
	
	int keywordUnitNum;
	String keyword;
}

/**

	Data class for recording statements in data block for further processing

 */
class DataBlockRawStat
{
	Unit statement;
	String dataBlockId;
}