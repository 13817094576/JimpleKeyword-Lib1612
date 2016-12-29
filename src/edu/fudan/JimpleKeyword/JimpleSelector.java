package edu.fudan.JimpleKeyword;

import java.io.File;

import edu.fudan.JimpleKeyword.io.InterestedApiList;
import edu.fudan.JimpleKeyword.io.LibrariesList;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SpecialInvokeExpr;

/**

	This class contains methods and configurations
	for determining if a given Jimple statement is 
	the one we interested in.

 */
class JimpleSelector 
{
	//
	// Data list for Jimple statement inspection
	private InterestedApiList interestedApiList;
	private LibrariesList librariesList;
	
	/**

		Judge if a given invoke stmt related to key-value operations.
		
		We check that if the invoked method has 2 parameters and 
		the type of the first parameter is String.
	
	 */
	boolean isInvokeStmtContainKeyValue(Unit unit)
	{
		//
		// Check if current invoke statement has 2 arguments
		// and the first one is string.
		// If so, we think this statement is interesting
		InvokeExpr invokeExpr = ((InvokeStmt)unit).getInvokeExpr();
		
		//
		// Skip special invoke statements
		// Key-Value pair doesn't likely appear in special invokes
		if (invokeExpr instanceof SpecialInvokeExpr)
		{
			return false;
		}
		
		// Check if the method invoked has 2 arguments
		if (invokeExpr.getArgCount() == 2)
		{
			// Check if the type of first argument is String
			String typeOfFirstArg = invokeExpr.getArg(0).getType().toString();
			if (typeOfFirstArg.equals("java.lang.String"))
			{
				return true;
			}
		}
		
		//
		// Current invoke stmt doesn't satisfy specified conditions
		return false;
	}
	
	/**
	
		Interface Method
	 
		This function performs inital Jimple statement filtering.
		
		The filter phases in this function must perform quickly,
		since this function will check every Jimple statement in the app.
	
	 */
	JimpleInitialJudgeStatus judgeJimpleInitially(Unit unit)
	{
		//
		// We only interested in Jimple statement
		// which invokes certain API 
		if (!(unit instanceof InvokeStmt))
		{
			// Skip non-invoke Jimple statement
			return JimpleInitialJudgeStatus.JIMPLE_NOT_INTERESTED;
		}		
		
		//
		// Check if current invoke statement has 2 arguments
		// and the first one is string.
		// If so, we think this statement is interesting
		if (isInvokeStmtContainKeyValue(unit))
		{
			return JimpleInitialJudgeStatus.JIMPLE_DEFINITE_HIT;
		}
		
		String unitInString = unit.toString();
		
		//
		// Check if current Jimple statement invokes
		// API we interested in
		if (Config.interestedApiOnly)
		{
			if (!interestedApiList.containInterestedApi(unitInString))
			{
				// Skip Jimple statement that doesn't contain interested API
				return JimpleInitialJudgeStatus.JIMPLE_NOT_INTERESTED;
			}
		}
		
		//
		// Given Jimple statement has passed initial quick screening conditions
		return JimpleInitialJudgeStatus.JIMPLE_NEED_DETAIL_INSPECTION;
	}
	
	/**
	 
	 	Interface Method

		This function performs slow steps of Jimple statement filtering.
		
		The Jimple statement passed in should be filtered in some extent in advance.
		If using this function to check every Jimple statement,
		the program will run very slow.
	
	 */
	boolean judgeJimpleInDetail(String unitInString)
	{
		//
		// Check if current API invoked is in the libraries list
		// The libraries list is long,
		// so the matching process is time consuming.
		if (Config.apiInLibrariesOnly)
		{
			if (!librariesList.containLibPackageName(unitInString))
			{
				// Skip Jimple statement that invokes API not in libraries list
				return false;
			}
		}
		
		//
		// Given Jimple statement has passed all screening conditions
		return true;
	}
	
	/**

		Initialize InterestedApiList class instance.
		
		If Config.interestedApiOnly is turned off, or 
		config file doesn't exist, null is returned.
	
	 */
	private InterestedApiList initInterestedApiList()
	{
		if (Config.interestedApiOnly)
		{
			//
			// Check if InterestedAPIs.txt exists
			File interestedApiListFile = new File(Config.CONFIG_FILE_INTERESTED_API);
			if (interestedApiListFile.isFile())
			{
				// Return initialized InterestedApiList instance
				return new InterestedApiList();
			}
			else
			{
				// Interested API list doesn't exist, 
				// Turn off Config.interestedApiOnly switch
				Config.interestedApiOnly = false;
				
				// Print warning message
				System.err.println("[WARN] Interested API list is missing, API filtering is disabled: "
									+ Config.CONFIG_FILE_INTERESTED_API);
				
				return null;
			}
		}
		else
		{
			// Config.interestedApiOnly switch is turned off
			return null;
		}
	}
	
	/**
	
		Initialize LibrariesList class instance.
		
		If Config.apiInLibrariesOnly is turned off, or 
		config file doesn't exist, null is returned.
	
	 */
	private LibrariesList initLibrariesList()
	{
		if (Config.apiInLibrariesOnly)
		{
			//
			// Check if CommonLibraries.txt exists
			File librariesListFile = new File(Config.CONFIG_FILE_LIBRARIES_LIST);
			if (librariesListFile.isFile())
			{
				// Return initialized LibrariesList instance
				return new LibrariesList();
			}
			else
			{
				// Libraries list doesn't exist, 
				// Turn off Config.apiInLibrariesOnly switch
				Config.apiInLibrariesOnly = false;
				
				// Print warning message
				System.err.println("[WARN] Libraries list is missing, API in libraries only filtering is disabled: "
									+ Config.CONFIG_FILE_LIBRARIES_LIST);
				
				return null;
			}
		}
		else
		{
			// Config.apiInLibrariesOnly switch is turned off
			return null;
		}
	}
	
	/**

		Initializer for this class.

	 */
	JimpleSelector()
	{
		//
		// Initialize data list for Jimple inspection
		interestedApiList = initInterestedApiList();
		librariesList = initLibrariesList();
	}
}

//
// Enumeration on Jimple statement status
// on initial quick judgement
enum JimpleInitialJudgeStatus
{
	JIMPLE_NOT_INTERESTED,
	JIMPLE_NEED_DETAIL_INSPECTION,
	JIMPLE_DEFINITE_HIT
}