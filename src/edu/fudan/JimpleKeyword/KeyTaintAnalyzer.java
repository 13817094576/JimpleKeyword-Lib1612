package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.RetStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.tagkit.Tag;

/**

	This class contains code used for propagating key taint tag
	and detect key taint tags at expected sink points.

 */
class KeyTaintAnalyzer 
{
	//
	// Starting points of data-flow analysis
	private List<KeyTaintedVar> keyTaintedVars;

	//
	// Sink point info for console output
	// The data format is <JimpleStatment, SinkPointOutputText>
	private Map<Unit, String> sinkOutput = new HashMap<Unit, String>();
	
	//
	// LeftKeyTag <- RightKeyTag
	//
	// TODO:
	// For completeness, we should merge the key tag of right value
	// to left value.
	// And we left this work for future implementation.
	private void propAssignStmt(AssignStmt stmt, Stack<SootMethod> methodStack)
	{
		//
		// Do propagation on invoke expression
		if (stmt.containsInvokeExpr())
		{
			InvokeExpr invokeExpr = stmt.getInvokeExpr();
			propInvokeExpr(invokeExpr, methodStack);
		}
		
		//
		// Do propagation on assignment
		ValueBox leftBox = stmt.getLeftOpBox();
		ValueBox rightBox = stmt.getRightOpBox();
		
		Tag keyTag = rightBox.getTag(KeyTaintTag.TAGNAME_KEYTAINT);
		if (keyTag != null)
		{
			leftBox.addTag(keyTag);
			
			//
			// DEBUG output
			if (Config.DEBUG)
			{
				String assignProp = String.format("%s<-%s, %s, %s", 
						leftBox.toString(), rightBox.toString(), keyTag.toString(), stmt.toString());
				System.out.println(assignProp);
			}
		}
	}
	
	//
	// This Pointer <- Merge(Tags of Arguments),
	// Return Value <- Prop taint in target method body
	private void propInstanceInvoke(InstanceInvokeExpr instInvoke, Stack<SootMethod> methodStack)
	{
		//
		// SPECIAL CASE: No argument in current invoke expression
		// Skip key taint tag prop step directly.
		if (instInvoke.getArgCount() == 0)
		{
			return;
		}
		
		//
		// Merge the taint of all arguments
		
		//
		// Read all tags of arguments
		int argCount = instInvoke.getArgCount();
		KeyTaintTag[] argTags = new KeyTaintTag[argCount];
		for (int i=0; i<argCount; i++)
		{
			ValueBox curArgBox = instInvoke.getArgBox(i);
			argTags[i] = (KeyTaintTag) (curArgBox.getTag(KeyTaintTag.TAGNAME_KEYTAINT));
		}
		
		//
		// Merge tags of arguments
		KeyTaintTag mergedTags = KeyTaintTag.merge(argTags);
		if (mergedTags == null)
		{
			//
			// If there is no taint on any argument of target method
			// There is no need to do propagation in the body of target method
			return;
		}
		
		//
		// Taint this pointer with merged tag list
		instInvoke.getBaseBox().addTag(mergedTags);
		
		//
		// DEBUG output
		if (Config.DEBUG)
		{
			String instInvokeTag = String.format("InstInvoke, %s, %s", 
					instInvoke.getBaseBox().toString(), instInvoke.toString());
			System.out.println(instInvokeTag);
		}
		
		//
		// Do taint prop in target method body
		
		// Get target method
		SootMethod targetMethod = instInvoke.getMethod();
		
		// Do taint prop in target method body
		propInMethodBody(targetMethod, methodStack);
	}
	
	private void propInMethodBody(SootMethod targetMethod, Stack<SootMethod> methodStack)
	{
		//
		// Check if target method from dummyMainClass
		if (isMethodFromDummyMain(targetMethod))
		{
			return;
		}
		
		//
		// Check if target method has already been processed
		if (methodStack.contains(targetMethod))
		{
			// Target method has already been processed
			// We come across a cyclic invocation
			// Skip propagation
			return;
		}
		
		//
		// Do taint propagation in target method
		
		//
		// Skip target method without active body
		// Active body of concrete method has already been retrieved
		// in Jimple scan phase.
		if (!targetMethod.hasActiveBody())
		{
			return;
		}
		
		// Get first unit of target method
		Unit firstUnitOfTarget = targetMethod.getActiveBody().getUnits().getFirst();
		if (firstUnitOfTarget == null)
		{
			return;
		}
		
		//
		// DEBUG output
		if (Config.DEBUG)
		{
			System.out.println("Prop in method body: " + targetMethod.toString() + " {");
		}
		
		// Do propagation in target method
		methodStack.push(targetMethod);
		propFromUnit(firstUnitOfTarget, methodStack);
		methodStack.pop();
		
		//
		// DEBUG output
		if (Config.DEBUG)
		{
			System.out.println("} Prop in method body: " + targetMethod.toString());
		}
	}
	
