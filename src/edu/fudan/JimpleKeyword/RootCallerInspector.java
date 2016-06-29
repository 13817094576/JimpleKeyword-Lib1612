package edu.fudan.JimpleKeyword;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
	
	private SootMethod getOnCreateMethod(SootClass sootClass)
	{
		Iterator<SootMethod> methodIter = sootClass.methodIterator();
		while (methodIter.hasNext())
		{
			SootMethod m = methodIter.next();
			
			if (m.getName().contains("onCreate"))
			{
				return m;
			}
		}
		
		//
		// No method named onCreate
		return null;
	}
	
	/**
	 
		Check if a root caller class is a child of Activity class.
		
		If so, record its related info and keyword on sensitive user info.

	 */
	private void inspectRootCallerClass(SootClass sootClass, String keyword)
	{
		//
		// We only care about root caller class which is a 
		// child of class managing content display
		if (!isChildOfDisplayableClass(sootClass))
		{
			return;
		}
		
		//
		// Figure out the onCreate method in an Activity class
		SootMethod m = getOnCreateMethod(sootClass);
		if (m == null)
		{
			System.err.println("[WARN] No onCreate() method in Activity class: " + sootClass.getName());
			return;
		}
		
		//	
		// Skip method without active body
		if (!SootUtil.ensureMethodActiveBody(m))
		{
			return;
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
			// We only care about invoke of setContentView method
			// Here we only match "setContentView" instead of
			// full name which contains package name etc.
			// since setContentView usually appears in virtualinvoke 
			// and it doesn't contains android framework package name
			InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
			if (!invokeExpr.getMethod().getName().contains("setContentView"))
			{
				continue;
			}
			
			//
			// Record root caller and its resource ID
			// Currently we record info in displayable format directly.
			String resourceId = invokeExpr.getArg(0).toString();
			rootCallerClassInfo.add(sootClass.getName() + ',' + keyword + ',' + resourceId);
		}
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
		// Find out the callers of current method
		Set<Unit> callers = Main.cfgOfApk.getCallersOf(m);
		if (callers.isEmpty())
		{
			//
			// Current method is a root caller
			
			// Inspect and record related information of the class
			inspectRootCallerClass(m.getDeclaringClass(), jimpleHit.keyword);
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
				// Transit keyword
				callerHit.keyword = jimpleHit.keyword;
				
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
