package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;

class RootCallerMethodInspector 
{
	//
	// Data fields for saving raw data block info
	private List<DataBlockRawStat> rawStat;
	
	//
	// Data fields for saving refined data block info
	private List<DataBlockInfo> dataBlocksInfo;
	
	private List<DataBlockInfo> refineDataBlockInfo()
	{
		//
		// Initialize the variables for recording 
		// refined data block info
		Map<String, DataBlockInfo> refinedInfo = new HashMap<String, DataBlockInfo>();
		
		//
		// Refine raw data block stat info
		for (DataBlockRawStat curRawStat : rawStat)
		{
			if (refinedInfo.containsKey(curRawStat.dataBlockId))
			{
				//
				// Insert keywords to existing refined info
				DataBlockInfo curRefinedInfo = refinedInfo.get(curRawStat.dataBlockId);
				curRefinedInfo.keywords.add(curRawStat.keyword);
			}
			else
			{
				//
				// Create a new refined info instance.
				DataBlockInfo curRefinedInfo = new DataBlockInfo();
				curRefinedInfo.dataBlockId = curRawStat.dataBlockId;
				curRefinedInfo.fromMethod = Main.cfgOfApk.getMethodOf(curRawStat.statement);
				curRefinedInfo.keywords = new HashSet<String>();
				curRefinedInfo.keywords.add(curRawStat.keyword);
				
				//
				// Record the new refined info instance
				refinedInfo.put(curRawStat.dataBlockId, curRefinedInfo);
			}
		}
		
		//
		// Convert refined data blocks info to list
		List<DataBlockInfo> refinedInfoInList = new ArrayList<DataBlockInfo>();
		refinedInfoInList.addAll(refinedInfo.values());
		
		return refinedInfoInList;
	}
	
	RootCallerMethodInspector(List<DataBlockRawStat> rawStat)
	{
		//
		// Initialize data fields
		this.rawStat = rawStat;
		
		//
		// Refine the data blocks info
		this.dataBlocksInfo = refineDataBlockInfo();
		
		//
		// Find out the root caller methods of data blocks
	}
	
	/**
	 
		This method returns formatted root caller methods info.
	
	 */
	String getRootCallerMethodInfo()
	{
		return "";
	}
}

/**

	Data class for saving info on data block

 */
class DataBlockInfo
{
	String dataBlockId;
	Set<String> keywords;
	SootMethod fromMethod;
}