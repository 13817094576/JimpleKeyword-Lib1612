package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.List;

import soot.Unit;

/**

	This class contains code inspect given HashMap relating
	statement and record its info.

 */
class HashMapStatHandler 
{
	//
	// Data recording fields
	
	// Jimple doesn't seem to duplicate
	private List<String> jimpleUsingHashMap;
	
	boolean isStatementUsingHashMap(String unitInString)
	{
		//
		// Here we only use "HashMap" since HashMap is an interface
		// There LinkedHashMap etc.
		if (unitInString.contains("HashMap")
				&& (unitInString.contains("put(") || unitInString.contains("get(")))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**

		Inspect Jimple statements using HashMap class
		and record relating information
	
	 */
	void inspectHashMapStatement(Unit curUnit, String curUnitInString)
	{
		//
		// Skip parameters validation currently.
		
		//
		// Record statement using HashMap if needed
		if (Config.recordJimpleUsingHashMap)
		{
			jimpleUsingHashMap.add(curUnitInString);
		}
	}
	
	//
	// Output interface
	
	List<String> getJimpleUsingHashMap()
	{
		return jimpleUsingHashMap;
	}
	
	HashMapStatHandler()
	{
		//
		// Initialize data recording fields
		jimpleUsingHashMap = new ArrayList<String>();
	}
}
