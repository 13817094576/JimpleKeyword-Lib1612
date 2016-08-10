package edu.fudan.JimpleKeyword;

class StringUtil 
{
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
