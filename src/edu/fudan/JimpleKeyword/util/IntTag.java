package edu.fudan.JimpleKeyword.util;

import soot.tagkit.Tag;

/**

	Customized IntTag for Jimple statements ID tagging

*/
public class IntTag implements Tag
{
	private String name;
	private int value;
	
	public IntTag(String name, int value)
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
	
	public int getInt()
	{
		return value;
	}
	
	String getIntInString()
	{
		return Integer.toString(value);
	}
}
