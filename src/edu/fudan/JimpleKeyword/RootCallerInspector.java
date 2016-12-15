package edu.fudan.JimpleKeyword;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

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
	
	//
	// Output statistics information
	
	// Information on root caller classes.
	// It is adapted to be printed to console directly.
	private Set<String> rootActivityClassInfo = new TreeSet<String>();
	// Information on root caller methods.
	// This info is formatted in order to be printed to console directly.
	private StringBuilder rootCallerMethodInfo = new StringBuilder();
	
	//
	// Some constants to avoid inconsistency in code
	private static final String STRING_UNDETERMINED = "UNDETERMINED";
	private static final char PREFIX_SUPER_CLASS = 'S';

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
				return STRING_UNDETERMINED;
			}
			
			
			String activityClassId = getIdOfActivityClass(superClass);
			if (activityClassId.equals(STRING_UNDETERMINED))
			{
				// No resource ID in super class,
				// so ID can't be determined, keep activityClassId unchanged
				return activityClassId;
			}
			else
			{
				// Found resource ID in super class
				// flag the ID as ID from super class by adding 'S' prefix
							
				if (activityClassId.charAt(0) == PREFIX_SUPER_CLASS)
				{
					// If activity ID already has 'S' prefix, return it directly
					return activityClassId;
				}
				else
				{
					// Otherwise, add 'S' prefix
					return PREFIX_SUPER_CLASS + activityClassId;
				}
			}
		}
		else
		{
			//
			// No setContentView method found
			// and no resource ID in base Activity class,
			// Can't determine resource ID
			return STRING_UNDETERMINED;
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
		which typically means that the code in Activity class
		set the content of Activity using a method other than
		setContentView method,
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
	 
		Record information on root caller class.
		
		If the root caller class is derived from Activity class,
		record info on Activity.

	 */
	private void inspectRootCallerClass(SootClass sootClass, String keyword, int keywordUnitNum)
	{		
		//
		// If root caller class is derived from Activity class,
		// record info on Activity class.
		if (isChildOfDisplayableClass(sootClass))
		{
		
			//
			// Find out the ID of activity class
			String activityId = getIdOfActivityClass(sootClass);
			
			//
			// Record root caller activity class
			String rootCallerClassName = sootClass.getName();
			rootActivityClassInfo.add(Integer.toString(keywordUnitNum) + ',' + rootCallerClassName + ',' + keyword + ',' + activityId);

		}
	}
	
	/**
	 
		FlowDroid set all components called from dummyMainClass.
		So when a method is called from dummyMainClass, 
		this method is an entrypoint.
		
		This function is used for determining if all callers are
		from dummyMainClass to judge if a method is an entrypoint.

	 */
	private boolean areCallersFromDummyMain(Collection<Unit> callers)
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

	private void recordRootCallerMethodInfo(SootMethod rootCallerMethod, JimpleHit jimpleHit, Stack<SootMethod> callChain)
	{
		// Summary info of current root caller method
		String curMethodInfo = String.format("%d,%s", 
				jimpleHit.keywordUnitNum, jimpleHit.keyword);
		
		// Write method info to root caller method info buffer
		rootCallerMethodInfo.append(curMethodInfo);
		rootCallerMethodInfo.append('\n');
		
		// Write call chain info
		rootCallerMethodInfo.append(rootCallerMethod.getSignature());
		rootCallerMethodInfo.append('\n');
		for (int i=callChain.size()-1; i>=0; i--)
		{
			SootMethod curMethod = callChain.elementAt(i);
			rootCallerMethodInfo.append(curMethod.getSignature());
			rootCallerMethodInfo.append('\n');
		}
		
		// Write a separate white line
		rootCallerMethodInfo.append('\n');		
	}
	
	//
	// A set of signature of root caller methods
	// in order to avoid duplicate root caller method info
	private Set<String> rootCallerMethodID = new HashSet<String>();
	
	/**
	
		Record a unique signature of root caller method
		in order to avoid duplicated root caller method info.

	 */
	private boolean recordRootCallerID(SootMethod rootCallerMethod, JimpleHit jimpleHit, Stack<SootMethod> callChain)
	{
		//
		// Get fromMethod signature
		String fromMethodID = null;
		if (callChain.isEmpty())
		{
			fromMethodID = rootCallerMethod.getSignature();
		}
		else
		{
			fromMethodID = callChain.elementAt(0).getSignature();
		}
		
		//
		// We record keyword,fromMethod,rootCallerMethod as a signature
		String curID = String.format("%s,%s,%s",
				jimpleHit.keyword, fromMethodID, rootCallerMethod.getSignature());
		return rootCallerMethodID.add(curID);
	}
	
	/**

		Root caller method is found.
		
		Inspect the root caller method
		and record relating info on the method.

	 */
	private void inspectRootCallerMethod(SootMethod rootCallerMethod, JimpleHit jimpleHit, Stack<SootMethod> callChain)
	{
		//
		// Record ID of root caller methods
		// in order to avoid duplicated root caller methods info
		if (recordRootCallerID(rootCallerMethod, jimpleHit, callChain))
		{
			//
			// Current root caller method hasn't been recorded
			// Record its relating info
			recordRootCallerMethodInfo(rootCallerMethod, jimpleHit, callChain);
		}
		
		//
		// Inspect and record related information of the root caller class
		SootClass rootCallerClass = rootCallerMethod.getDeclaringClass();
		inspectRootCallerClass(rootCallerClass, jimpleHit.keyword, jimpleHit.keywordUnitNum);		
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
		// It's strange that sometimes getMethodOf returns null
		// in newer version of FlowDroid.
		if (m == null)
		{
			return;
		}
		
		//
		// Find out the callers of current method
		Collection<Unit> callers = Main.cfgOfApk.getCallersOf(m);
		if (callers.isEmpty()
			// For FlowDroid, all entrypoint methods are called from dummyMainClass
			|| areCallersFromDummyMain(callers))
		{
			//
			// Current method is a root caller
			// Inspect the root caller method
			inspectRootCallerMethod(m, jimpleHit, methodStack);
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
	
	Set<String> getRootActivityClassInfo()
	{
		return rootActivityClassInfo;
	}
	
	/**
	 
		This method returns formatted root caller methods info.

	 */
	String getRootCallerMethodInfo()
	{
		return rootCallerMethodInfo.toString();
	}
}