	private boolean hasTaintOnArgs(InvokeExpr invokeExpr)
	{
		//
		// Check each argument
		for (int i=0; i<invokeExpr.getArgCount(); i++)
		{
			Tag curTag = invokeExpr.getArgBox(i).getTag(KeyTaintTag.TAGNAME_KEYTAINT);
			if (curTag != null)
			{
				//
				// Since the tag name of KeyTaintTag is fixed.
				// We can safely cast Tag to KeyTaintTag.
				KeyTaintTag curKeyTaintTag = (KeyTaintTag)curTag;
				if (!curKeyTaintTag.isEmpty())
				{
					//
					// There is an argument which is key tainted.
					return true;
				}
			}
		}
		
		//
		// No argument is key tainted.
		return false;
	}
	
	private void propStaticInvoke(StaticInvokeExpr staticInvoke, Stack<SootMethod> methodStack)
	{
		//
		// Enter the body of invoked method
		// to tag the return value at the return statement.
		
		//
		// Find out target method
		SootMethod targetMethod = staticInvoke.getMethod();
		
		//
		// Check if there is no taint on any argument of target method
		// If so, we can skip the taint propagation in the body of target method
		// 
		// This can greatly reduce the amount of code the propagation logic should handle
		// since no taint on any argument is the most common case we'll come across
		if (!hasTaintOnArgs(staticInvoke))
		{
			return;
		}
		
		//
		// Do propagation in body of methods
		propInMethodBody(targetMethod, methodStack);
		
	}
	
	/**
	
		Handle taint tag prop with each type of
		invoke expression case by case.

	 */
	private void propInvokeExpr(InvokeExpr invokeExpr, Stack<SootMethod> methodStack)
	{
		if (invokeExpr instanceof InstanceInvokeExpr)
		{
			propInstanceInvoke((InstanceInvokeExpr)invokeExpr, methodStack);
		}
		else if (invokeExpr instanceof StaticInvokeExpr)
		{
			propStaticInvoke((StaticInvokeExpr)invokeExpr, methodStack);
		}		
	}
	
	private void propInvokeStmt(InvokeStmt stmt, Stack<SootMethod> methodStack)
	{
		//
		// Get invoke expression
		InvokeExpr invokeExpr = stmt.getInvokeExpr();
		
		//
		// Do propagation on invoke expression
		propInvokeExpr(invokeExpr, methodStack);
	}
	
	/**
	
		Handle taint tag prop with each type of
		statement case by case.

	 */
	private void propByUnit(Unit curUnit, Stack<SootMethod> methodStack)
	{
		//
		// AssignStmt may already contain class fields assignment
		if (curUnit instanceof AssignStmt)
		{
			propAssignStmt((AssignStmt)curUnit, methodStack);
		}
		
		//
		// InvokeStmt already contains object creation case 
		else if (curUnit instanceof InvokeStmt)
		{
			propInvokeStmt((InvokeStmt)curUnit, methodStack);
		}
		
		else
		{
			// Unexpected type of statement
			// Do nothing currently.
		}
	}
	
	private boolean isMethodFromDummyMain(SootMethod m)
	{
		//
		// Get the class name of method
		String className = m.getDeclaringClass().getName();
		
		//
		// Determine if method from dummyMainClass
		return className.contains("dummyMainClass");		
	}
	
	private void doPropForCallers(Unit retStmt, Stack<SootMethod> methodStack)
	{
		//
		// Find out caller sites
		SootMethod curMethod = Main.cfgOfApk.getMethodOf(retStmt);
		Collection<Unit> callerStmts = Main.cfgOfApk.getCallersOf(curMethod);
		
		//
		// Process each caller statements
		for (Unit curCallerStmt : callerStmts)
		{
			//
			// Check if caller method should be skipped
			SootMethod curCallerMethod = Main.cfgOfApk.getMethodOf(curCallerStmt);
			
			//
			// Skip methods from dummyMainClass
			if (isMethodFromDummyMain(curCallerMethod))
			{
				continue;
			}
			
			//
			// Check if current caller method has already been processed
			if (methodStack.contains(curCallerMethod))
			{
				// Current caller method has already been processed,
				// Skip current caller
				continue;
			}
			
			// Record caller to stack
			// and process caller method
			methodStack.push(curCallerMethod);
			propFromUnit(curCallerStmt, methodStack);
			methodStack.pop();
		}		
	}
	
	//
	// Check if arguments contains "http://" or "https://"
	private boolean argContainHttpAddr(List<Value> argValues)
	{
		for (Value curArgValue : argValues)
		{
			String curArgInStr = curArgValue.toString();
			
			//
			// Raw arg value in string is in C-style.
			// We unescape it here
			curArgInStr = StringUtil.unescapeString(curArgInStr);
			
			// Convert to lower case to ignore case
			curArgInStr = curArgInStr.toLowerCase();
			
			// Check if contains HTTP addr
			if (curArgInStr.contains("http://")
				|| curArgInStr.contains("https://"))
			{
				return true;
			}
		}
		
		//
		// No arg contains HTTP addr
		return false;
	}
	
