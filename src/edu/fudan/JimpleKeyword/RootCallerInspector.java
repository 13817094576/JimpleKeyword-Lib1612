package edu.fudan.JimpleKeyword;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;

/**

	This file contains code for finding out the root caller of 
	given Jimple statements.
	
	And record relating info if the root caller satisfies some
	conditions.

 */
class RootCallerInspector 
{
	
	//
	// Cached data structure for speeding up processing
	
	// Activity ID cache for avoiding repeated Activity class scanning
	private Map<String, String> activityClassIdCache = new HashMap<String, String>();
	// Cache for recording method and its corresponding root caller
	// in order to avoid repeat root caller inspection
	private Map<SootMethod, SootClass> methodRootCallerCache = new HashMap<SootMethod, SootClass>();
	
	//
	// Output statistics information
	
	private Set<String> rootCallerClassInfo = new HashSet<String>();

	/**

		Check if the given class is a sub-class of framework class which
		manages content display, such as Activity, Fragment.
		
	*/
	private boolean isChildOfDisplayableClass(SootClass sootClass)
	{
		// Check classes from the lowest class hierarchy
		SootClass curClass = sootClass;
		while (true)
		{
			//
			// Check if the name of the class contains
			// name of class managing content display
			String curClassName = curClass.getName();
			
			if (curClassName.contains("android.app.Activity")
				|| curClassName.contains("android.app.Fragment"))
			{
				return true;
			}
			
			//
			// Otherwise, check the superclass of current class
			if (curClass.hasSuperclass())
			{
				curClass = curClass.getSuperclass();
			}
			else
			{
				// We have arrived at the top of class hierarchy
				// so the given class isn't a child of Activity
				return false;
			}
		}
	}
	
	/**
	 
		Try to get activity from the super classes of a activity class

	 */
	private String getActivityIdFromSuperClass(SootClass activityClass)
	{
		//
		// Check assumptions
		assert activityClass != null;
		
		if (activityClass.hasSuperclass())
		{
			//
			// Check if the super class is a child of displayable class
			SootClass superClass = activityClass.getSuperclass();
			if (!isChildOfDisplayableClass(superClass))
			{
				// Super class isn't a child of displayable class
				// So we're sure no resource ID in super class
				return "UNDETERMINED";
			}
			
			
			String activityClassId = getIdOfActivityClass(superClass);
			if (activityClassId.equals("UNDETERMINED"))
			{
				// No resource ID in super class,
				// so ID can't be determined, keep activityClassId unchanged
				return activityClassId;
			}
			else
			{
				// Found resource ID in super class
				// flag the ID as ID from super class by adding 'S' prefix
				return 'S' + activityClassId;
			}
		}
		else
		{
			//
			// No setContentView method found
			// and no resource ID in base Activity class,
			// Can't determine resource ID
			return "UNDETERMINED";
		}		
	}
	
	/**
	
		This function checks if a method invoked is
		setContentView or inflate.
		
		These method set the content of an activity

	 */
	private boolean isInvokeOfSetLayoutMethod(String methodName, String unitInString)
	{
		// For setContentView method,
		// Here we only match "setContentView" instead of
		// full name which contains package name etc.
		// since setContentView usually appears in virtualinvoke 
		// and it doesn't contains android framework package name
		if (methodName.contains("setContentView"))
		{
			return true;
		}
		
		//
		// Matching LayoutInflater.inflate()
		if (unitInString.contains("inflate") && unitInString.contains("LayoutInflater"))
		{
			return true;
		}
		
		return false;
	}
	
	/**

		Find out the resource ID of a given Activity class
		by matching the setContentView method.
		
		If resource ID is found in a super Activity class,
		the resource ID is marked with 'S' prefix.
		
		If this function can't determine the ID,
		"UNDETERMINED" is returned.

	 */
	private String getIdOfActivityClass(SootClass activityClass)
	{
		//
		// Check assumptions
		assert activityClass != null;
		
		//
		// Lookup the Activity ID cache first
		String className = activityClass.getName();
		if (activityClassIdCache.containsKey(className))
		{
			return activityClassIdCache.get(className);
		}
		
		//
		// Scan the Jimple code of Activity Class
		Iterator<SootMethod> methodIter = activityClass.methodIterator();
		while (methodIter.hasNext())
		{
			SootMethod m = methodIter.next();
			
			//	
			// Skip method without active body
			if (!SootUtil.ensureMethodActiveBody(m))
			{
				continue;
			}
			
			//
			// Traverse the statements in onCreate method
			// to extract setContentView statement
			Iterator<Unit> unitIter = m.getActiveBody().getUnits().iterator();
			while (unitIter.hasNext())
			{
				//
				// We only care about invoke statement
				Unit unit = unitIter.next();
				if (!(unit instanceof InvokeStmt))
				{
					continue;
				}				
				
				//
				// We only care about invoke of setContentView/inflate method
				InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
				String invokedMethodName = invokeExpr.getMethod().getName();
				if (!isInvokeOfSetLayoutMethod(invokedMethodName, unit.toString()))
				{
					continue;
				}
				
				//
				// Record root caller and its resource ID
				// Currently we record info in displayable format directly.
				// For both setContentView and inflate,
				// the resource ID is the first argument.
				String resourceId = invokeExpr.getArg(0).toString();
				
				// Save resource ID to activity ID cache
				activityClassIdCache.put(className, resourceId);
				
				return resourceId;
			}			
		}
		
		//
		// No setContentView method found in current Activity class,
		// maybe its base Activity class has resource ID.
		// If we can't determine ID with super class,
		// "UNDETERMINED" is returned.
		String activityClassId = getActivityIdFromSuperClass(activityClass);
		
		// Save activity class ID to cache
		activityClassIdCache.put(className, activityClassId);
		
		return activityClassId;
	}
	
