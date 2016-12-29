package edu.fudan.JimpleKeyword.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**

	WordCounter class count the frequency a word appeared

*/
public class WordCounter
{
	//
	// Initialize output statistic variables
	Map<String, Integer> keywordsStat = new HashMap<String, Integer>();
	
	/**
	 
		Increment the counter of a given word
	
	 */
	public void Count(String keyword)
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
	public Map<String, Integer> getListInDesc()
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
