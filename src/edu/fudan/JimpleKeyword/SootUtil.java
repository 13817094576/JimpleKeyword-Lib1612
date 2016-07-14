package edu.fudan.JimpleKeyword;

import soot.SootMethod;
import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;

class SootUtil 
{
	/**
	 
		Try to retrieve the body of method first,
		then return whether the method has active body
		after attempt.

	 */
	static boolean ensureMethodActiveBody(SootMethod m)
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
	static String getLeadingPartsOfName(String name, int partsCount)
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

/**

	Customized IntTag for Jimple statements ID tagging

 */
class IntTag implements Tag
{
	private String name;
	private int value;
	
	IntTag(String name, int value)
	{
		this.name = name;
		this.value = value;
	}

	@Override
	public String getName() 
	{
		return name;
	}

	@Override
	public byte[] getValue()
	{
		return Integer.toString(value).getBytes();
	}
	
	int getInt()
	{
		return value;
	}
	
	String getIntInString()
	{
		return Integer.toString(value);
	}
}