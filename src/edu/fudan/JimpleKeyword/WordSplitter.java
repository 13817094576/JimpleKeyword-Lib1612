package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**

	This class contains methods used for
	splitting out words in a sentence.

 */
class WordSplitter {
	
	String sentence;
	
	WordSplitter(String s)
	{
		//
		// Initialize shared variables
		sentence = s;

		// Do words splitting
		splitWords();
	}
	
	private List<String> splitWordsByDelimiter(String s)
	{
		StringTokenizer tokenizer = new StringTokenizer(s, " 1234567890-=[]\\`;',./!#$%^&*()_+{}|~:\"<>?\n\r\t");
		List<String> words = new ArrayList<String>();
		while (tokenizer.hasMoreTokens())
		{
			words.add(tokenizer.nextToken());
		}
		
		return words;
	}
	
	private String findOutWordAfterUpperCaseChar(String fragment, int i)
	{
		//
		// Check assumptions
		
		// The validation of fragmentInCharArr, i is implied
		assert Character.isUpperCase(fragment.charAt(i));
		
		if (i+1 < fragment.length())
		{
			//
			// Find out all the character following
			// char i with the same case
			
			if (Character.isUpperCase(fragment.charAt(i+1)))
			{
				//
				// The next char i+1 after the beginning i
				// is still an upper case char
				// Find out all the following upper case char
				
				for (int j=i+1; j<fragment.length(); j++)
				{
					//
					// Loop until an lower case char occurred.
					if (Character.isLowerCase(fragment.charAt(j)))
					{
						//
						// Extract current word
						
						// Here we minus length by one since we want to exclude
						// the last upper case char. 
						// The last upper case char typically is the beginning of
						// next word
						// e.g. In "URLConnection", we want to exclude the last 'C'
						
						String curWord = fragment.substring(i, j - 1);
						return curWord;	
					}
				}
				
				//
				// The end of fragment reached,
				// return the fragment after char i
				return fragment.substring(i);
			}
			else
			{
				//
				// The next char i+1 after the beginning i
				// is an lower case char
				// Find out all the following lower case char
				
				for (int j=i+1; j<fragment.length(); j++)
				{
					//
					// Loop until an upper case char occurred.
					if (Character.isUpperCase(fragment.charAt(j)))
					{
						//
						// Extract current word
						String curWord = fragment.substring(i, j);
						return curWord;
					}
				}
				
				//
				// The end of fragment reached.
				// return the fragment after char i
				return fragment.substring(i);
			}
			
	
		}
		else
		{
			//
			// Current standalone char is a word
			// The end of fragment reached
			return fragment.substring(i);				
		}		
	}
	
	private String findOutWordAfterLowerCaseChar(String fragment, int i)
	{
		//
		// Check assumptions
		assert Character.isLowerCase(fragment.charAt(i));
		
		//
		// Find out all lower case char following
		
		for (int j=i+1; j<fragment.length(); j++)
		{
			//
			// Loop until an upper case char occured
			if (Character.isUpperCase(fragment.charAt(j)))
			{
				// Extract current word
				String curWord = fragment.substring(i, j);
				return curWord;
			}
		}
		
		//
		// The end of fragment reached,
		// return the fragment after i
		return fragment.substring(i);
	}
	
	/**
	 
		Split the text in fragment by looking for case change
		and output result in wordsOut.
		
		So far we only consider case change used by identifiers.
		Changes violates programming convention is ignore and 
		may not be handled correctly.
		
		Here we output result in wordsOut to save memory by 
		creating less objects and invokes less GC.

	 */
	private void splitWordsByCase(String fragment, List<String> wordsOut)
	{
		//
		// Skip empty strings
		if (fragment == null)
		{
			return;
		}
		fragment = fragment.trim();
		
		//
		// CORNER CASE: Empty string is skipped
		for (int i=0; i<fragment.length(); )
		{
			//
			// Check if the start of a word is an upper case char
			if (Character.isUpperCase(fragment.charAt(i)))
			{
				String word = findOutWordAfterUpperCaseChar(fragment, i);
				wordsOut.add(word);
				
				// Inspect next word
				i += word.length();
			}
			else
			{
				String word = findOutWordAfterLowerCaseChar(fragment, i);
				wordsOut.add(word);
				
				// Inspect next word
				i += word.length();
			}
		}
	}
	
	private List<String> splitWordsByCase(List<String> fragments)
	{
		List<String> words = new ArrayList<String>();
		
		for (String fragment : fragments)
		{
			//
			// Split current fragment and add results to words list
			splitWordsByCase(fragment, words);
		}
		
		return words;
	}
	
	private void splitWords()
	{
		//
		// Process sentence through several passes
		List<String> words = splitWordsByDelimiter(sentence);
		words = splitWordsByCase(words);
		
		//
		// DEBUG
		for (String word : words)
		{
			System.out.print(word + ' ');
		}
		
		System.out.println();
	}
}
