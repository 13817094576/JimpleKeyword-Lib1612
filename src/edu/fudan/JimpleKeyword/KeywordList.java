package edu.fudan.JimpleKeyword;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.android.IMethodSpec;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

/**

	This class is used for parsing the content of a keyword list file,
	load it's content and store its content to class.
	We can query the list using the methods provided by the class.

 */
public class KeywordList 
{
	// Keyword list for Jimple keyword matching
	private ArrayList<String> keywordList = new ArrayList<String>();
	// Word list for sentence words splitting
	private HashSet<String> wordsForSplitting = new HashSet<String>();
	
	/**
	 
		Canonicalize text line read from keyword list file.
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
	 
		Read all text lines from a file in raw format

	 */
	private List<String> readAllLinesFromFile(String fileName)
	{
		List<String> lines = new ArrayList<String>();
		
		try 
		{
			FileInputStream fileInputStream = new FileInputStream(fileName);
			DataInputStream dataInputStream = new DataInputStream(fileInputStream);
			
			while (true)
			{
				String line = dataInputStream.readLine();
				if (line == null)
				{
					break;
				}
				
				// Record current line
				lines.add(line);
			}
		} 
		catch (Exception e) 
		{
			throw new RuntimeException("Unexpected IO error on "+ fileName, e);
		}		
		
		return lines;
	}
	
	/**
	 
	 	Load the content of a keyword list file
	 	and store the content to the class
	 
	 */
	KeywordList(String fileName)
	{
		//
		// Read content of keyword list file to array list in class
		List<String> listLines = readAllLinesFromFile(fileName);
		
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
			
			// Convert keyword to lower case in order to ignore case
			listLine = listLine.toLowerCase();
			
			// Use keyword directly for Jimple keyword matching
			keywordList.add(listLine);
			
			// Use individual words in phrases for word splitting
			String[] wordsInCurLine = listLine.split(" ");
			for (String wordInCurLine : wordsInCurLine)
			{
				wordsForSplitting.add(wordInCurLine);
			}
		}
	}

	/**
	 
		Check if a given fragment of text has
		keywords in list

	 */
	boolean hasKeyword(String text)
	{
		return figureOutKeyword(text) != null;
	}
	
	/**
	 
		Specify which keyword is in given text.
		If given text contains no keyword, null is returned.

	 */
	String figureOutKeyword(String text)
	{
		// Convert text to lower case to ignore case
		// Keywords in keywordList has already converted to lower case
		text = text.toLowerCase();
		
		for (String knownKeyword : keywordList)
		{
			//
			// Here we use contain instead of equal
			if (text.contains(knownKeyword))
			{
				return knownKeyword;
			}
		}
		
		// Given text doesn't contain any known keyword
		return null;
	}
	
	Set<String> getDictForWordSplit()
	{
		return wordsForSplitting;
	}
}
