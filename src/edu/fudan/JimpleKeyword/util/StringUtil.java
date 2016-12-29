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

	StringUtil class contains methods for miscellaneous string processing

 */
public class StringUtil 
{
	/**
	 
		Combine string items in a list with given seperator char

	 */
	public static String joinString(List<String> strList, char seperator)
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
	public static String unescapeString(String rawString)
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