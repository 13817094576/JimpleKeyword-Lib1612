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

import edu.fudan.JimpleKeyword.io.KeywordList;
import edu.fudan.JimpleKeyword.util.IntTag;
import edu.fudan.JimpleKeyword.util.SootUtil;
import edu.fudan.JimpleKeyword.util.StringUtil;
import edu.fudan.JimpleKeyword.util.WordCounter;
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
import soot.ValueBox;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.tagkit.Tag;
import soot.util.queue.QueueReader;

/**

	This class contains code for inspecting keywords in Jimple statement.
	
	If the Jimple statement is the one we interested in,
	related information will be recorded for later use.

 */

class KeywordInspector 
{	
	//
	// Utilities for Jimple statement inspection
	private KeywordDetector keywordDetector;
	private JimpleSelector jimpleSelector;
	
	//
	// Output statistic information
	//
	// The keywords in output log are stemmed keywords
	// instead of raw keywords appearing in Jimple statement
	
	// We use List since Jimple statements doesn't seem to duplicate
	// This field records Jimple statements with keywords for direct output
	private List<String> jimpleWithKeywords;
	// We use Set to avoid duplicated keywords
	private Set<String> keywordsHit;
	// We use Set to avoid duplicated <package, keyword> pair
	private Set<String> keywordsInPackage;
	// Jimple doesn't seem to duplicate
	private List<String> jimpleUsingHashMap;
	// Record raw Jimple statements with keywords for inspecting root classes
	private List<JimpleHit> jimpleHit;
	
	//
	// The following fields record the the raw data block statement
	
	// Data block statements for output
	private List<String> dataBlockStatement;
	// Data block raw statements for further processing
	private List<DataBlockRawStat> dataBlockRawStat;
	
	//
	// The following fields record the statements in data blocks with keywords
	
	// The raw statements in data blocks which contain keywords
	private List<DataBlockRawStat> dataBlockWithKeywordsRawStat;
	// Data block with keywords may hit multiple times.
	// There may be multiple statements in a data block containing keywords
	// This variable records the IDs of data blocks with keywords.
	// IDs are in String format instead of int
	private Set<String> dataBlockWithKeywordsIds;
	
	//
	// For library package name list,
	// we only care about the first 3 parts of package name
	private Set<String> libraryPackageName;
	
	//
	// The following fields record the <full-package-name, keyword> pair for output
	// If the first 2 parts of package name is the same as that of APK,
	// we treat it as app package.
	private Set<String> keywordsInAppPackage;
	private Set<String> keywordsInLibPackage;
	
	//
	// This list is used for recording key tainted vars
	// and taint source statements.
	// So that we can find out the starting point of data-flow analysis.
	private List<KeyTaintedVar> keyTaintedVars = new ArrayList<KeyTaintedVar>();
	
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
	static Pattern intPattern = Pattern.compile("[1-9][0-9]*");
	
	/**

		Given statement contains key-value pair operation.
		
		Record the given statement in corresponding data block.
		and return the IDs of data block objects.

	 */
	private List<String> recordStatementInDataBlock(Unit curUnit, 
			String curUnitInString, 
			String curClassName, 
			int unitNum,
			String keywordInUnit)
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
			rawStat.keyword = keywordInUnit;
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
	 
		Taint the key-value pair container (i.e. HashMap instance)
		with key string const.

	 */
	private void tryTaintHashMap(Unit curUnit)
	{
		//
		// Here we assume curUnit is HashMap.get or HashMap.put
		
		// Ensure curUnit is an invoke expression
		if (!(curUnit instanceof InvokeStmt))
		{
			return;
		}
		
		//
		// Get key of current key-value pair
		String key = extractValidKeyArgOfStat(curUnit);
		if (key == null)
		{
			return;
		}
		
		//
		// Get the HashMap instance ValueBox
		InvokeStmt invokeStmt = (InvokeStmt)curUnit;
		InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
		if (!(invokeExpr instanceof InstanceInvokeExpr))
		{
			return;
		}
		InstanceInvokeExpr instanceInvoke = (InstanceInvokeExpr)invokeExpr;
		ValueBox thisBox = instanceInvoke.getBaseBox();
		
		//
		// Taint this value box
		//
		// Currently, although this pointers may point to the same container object,
		// this value box in each statement is a different instance.
		// So a new tag will be associated to each this pointer in each statement.
		Tag thisKeyTag = thisBox.getTag(KeyTaintTag.TAGNAME_KEYTAINT);
		if (thisKeyTag == null)
		{
			// Create a new key taint tag
			KeyTaintTag keyTag = new KeyTaintTag(KeyTaintTag.TAGNAME_KEYTAINT);
			keyTag.addKeyConst(key);
			
			// Associate the new tag to this container variable
			thisBox.addTag(keyTag);
		}
		else
		{
			// Add new key const to KeyTaintTag
			KeyTaintTag keyTag = (KeyTaintTag)thisKeyTag;
			keyTag.addKeyConst(key);
		}
		
		//
		// Record the taint source statement in order to
		// determine the starting point of data-flow analysis
		
		// Create a new KeyTaintedVar instance
		KeyTaintedVar keyTaintedVar = new KeyTaintedVar();
		keyTaintedVar.taintSrcStmt = curUnit;
		keyTaintedVar.varBox = thisBox;
		
		// Add the new KeyTaintedVar instance to list
		keyTaintedVars.add(keyTaintedVar);
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
		JimpleInitialJudgeStatus initialJudgeStatus = jimpleSelector.judgeJimpleInitially(curUnit);
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
			//
			// Record HashMap related statement if needed
			inspectHashMapStatement(curUnit, curUnitInString);
			
			//
			// Taint HashMap instance for data-flow analysis
			tryTaintHashMap(curUnit);
		}
		
