package edu.fudan.JimpleKeyword;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.SootClass;
import soot.SootMethod;

/**

	This class contains code for emitting
	info on sensitive data structure
	in order to track these structures in 
	TaintDroid.
	
	NOTES:
	Currently we tag sensitive data from 
	data blocks with keywords.
	This brings in less sensitive data than
	Jimple statements with keywords.

 */
class TDroidLink 
{
	//
	// Raw stat info on data blocks in app
	private List<DataBlockRawStat> rawStat;
	
	//
	// Output buffer for recording sensitive info
	// so that we can track them in TaintDroid
	private Set<String> sensitiveDataInfo = new HashSet<String>();
	
	private void genSensitiveDataInfo()
	{
		for (DataBlockRawStat curRawStat : rawStat)
		{
			//
			// Skip statements without a keyword
			if (curRawStat.keyword == null)
			{
				continue;
			}
			
			//
			// Get the method of current statement 
			SootMethod curMethod = Main.cfgOfApk.getMethodOf(curRawStat.statement);
			SootClass curClass = curMethod.getDeclaringClass();
			
			//
			// TODO: The INFO_TYPE field should be expanded in the future
			
			//
			// Generate sensitive data info
			// The format of sensitive data info is:
			// INFO_TYPE, KEYWORD, METHOD_NAME
			String curSensitiveDataInfo = String.format("1,%s,%s.%s", 
					curRawStat.keyword, curClass.getName(), curMethod.getName());
			
			//
			// Record current sensitive data info
			sensitiveDataInfo.add(curSensitiveDataInfo);
		}
	}
	
	TDroidLink(List<DataBlockRawStat> rawStat)
	{
		this.rawStat = rawStat;
		
		//
		// Generate info on sensitive data structures
		// so that we can track them in TaintDroid
		genSensitiveDataInfo();
	}
	
	Set<String> getSensitiveDataInfo()
	{	
		return sensitiveDataInfo;
	}
}
