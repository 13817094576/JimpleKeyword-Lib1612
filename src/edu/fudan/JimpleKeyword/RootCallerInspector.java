package edu.fudan.JimpleKeyword;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import soot.SootMethod;
import soot.Unit;

/**

	This file contains code for finding out the root caller of 
	given Jimple statements.
	
	And record relating info if the root caller satisfies some
	conditions.

 */
class RootCallerInspector 
{
	Set<String> rootCallerClass = new HashSet<String>();
	
	//
	// DEBUG
	void dumpRootCallerClass()
	{
		for (String curRootCallerClass : rootCallerClass)
		{
			System.out.println(curRootCallerClass);
		}
	}
	
	/**
	 
		Inspect the root caller of a given Jimple statement.
		
		methodStack is used for recording the methods in the call chain
		to avoid invocation cycle.

	 */
	private void inspectCaller(Unit jimple, Stack<SootMethod> methodStack)
	{
		// Find out the method contains the Jimple statement
		SootMethod m = Main.cfgOfApk.getMethodOf(jimple);
		
		//
		// Find out the callers of current method
		Set<Unit> callers = Main.cfgOfApk.getCallersOf(m);
		if (callers.isEmpty())
		{
			//
			// Current method is a root caller
			
			// Record name of root caller class
			String rootCallerClassName = m.getDeclaringClass().getName();
			rootCallerClass.add(rootCallerClassName);
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
				inspectCaller(caller, methodStack);
			}
			
			//
			// Remove current method and
			// return to an upper level
			methodStack.pop();
		}
	}
	
	private void inspectRootCaller(List<Unit> jimples)
	{
		//
		// Check assumptions
		assert jimples != null;
		
		//
		// CORNER CASE: When the list is empty,
		// This function still work.
		for (Unit jimple : jimples)
		{
			//
			// Allocate an method stack for each Jimple statement
			// to avoid invocation cycle
			Stack<SootMethod> methodStack = new Stack<SootMethod>();
			inspectCaller(jimple, methodStack);
		}
	}

	RootCallerInspector(List<Unit> jimples)
	{
		//
		// Check assumptions
		assert Main.cfgOfApk != null;
		
		inspectRootCaller(jimples);
	}
}
