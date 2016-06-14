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
	private ArrayList<String> keywordList = new ArrayList<String>();
	
	/**
	 
	 	Load the content of a keyword list file
	 	and store the content to the class
	 
	 */
	KeywordList(String fileName)
	{
		//
		// Read content of keyword list file to array list in class
		
		try 
		{
			FileInputStream listFileInputStream = new FileInputStream(fileName);
			DataInputStream listDataInputStream = new DataInputStream(listFileInputStream);
			
			while (true)
			{
				String listLine = listDataInputStream.readLine();
				if (listLine == null)
				{
					break;
				}
				
				// Convert keyword to lower case in order to ignore case
				keywordList.add(listLine.toLowerCase());
			}
		} 
		catch (Exception e) 
		{
			throw new RuntimeException("Unexpected IO error on "+ fileName, e);
		}
	}

	/**
	 
		Check if a given fragment of text has
		keywords in list

	 */
	boolean hasKeyword(String text)
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
				return true;
			}
		}
		
		return false;
	}
}
