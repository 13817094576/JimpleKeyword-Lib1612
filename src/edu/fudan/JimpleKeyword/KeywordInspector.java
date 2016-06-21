package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

class KeywordInspector {

	//
	// Some utilities for keyword inspection
	private KeywordList keywordList;
	private WordSplitter wordSplitter;
	private PorterStemmer porterStemmer;
	
	// We use List since Jimple statements doesn't seem to duplicate
	private List<String> jimpleWithKeywords;
	// We use Set to avoid duplicated keywords
	private Set<String> keywordsHit;
	
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
		
		char[] jimpleInCharArray = jimpleInString.toCharArray();
		for (int i=0; i<jimpleInCharArray.length; i++)
		{
			//
			// Double quote indicate either begin or end of a string constant
			if (jimpleInCharArray[i] == '\"')
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
					int stringConstLen = stringConstEnd - stringConstBegin + 1;
					String stringConst = new String(jimpleInCharArray, stringConstBegin, stringConstLen);
					
					stringConsts.add(stringConst);
				}
			}
			//
			// Backslash indicates escape character
			else if (jimpleInCharArray[i] == '\\')
			{
				// An \" escape sequence is encountered
				if (jimpleInCharArray[i+1] == '\"')
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
	 
		Scanning the classes with FlowDroid
		and find out the information we care.
		It finds out the Jimple statements with keywords
		and keywords hit.

	 */
	private void processJimple()
	{
		//
		// Check assumptions
		assert keywordList != null;
		
		// Initialize variables
		jimpleWithKeywords = new ArrayList<String>();
		keywordsHit = new HashSet<String>();
		
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
					String curUnitInString = curUnit.toString();
					
					// Check if current statement contains any known keyword
					String keywordInUnit = figureOutKeywordInJimple(curUnitInString);
					if (keywordInUnit != null)
					{
						// Record current Jimple statement
						jimpleWithKeywords.add(curUnitInString);
						
						// Record current keyword
						keywordsHit.add(keywordInUnit);
					}
				}
			}
		}
	}
	
	KeywordInspector(KeywordList list)
	{
		//
		// Initialize private variables
		keywordList = list;
		wordSplitter = new WordSplitter(keywordList.getDictForWordSplit());
		porterStemmer = new PorterStemmer();
		
		//
		// Inspect Jimple statements
		processJimple();
	}
	
	List<String> getJimpleWithKeywords()
	{
		return jimpleWithKeywords;
	}
	
	Set<String> getKeywordsHit()
	{
		return keywordsHit;
	}
}
