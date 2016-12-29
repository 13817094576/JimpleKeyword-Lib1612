package edu.fudan.JimpleKeyword.util;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**

	This file contains some utility functions 
	for file operations

 */
public class FileUtil 
{
	/**
	 
		Read all text lines from a file in raw format

	*/
	public static List<String> readAllLinesFromFile(String fileName)
	{
		List<String> lines = new ArrayList<String>();
		
		//
		// Here we leave parameter validation to InputStream class
		
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
}
