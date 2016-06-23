package edu.fudan.JimpleKeyword;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeStmt;

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
					String stringConst = jimpleInString.substring(stringConstBegin, stringConstEnd);
					
					stringConsts.add(stringConst);
				}
			}
			//
			// Backslash indicates escape character
			else if (jimpleInString.charAt(i) == '\\')
			{
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
			String stemmedWord = porterStemmer.stripAffixes(wordsInStrConst.get(i));
			wordsInStrConst.set(i, stemmedWord);
		}
		
		// Output the process string constant
		String processedStrConst = joinString(wordsInStrConst, ' ');
		
		return processedStrConst;
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
			//
			// Canonicalize string const with word splitting, stemming, etc.
			String canonicalizedStrConst = canonicalizeStringConst(stringConst);
			
			// Check if current string const contains keyword
			String keywordInStringConst = keywordList.figureOutKeyword(canonicalizedStrConst);
			if (keywordInStringConst != null)
			{
				// Current Jimple statement contains keyword
				return keywordInStringConst;
			}			
		}
		
		// None of the string consts in current Jimple statement
		// contains keyword
		return null;
	}
	
	/**
	 
		Inspect given Jimple statement
		and record relating information if
		the statement is the one we interested in

	 */
	private void inspectJimpleStatement(Unit curUnit, SootClass curClass)
	{		
		//
		// Currently we only interested in Jimple statement
		// which invokes certain API
		if (!(curUnit instanceof InvokeStmt))
		{
			// Skip non-invoke Jimple statement
			return;
		}
		
		String curUnitInString = curUnit.toString();
		
		//
		// Check if current Jimple statement invokes
		// API we interested in
		if (Config.interestedApiOnly)
		{
			if (!interestedApiList.containInterestedApi(curUnitInString))
			{
				// Skip Jimple statement that doesn't contain interested API
				return;
			}
		}
		
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
		if (keywordInUnit != null)
		{
			// Record current Jimple statement
			jimpleWithKeywords.add(curUnitInString);
			
			// Record current keyword
			keywordsHit.add(keywordInUnit);
			
			//
			// Record the keywords and its corresponding package
			// Here we record package name as key
			// since a keyword may appears in multiple packages
			keywordsInPackage.add(curClass.getPackageName() + ',' + keywordInUnit);
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
				
				// Construct active body for some method
				if (!m.hasActiveBody() && m.isConcrete())
				{
					m.retrieveActiveBody();
				}
					
				// Skip method without active body
				if (!m.hasActiveBody())
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
			if (interestedApiListFile.exists())
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
	
	KeywordInspector(KeywordList keywordList)
	{
		
		//
		// Initialize data list for Jimple inspection
		this.keywordList = keywordList;
		interestedApiList = initInterestedApiList();

		//
		// Initialize utilities
		wordSplitter = new WordSplitter(keywordList.getDictForWordSplit());
		porterStemmer = new PorterStemmer();
		
		//
		// Initialize output information variables
		keywordsInPackage = new HashSet<String>();
		jimpleWithKeywords = new ArrayList<String>();
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
