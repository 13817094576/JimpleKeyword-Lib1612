package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**

	StringUtil class contains methods for miscellaneous string processing

 */
class StringUtil 
{
	/**
	 
		Combine string items in a list with given seperator char

	 */
	static String joinString(List<String> strList, char seperator)
	{
		StringBuilder outputBuilder = new StringBuilder();
		
		int indexOfLastItem = strList.size() - 1;
		for (int i=0; i<strList.size(); i++)
		{
			outputBuilder.append(strList.get(i));
			
			//
			// Append seperators for items excluding the last one
			if (i < indexOfLastItem)
			{
				outputBuilder.append(seperator);
			}
		}
		
		return outputBuilder.toString();
	}
	
	/**
	 
		Unescape string with C-style escape sequences

	 */
	static String unescapeString(String rawString)
	{
		// Initialize output variables
		StringBuilder unescapedStrBuilder = new StringBuilder(rawString.length());
		
		//
		// Scan the raw string
		for (int i=0; i<rawString.length(); i++)
		{
			char c = rawString.charAt(i);
			if (c == '\"')
			{
				// Skip double quote
			}
			else if (c == '\\')
			{
				//
				// Do escape
				
				if (i+1 < rawString.length())
				{
					// Check escape char
					i++;
					char escapeChar = rawString.charAt(i);
					
					if (escapeChar == '\"')
					{
						unescapedStrBuilder.append('\"');
					}
					else if (escapeChar == '\\')
					{
						unescapedStrBuilder.append('\\');
					}
					else
					{
						// Ignore other escape char currently
					}
				}
				else
				{
					// It's illegal to has a escape mark '\' solely.
				}
				
			}
			else
			{
				// Copy other chars directly
				unescapedStrBuilder.append(c);
			}
		}
		
		// Return final unescaped string
		return unescapedStrBuilder.toString();
	}
}

/**

	WordCounter class count the frequency a word appeared

 */
class WordCounter
{
	//
	// Initialize output statistic variables
	Map<String, Integer> keywordsStat = new HashMap<String, Integer>();
	
	/**
	 
		Increment the counter of a given word

	 */
	void Count(String keyword)
	{
		//
		// Increment the counter for keywords
		if (keywordsStat.containsKey(keyword))
		{
			Integer keywordCounter = keywordsStat.get(keyword);
			keywordCounter++;
			keywordsStat.put(keyword, keywordCounter);
		}
		else
		{
			keywordsStat.put(keyword, 1);
		}
	}
	
	/**
	 
		Return the word counter list in descending order

	 */
	Map<String, Integer> getListInDesc()
	{
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
}