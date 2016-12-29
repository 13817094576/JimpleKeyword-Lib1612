package edu.fudan.JimpleKeyword.io;

import java.util.ArrayList;
import java.util.List;

import edu.fudan.JimpleKeyword.Config;
import edu.fudan.JimpleKeyword.util.FileUtil;

/**

	This class is used for parsing the content of libraries API list file,
	load it's content and store its content to class.
	We can query the list using the methods provided by the class.

 */
public class LibrariesList 
{
	private ArrayList<String> librariesList = new ArrayList<String>();
	
	/**
	 
		Canonicalize text line read from libraries API list file.
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
	 
	 	Load the content of libraries API list file
	 	and store the content to the class
	 
	 */
	public LibrariesList()
	{
		//
		// Check if interested API only switch is turned on
		if (!Config.apiInLibrariesOnly)
		{
			throw new RuntimeException("Config.apiInLibrariesOnly switch isn't turned on");
		}
		
		//
		// Read content of libraries list file to array list in class
		List<String> listLines = FileUtil.readAllLinesFromFile(Config.CONFIG_FILE_LIBRARIES_LIST);
		
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
			
			// Record library package name
			librariesList.add(listLine);
		}
	}

	/**
	 
		Check if a given fragment of text 
		contains package name of certain library

	 */
	public boolean containLibPackageName(String text)
	{
		return figureOutLibPackageName(text) != null;
	}
	
	/**
	 
		Specify which library package name is in given text.
		If given text contains no library package name, null is returned.

	 */
	String figureOutLibPackageName(String text)
	{
		for (String libPackageName : librariesList)
		{
			//
			// Here we use contain instead of equal
			if (text.contains(libPackageName))
			{
				return libPackageName;
			}
		}
		
		// Given text doesn't contain any package name of library
		return null;
	}
}