	private KeyTaintTag mergeArgKeyTags(InvokeExpr invokeExpr)
	{
		//
		// Read all tags of arguments
		int argCount = invokeExpr.getArgCount();
		KeyTaintTag[] argTags = new KeyTaintTag[argCount];
		for (int i=0; i<argCount; i++)
		{
			ValueBox curArgBox = invokeExpr.getArgBox(i);
			argTags[i] = (KeyTaintTag) (curArgBox.getTag(KeyTaintTag.TAGNAME_KEYTAINT));
		}
		
		//
		// Merge tags of arguments
		KeyTaintTag mergedTag = KeyTaintTag.merge(argTags);
		if (mergedTag == null)
		{
			return null;
		}
		else
		{
			return mergedTag;
		}
	}
	
	/**
	 
		Check if a given statement is a sink point.
		
		If so, return the key consts.
		Otherwise, return null.

	 */
	private String tryGetSinkPointKeys(Unit curUnit)
	{
		//
		// Sink points must be invoke statements
		if (!(curUnit instanceof InvokeStmt))
		{
			return null;
		}
		
		//
		// Sink points must contain HTTP address
		InvokeExpr invokeExpr = ((InvokeStmt)curUnit).getInvokeExpr();
		if (!argContainHttpAddr(invokeExpr.getArgs()))
		{
			return null;
		}
		
		//
		// Merge arg key taint tags as final taint tag
		KeyTaintTag mergedTag = mergeArgKeyTags(invokeExpr);
		
		// Return merged tag in string format
		if (mergedTag == null)
		{
			return null;
		}
		else
		{
			return mergedTag.toString();
		}
	}
	
	/**

		Check if current statement is an expected sink point.
		
		If so, record sink info and return true.
		Otherwise, return false;

	 */
	private boolean tryHandleSinks(Unit curUnit)
	{
		//
		// Try get key consts at sink points
		String keyConstsAtSink = tryGetSinkPointKeys(curUnit);
		
		if (keyConstsAtSink != null)
		{
			//
			// Record sink point info
			// UNSURE: Currently we directly overwrite the key consts in HashMap
			sinkOutput.put(curUnit, keyConstsAtSink);
			
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 
	  	Handle key taint propagation on a given Unit.
	  	This method will scan the CFG with BFS order.
	  
	 	@param curUnit		Unit to process
	 	@param methodStack	A stack of method processed in order to avoid cyclic situation
	 	
	 */
	private void propFromUnit(Unit fromUnit, Stack<SootMethod> methodStack)
	{
		//
		// Initialize the queue of units to process
		Queue<Unit> units = new LinkedList<Unit>();
		units.add(fromUnit);
		
		while (!units.isEmpty())
		{
			//
			// Get the unit at the head of queue
			Unit curUnit = units.poll();
			
			//
			// Do taint prop for current unit
			propByUnit(curUnit, methodStack);
			
			//
			// Handle return statement
			if (curUnit instanceof ReturnStmt
				|| curUnit instanceof JReturnVoidStmt)
			{
				//
				// Prop taint in caller methods
				doPropForCallers(curUnit, methodStack);
				
				//
				// Return statements don't have successor statements
				return;
			}
			
			//
			// Handle sink points
			if (tryHandleSinks(curUnit))
			{
				//
				// We arrived at sink points,
				// no need to inspect successor statements
				return;
			}
			
			//
			// Handle next units
			List<Unit> nextUnits = Main.cfgOfApk.getSuccsOf(curUnit);
			units.addAll(nextUnits);
		}
	}
	
	private void doTaintProp(KeyTaintedVar keyTaintedVar)
	{
		//
		// Initializer a method stack to record
		// methods processed
		// in order to avoid cyclic situation.
		Stack<SootMethod> methodStack = new Stack<SootMethod>();
		
		//
		// Push the initial method to inspect to stack
		SootMethod startPointMethod = Main.cfgOfApk.getMethodOf(keyTaintedVar.taintSrcStmt);
		methodStack.push(startPointMethod);
		
		//
		// Prop taint beginning with the starting point statement		
		propFromUnit(keyTaintedVar.taintSrcStmt, methodStack);	
	}
	
	private void doTaintProp()
	{
		//
		// Do taint prop for each tainted container variable
		for (KeyTaintedVar curKeyTaintedVar : keyTaintedVars)
		{
			doTaintProp(curKeyTaintedVar);
		}
	}
	
	KeyTaintAnalyzer(List<KeyTaintedVar> keyTaintedVars)
	{
		//
		// Initialize starting points of data-flow analysis
		this.keyTaintedVars = keyTaintedVars;
		
		//
		// Do key taint propagation
		doTaintProp();
	}
	
	/**
	 
		Get info on sink points in readable string format

	 */
	List<String> getSinkOutput()
	{
		List<String> sinkOutputInStr = new ArrayList<String>(sinkOutput.size());
		for (Map.Entry<Unit, String> curSink : sinkOutput.entrySet())
		{
			//
			// Format current sink info
			String sinkInStr = curSink.getValue() + ',' + curSink.getKey().toString();
			
			//
			// Record current sink info in readable format
			sinkOutputInStr.add(sinkInStr);
		}
		
		return sinkOutputInStr;
	}
}
