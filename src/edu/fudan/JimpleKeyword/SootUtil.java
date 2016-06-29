package edu.fudan.JimpleKeyword;

import soot.SootMethod;

class SootUtil 
{
	/**
	 
		Try to retrieve the body of method first,
		then return whether the method has active body
		after attempt.

	 */
	static boolean ensureMethodActiveBody(SootMethod m)
	{
		//
		// Check assumptions
		assert m != null;
		
		//
		// First attempt to retrieve active body of method
		if (!m.hasActiveBody() && m.isConcrete())
		{
			m.retrieveActiveBody();
		}
		
		//	
		// Then return whether the method has active body
		return m.hasActiveBody();
	}
}
