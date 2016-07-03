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