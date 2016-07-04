package edu.fudan.JimpleKeyword;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
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
	// We use List since Jimple statements doesn't seem to duplicate
	private List<JimpleHit> jimpleHit;
	
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
	
	private String joinString(List<String> strList, char seperator)
	{
		StringBuilder outputBuilder = new StringBuilder();
		
		for (String str : strList)
		{
			outputBuilder.append(str);
			outputBuilder.append(seperator);
		}
		
		return outputBuilder.toString();
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
		String canonicalizedStrConst = joinString(wordsInStrConst, ' ');
		
		//
		// Convert canonicalized str const to lower case to ignore case
		canonicalizedStrConst = canonicalizedStrConst.toLowerCase();
		
		return canonicalizedStrConst;
	}
	
	private String figureOutKeywordInStrConst(String stringConst)
	{
		//
		// Canonicalize string const with word splitting, stemming, etc.
		String canonicalizedStrConst = canonicalizeStringConst(stringConst);
		
		// Check if current string const contains keyword
		String keywordInStringConst = keywordList.figureOutKeyword(canonicalizedStrConst);
		if (keywordInStringConst != null)
		{
			//
			// Check that the keyword is an complete word
			// instead of char sequence appear in the middle of
			// another word
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
		InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
		// Check if the method invoked has 2 arguments
		if (invokeExpr.getArgCount() == 2)
		{
			// Check if the type of first argument is String
			String typeOfFirstArg = invokeExpr.getArg(0).getType().toString();
			if (typeOfFirstArg.equals("java.lang.String"))
			{
				return JimpleInitialJudgeStatus.JIMPLE_DEFINITE_HIT;
			}
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
		
		//
		// Check if current statement uses HashMap class
		if (Config.recordJimpleUsingHashMap)
		{
			if (curUnitInString.contains("java.util.HashMap")
				&& (curUnitInString.contains("put(") || curUnitInString.contains("get(")))
			{
				jimpleUsingHashMap.add(curUnitInString);
			}
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
		
		// Record current Jimple statement
		IntTag unitNumTag = (IntTag)(curUnit.getTag("unitNum"));
		jimpleWithKeywords.add(unitNumTag.getIntInString() + ',' + keywordInUnit + ',' + curUnitInString);
		
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
		keywordsInPackage.add(curClass.getPackageName() + ',' + keywordInUnit);
		
	}

	/**
		 
		Scanning the Jimple statements which are though reachable by FlowDroid,
		and record the information we care.
	
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
		
		//
		// Scan Jimple statements
		// and record the information we interested in
		scanJimple();
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
}

/**

	Data class used for recording info on Jimple statement hit.

 */
class JimpleHit
{
	Unit jimple;
	
	int keywordUnitNum;
	String keyword;
}