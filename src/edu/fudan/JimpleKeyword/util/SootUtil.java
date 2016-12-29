package edu.fudan.JimpleKeyword.util;

import java.util.ArrayList;
import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;

public class SootUtil 
{
	/**
	 
		Try to retrieve the body of method first,
		then return whether the method has active body
		after attempt.

	 */
	public static boolean ensureMethodActiveBody(SootMethod m)
	{
		//
		// Check assumptions
		assert m != null;
		
		//
		// First attempt to retrieve active body of method
		if (!m.hasActiveBody() && m.isConcrete())
		{
			m.retrieveActiveBody();
		}
		
		//	
		// Then return whether the method has active body
		return m.hasActiveBody();
	}
	
	/**
	 
		Get several leading parts of a package/class full name.
	
	 */
	public static String getLeadingPartsOfName(String name, int partsCount)
	{
		int dotPos = -1;
		for (int i=0; i<partsCount; i++)
		{
			//
			// Find position of next dot
			dotPos = name.indexOf('.', dotPos + 1);
			
			//
			// Check if given name has less parts than we want
			if (dotPos < 0)
			{
				//
				// If given name has less parts than we want
				// return the whole name
				return name;
			}
		}
		
		//
		// The prevDotPos index is set to
		// the dot after the parts we want
		String leadingParts = name.substring(0, dotPos);
		return leadingParts;
	}
}