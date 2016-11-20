package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

/**

	This class contains code for inspecting
	the root caller methods of data blocks

 */
class RootCallerMethodInspector 
{
	//
	// Data fields for saving raw data block info
	private List<DataBlockRawStat> rawStat;
	
	//
	// Data fields for saving refined data block info
	private List<MethodHitInfo> dataBlocksInfo;
	
	//
	// Output buffer for saving root caller method info
	private StringBuilder rootCallerMethodInfo = new StringBuilder();
	
	private List<MethodHitInfo> refineDataBlockInfo()
	{
		//
		// Initialize the variables for recording 
		// refined data block info
		Map<String, MethodHitInfo> refinedInfo = new HashMap<String, MethodHitInfo>();
		
		//
		// Refine raw data block stat info
		for (DataBlockRawStat curRawStat : rawStat)
		{
			//
			// Skip Jimple Hit without valid keyword
			if (curRawStat.keyword == null)
			{
				continue;
			}
			
			if (refinedInfo.containsKey(curRawStat.dataBlockId))
			{
				//
				// Insert keywords to existing refined info
				MethodHitInfo curRefinedInfo = refinedInfo.get(curRawStat.dataBlockId);
				curRefinedInfo.keywords.add(curRawStat.keyword);
			}
			else
			{
				//
				// Create a new refined info instance.
				MethodHitInfo curRefinedInfo = new MethodHitInfo();
				curRefinedInfo.dataBlockId = curRawStat.dataBlockId;
				curRefinedInfo.methodHit = Main.cfgOfApk.getMethodOf(curRawStat.statement);
				curRefinedInfo.keywords = new HashSet<String>();
				curRefinedInfo.keywords.add(curRawStat.keyword);
				
				//
				// Record the new refined info instance
				refinedInfo.put(curRawStat.dataBlockId, curRefinedInfo);
			}
		}
		
		//
		// Convert refined data blocks info to list
		List<MethodHitInfo> refinedInfoInList = new ArrayList<MethodHitInfo>();
		refinedInfoInList.addAll(refinedInfo.values());
		
		return refinedInfoInList;
	}
	
	/**
	 
		FlowDroid set all components called from dummyMainClass.
		So when a method is called from dummyMainClass, 
		this method is an entrypoint.
		
		This function is used for determining if all callers are
		from dummyMainClass to judge if a method is an entrypoint.
	
	 */
	private boolean areCallersFromDummyMain(Set<Unit> callers)
	{
		//
		// Check assumptions
		assert callers != null;
		
		for (Unit caller : callers)
		{
			//
			// Find out the class the caller statement located in
			SootMethod callerMethod = Main.cfgOfApk.getMethodOf(caller);
			SootClass callerClass = callerMethod.getDeclaringClass();
			
			//
			// Check if the caller class isn't dummyMainClass
			if (!callerClass.getName().contains("dummyMainClass"))
			{
				return false;
			}
		}
		
		//
		// All callers are from dummyMainClass
		return true;
	}
	
	private void inspectRootCallerMethod(MethodHitInfo methodHit, Stack<SootMethod> callChain)
	{
		//
		// Output root caller method info
		
		// Output root caller method summary
		rootCallerMethodInfo.append(methodHit.dataBlockId);
		rootCallerMethodInfo.append(',');
		rootCallerMethodInfo.append(methodHit.keywords.toString());
		rootCallerMethodInfo.append('\n');
		
		//
		// Output root caller method call chain
		rootCallerMethodInfo.append(methodHit.methodHit.getSignature());
		rootCallerMethodInfo.append('\n');
		for (int i=callChain.size()-1; i>=0; i--)
		{
			SootMethod curMethod = callChain.get(i);
			
			rootCallerMethodInfo.append(curMethod.getSignature());
			rootCallerMethodInfo.append('\n');
		}
		
		//
		// Output epilog text
		rootCallerMethodInfo.append('\n');
	}
	
	private void inspectCaller(MethodHitInfo methodHit, Stack<SootMethod> callChain)
	{		
		// Shortcut for current method
		SootMethod m = methodHit.methodHit;
		
		//
		// Find out the callers of current method
		Set<Unit> callers = Main.cfgOfApk.getCallersOf(m);
		if (callers.isEmpty()
			// For FlowDroid, all entrypoint methods are called from dummyMainClass
			|| areCallersFromDummyMain(callers))
		{
			//
			// Current method is a root caller
			// Inspect the root caller method
			inspectRootCallerMethod(methodHit, callChain);
		}
		else
		{
			if (callChain.contains(m))
			{
				// An invocation cycle encountered
				// Don't inspect caller statements any more
				return;
			}
			
			//
			// Record current method on stack
			// for inspecting method in next-level
			callChain.push(m);
			
			for (Unit caller : callers)
			{
				// Build a new instance of MethodHit
				// to record method hit and corresponding keyword
				MethodHitInfo callerHit = new MethodHitInfo();
				callerHit.methodHit = Main.cfgOfApk.getMethodOf(caller);
				// Transit keyword, keywordUnitNum
				callerHit.keywords = methodHit.keywords;
				callerHit.dataBlockId = methodHit.dataBlockId;
				
				inspectCaller(callerHit, callChain);
			}
			
			//
			// Remove current method and
			// return to an upper level
			callChain.pop();
		}
	}
	
	private void inspectRootCaller()
	{
		//
		// This method must be called after
		// the data block info is refined
		assert dataBlocksInfo != null;
		
		//
		// Inspect root caller method of each data block
		for (MethodHitInfo curDataBlockInfo : dataBlocksInfo)
		{
			//
			// Build a new call chain stack for each data block
			Stack<SootMethod> callChain = new Stack<SootMethod>();
			
			//
			// Inspect current data block recursively
			inspectCaller(curDataBlockInfo, callChain);
		}
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
		// and save info to class fields
		inspectRootCaller();
	}
	
	/**
	 
		This method returns formatted root caller methods info.
	
	 */
	String getRootCallerMethodInfo()
	{
		return rootCallerMethodInfo.toString();
	}
}

/**

	Data class for saving info on method hit
	in root caller inspection proess

 */
class MethodHitInfo
{
	String dataBlockId;
	Set<String> keywords;
	SootMethod methodHit;
}