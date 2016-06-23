package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.List;

/**

	This class is used for parsing the content of interested API list file,
	load it's content and store its content to class.
	We can query the list using the methods provided by the class.

 */
public class InterestedApiList 
{
	private ArrayList<String> interestedApiList = new ArrayList<String>();
	
	/**
	 
		Canonicalize text line read from interested API list file.
		If we should skip current line, null is returned.

	 */
	private String canonicalizeListLine(String listLine)
	{
		// Canonicalize the line
		listLine = listLine.trim();
		
		// Is empty line?
		if (listLine.isEmpty())
		{
			// Skip empty line
			return null;
		}
		// Is comment line?
		// Comment lines begin with '#'
		if (listLine.charAt(0) == '#')
		{
			// Skip comment line
			return null;
		}
		
		// Return processed list line
		return listLine;
	}
	
	/**
	 
	 	Load the content of interested API list file
	 	and store the content to the class
	 
	 */
	InterestedApiList()
	{
		//
		// Check if interested API only switch is turned on
		if (!Config.interestedApiOnly)
		{
			throw new RuntimeException("Config.interestedApiOnly switch isn't turned on");
		}
		
		//
		// Read content of keyword list file to array list in class
		List<String> listLines = FileUtil.readAllLinesFromFile(Config.CONFIG_FILE_INTERESTED_API);
		
		//
		// Use the content of file to initialize variables
		for (String listLine : listLines)
		{
			// Canonicalize list line
			// and skip lines we should ignore
			listLine = canonicalizeListLine(listLine);
			if (listLine == null)
			{
				continue;
			}
			
			// Record interested API
			interestedApiList.add(listLine);
		}
	}

	/**
	 
		Check if a given fragment of text 
		contains interested API

	 */
	boolean containInterestedApi(String text)
	{
		return figureOutInterestedApi(text) != null;
	}
	
	/**
	 
		Specify which interested API is in given text.
		If given text contains no interested API, null is returned.

	 */
	String figureOutInterestedApi(String text)
	{
		for (String interestedApi : interestedApiList)
		{
			//
			// Here we use contain instead of equal
			if (text.contains(interestedApi))
			{
				return interestedApi;
			}
		}
		
		// Given text doesn't contain any interested API
		return null;
	}
}
