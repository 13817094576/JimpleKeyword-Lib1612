package edu.fudan.JimpleKeyword;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

class KeywordInspector {

	private KeywordList keywordList;
	
	private List<String> jimpleWithKeywords;
	private Set<String> keywordsHit;
	
	/**
	 
		Scanning the classes with FlowDroid
		and find out the information we care.
		It finds out the Jimple statements with keywords
		and keywords hit.

	 */
	private void processJimple()
	{
		//
		// Check assumptions
		assert keywordList != null;
		
		// Initialize variables
		jimpleWithKeywords = new ArrayList<String>();
		keywordsHit = new HashSet<String>();
		
		//
		// Traverse the classes in APK
		Iterator<SootClass> classIter = Scene.v().getClasses().iterator();
		while (classIter.hasNext())
		{
			SootClass curClass = classIter.next();
			
			//
			// Traverse the methods in a class
			Iterator<SootMethod> methodIter = curClass.getMethods().iterator();
			while (methodIter.hasNext())
			{
				SootMethod m = methodIter.next();
				
				// Construct active body for some method
				if (!m.hasActiveBody() && m.isConcrete())
				{
					m.retrieveActiveBody();
				}
					
				// Skip method without active body
				if (!m.hasActiveBody())
				{
					continue;
				}
				
				//
				// Traverse the statements in a method
				Iterator<Unit> unitIter = m.getActiveBody().getUnits().iterator();
				while (unitIter.hasNext())
				{
					Unit curUnit = unitIter.next();
					String curUnitInString = curUnit.toString();
					
					// Check if current statement contains any known keyword
					String keywordInUnit = keywordList.figureOutKeyword(curUnitInString);
					if (keywordInUnit != null)
					{
						// Record current Jimple statement
						jimpleWithKeywords.add(curUnitInString);
						
						// Record current keyword
						keywordsHit.add(keywordInUnit);
					}
				}
			}
		}
	}
	
	KeywordInspector(KeywordList list)
	{
		//
		// Initialize private variables
		keywordList = list;
		
		//
		// Inspect Jimple statements
		processJimple();
	}
	
	List<String> getJimpleWithKeywords()
	{
		return jimpleWithKeywords;
	}
	
	Set<String> getKeywordsHit()
	{
		return keywordsHit;
	}
}