	/**
	 
		Check if a root caller class is a child of Activity class.
		
		If so, record its related info and keyword on sensitive user info.

	 */
	private void inspectRootCallerClass(SootClass sootClass, String keyword, int keywordUnitNum)
	{		
		//
		// We only care about root caller class which is a 
		// child of class managing content display
		if (!isChildOfDisplayableClass(sootClass))
		{
			return;
		}
		
		//
		// Find out the ID of activity class
		String activityId = getIdOfActivityClass(sootClass);
		
		//
		// Record root caller activity class
		String rootCallerClassName = sootClass.getName();
		rootCallerClassInfo.add(Integer.toString(keywordUnitNum) + ',' + rootCallerClassName + ',' + keyword + ',' + activityId);
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
	
	/**
	 
		Inspect the root caller of a given Jimple statement.
		
		methodStack is used for recording the methods in the call chain
		to avoid invocation cycle.

	 */
	private void inspectCaller(JimpleHit jimpleHit, Stack<SootMethod> methodStack)
	{
		// Find out the method contains the Jimple statement
		SootMethod m = Main.cfgOfApk.getMethodOf(jimpleHit.jimple);
		
		//
		// Lookup cache first
		if (methodRootCallerCache.containsKey(m))
		{
			//
			// Root caller found.
			// Inspect root caller directly
			inspectRootCallerClass(methodRootCallerCache.get(m), jimpleHit.keyword, jimpleHit.keywordUnitNum);
			
			return;
		}
		
		//
		// Find out the callers of current method
		Set<Unit> callers = Main.cfgOfApk.getCallersOf(m);
		if (callers.isEmpty()
			// For FlowDroid, all entrypoint methods are called from dummyMainClass
			|| areCallersFromDummyMain(callers))
		{
			//
			// Current method is a root caller
			
			// Inspect and record related information of the class
			SootClass rootCallerClass = m.getDeclaringClass();
			inspectRootCallerClass(rootCallerClass, jimpleHit.keyword, jimpleHit.keywordUnitNum);
			
			//
			// Save root caller of current method to cache
			methodRootCallerCache.put(m, rootCallerClass);
		}
		else
		{
			if (methodStack.contains(m))
			{
				// An invocation cycle encountered
				// Don't inspect caller statements any more
				return;
			}
			
			//
			// Record current method on stack
			// for inspecting method in next-level
			methodStack.push(m);
			
			for (Unit caller : callers)
			{
				// Build a new instance of JimpleHit
				// to record jimple and corresponding keyword
				JimpleHit callerHit = new JimpleHit();
				callerHit.jimple = caller;
				// Transit keyword, keywordUnitNum
				callerHit.keyword = jimpleHit.keyword;
				callerHit.keywordUnitNum = jimpleHit.keywordUnitNum;
				
				inspectCaller(callerHit, methodStack);
			}
			
			//
			// Remove current method and
			// return to an upper level
			methodStack.pop();
		}
	}
	
	private void inspectRootCaller(List<JimpleHit> jimples)
	{
		//
		// Check assumptions
		assert jimples != null;
		
		//
		// CORNER CASE: When the list is empty,
		// This function still work.
		for (JimpleHit jimpleHit : jimples)
		{
			//
			// Allocate an method stack for each Jimple statement
			// to avoid invocation cycle
			Stack<SootMethod> methodStack = new Stack<SootMethod>();
			inspectCaller(jimpleHit, methodStack);
		}
	}

	RootCallerInspector(List<JimpleHit> jimples)
	{
		//
		// Check assumptions
		assert Main.cfgOfApk != null;
		
		inspectRootCaller(jimples);
	}
	
	//
	// Output information access methods
	Set<String> getRootCallerClassInfo()
	{
		return rootCallerClassInfo;
	}
}
