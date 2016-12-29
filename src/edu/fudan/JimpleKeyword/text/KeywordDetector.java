package edu.fudan.JimpleKeyword.text;

import java.util.ArrayList;
import java.util.List;

import edu.fudan.JimpleKeyword.io.KeywordList;
import edu.fudan.JimpleKeyword.util.StringUtil;

/**

	This class contains methods and configurations
	for detecting keywords in a given Jimple statement.

 */
public class KeywordDetector 
{
	//
	// Data list for Jimple statement inspection
	private KeywordList keywordList;
	
	//
	// Some utilities for keyword inspection
	private WordSplitter wordSplitter;
	private PorterStemmer porterStemmer;
	
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
	
	/**
	 
	 	Main Interface Method
	 
		Figure out if given Jimple statement contains keyword we interested
		Currently, we only focused on keyword in String constant.
		
		The keyword returned is stemmed keyword.
	
	 */
	public String figureOutKeywordInJimple(String jimpleInString)
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

		Initializer of this class

	 */
	public KeywordDetector(KeywordList keywordList)
	{
		//
		// Initialize data list for Jimple inspection
		this.keywordList = keywordList;
		
		//
		// Initialize utilities
		wordSplitter = new WordSplitter(keywordList.getDictForWordSplit());
		porterStemmer = new PorterStemmer();
	}
}