		//
		// Find out if current Jimple statement contains a keyword
		String keywordInUnit = keywordDetector.figureOutKeywordInJimple(curUnitInString);
		
		//
		// Record key-value invocation in on the same data block instance
		List<String> dataBlockObjIdList = null;
		if (jimpleSelector.isInvokeStmtContainKeyValue(curUnit))
		{
			dataBlockObjIdList = recordStatementInDataBlock(curUnit, curUnitInString, curClass.getName(), unitNumTag.getInt(), keywordInUnit);
		}
		
		// Check if current statement contains any known keyword
		if (keywordInUnit == null)
		{
			// Skip Jimple statement without keyword
			return;
		}
		
		//
		// Supplement detailed inspection
		if (initialJudgeStatus == JimpleInitialJudgeStatus.JIMPLE_NEED_DETAIL_INSPECTION)
		{
			if (!jimpleSelector.judgeJimpleInDetail(curUnitInString))
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
		
		//
		// Record raw Jimple statements with keywords
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
	
	 */
	private void scanJimpleReachableOnly()
	{
		//
		// Check assumptions
		assert keywordDetector != null;		
		
		//
		// Traverse the reachable method in APK
		int unitNum = 0;					// Unique ID for each Jimple statement
		
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
			// Skip system packages
			String curPackageName = m.getDeclaringClass().getPackageName();
			if (isSystemPackage(curPackageName))
			{
				continue;
			}
			
			//
			// Record package name for statistics on package in app
			inspectPackageName(curPackageName);
			
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
		
		This method is the main entry of Jimple 
		scrutinizing procedure.

	 */
	private void scanJimple()
	{
		//
		// Check assumptions
		assert keywordDetector != null;		
		
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
			
			// Clone the list of methods in order to
			// avoid ConcurrentModificationException
			List<SootMethod> methods = new ArrayList<SootMethod>(curClass.getMethods());
			
			for (SootMethod m : methods)
			{					
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
	
		Extract out the data blocks with keywords from dataBlockRawStat
		according to the data block object ID list dataBlockWithKeywordsIds

	 */
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
		// Initialize utilities		
		keywordDetector = new KeywordDetector(keywordList);
		jimpleSelector = new JimpleSelector();
		
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
		if (Config.reachableMethodsOnly)
		{
			scanJimpleReachableOnly();
		}
		else
		{
			scanJimple();
		}
		
		//
		// Pick out raw statements in data blocks which have keywords
		// for further processing
		dataBlockWithKeywordsRawStat = pickOutRawStatInDataBlocksWithKeywords();
		
		//
		// Sort the data block statements with object ID
		// so that statements on the same object are placed together
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
		
		//
		// Unescape C-style string
		firstArgInStr = StringUtil.unescapeString(firstArgInStr);
		
		return firstArgInStr;
	}
	
	/**

		Generate the statistics on the keywords used by data blocks.
		
		And output the result list in descending order.
		
		This method only considers data blocks containing expected keywords

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
		WordCounter keywordCounter = new WordCounter();
		
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
			keywordCounter.Count(firstArgInStr);
		}
		
		//
		// Sort the keywords stat in descending order
		return keywordCounter.getListInDesc();
	}
	
	/**

		Generate the content of Simplified Data Blocks section.
		
		The Simplified Data Blocks section only contains the object ID of data blocks,
		the package name and the keywords in data blocks.
		
		The data blocks listed in Simplified Data Blocks section
		must contain expected keywords.

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
			// constKey = StringUtil.unescapeString(constKey);
			
			String stat = rawStat.dataBlockId + ',' + constKey;
			dataBlockStat.add(stat);
		}
		
		return dataBlockStat;
	}
	
	/**
	 
		Return the list of key tainted container variables
		
		So that we can do data-flow analysis on these variables

	 */
	List<KeyTaintedVar> getKeyTaintedVars()
	{
		return keyTaintedVars;
	}
	
	List<DataBlockRawStat> getDataBlockWithKeywordsRawStat()
	{
		return dataBlockWithKeywordsRawStat;
	}
	
	//
	// Utility functions
	
	/**
	
		Check if the given stmt hits the keywords we desired, 
		if hit, return the correspond keyword,
		If not, return null

	 */
	public String isHit(Unit unit)
	{
		return keywordDetector.figureOutKeywordInJimple(unit.toString());
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
	
	// The keyword in current Jimple statement
	String keyword;
}