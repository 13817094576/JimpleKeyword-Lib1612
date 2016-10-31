package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
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

/**

	This class is used for recording key string const
	associated with a container variable instance.

 */
class KeyTaintTag implements Tag
{
	// The tag name of KeyTaintTag for tainting Soot ValueBox
	static final String TAGNAME_KEYTAINT = "keyTaint";
	
	private String name;
	private List<String> keyConsts = new ArrayList<String>();
	
	KeyTaintTag(String name)
	{
		this.name = name;
	}

	public void addKeyConst(String keyConst)
	{
		keyConsts.add(keyConst);
	}
	
	@Override
	public String getName() 
	{
		return name;
	}

	@Override
	public byte[] getValue() throws AttributeValueException 
	{
		return this.toString().getBytes();
	}
	
	public String getKeyConstsInStr()
	{
		return this.toString();
	}
	
	/**
	
		Format the key consts list and output it.
	
	 */
	@Override
	public String toString()
	{
		StringBuilder keyConstsInStrBuilder = new StringBuilder();
		for (String keyConst : keyConsts)
		{
			keyConstsInStrBuilder.append(keyConst);
			keyConstsInStrBuilder.append(',');
		}
		
		return keyConstsInStrBuilder.toString();
	}
	
	public static KeyTaintTag merge(KeyTaintTag[] tags)
	{
		//
		// Handle empty corner case
		if (tags == null || tags.length == 0)
		{
			return null;
		}
		
		// Create a new KeyTaintTag instance
		KeyTaintTag mergedTags = new KeyTaintTag(KeyTaintTag.TAGNAME_KEYTAINT);
		
		//
		// Add all key tags to new instance
		for (KeyTaintTag curTag : tags)
		{
			if (curTag == null)
			{
				continue;
			}
			
			mergedTags.keyConsts.addAll(curTag.keyConsts);
		}
		
		//
		// Check if merged tag list is still empty
		if (mergedTags.keyConsts.isEmpty())
		{
			// If merged tag list is still empty,
			// return null
			return null;
		}
		
		return mergedTags;
	}
	
	public void union(KeyTaintTag tag)
	{
		//
		// Add the key consts of another instance 
		// to current instance
		keyConsts.addAll(tag.keyConsts);
	}
}

/**

	This class is used for recording tainted container vars
	and the taint source statement.

 */
class KeyTaintedVar
{
	Unit taintSrcStmt;
	ValueBox varBox;
}