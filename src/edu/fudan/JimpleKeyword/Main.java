package edu.fudan.JimpleKeyword;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.android.IMethodSpec;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

public class Main 
{	
	private static KeywordList keywordList;
	
	private static void ShowUsage()
	{
		System.out.println("Usage: java -jar PluginStat.jar --android-jar ANDROID.JAR APP.APK KEYWORD-LIST.TXT");
		System.out.println("This program is written and tested on Java 1.7");
	}
	
	/**
	
		Check if the files and libraries required by this program exists.
		
		If any of them is abnormal, print error messages and abort program execution.
	
	 */
	private static void CheckDependencies()
	{
		//
		// Check if config file exist
		
		File taintWrapperFile = new File(Config.CONFIG_FILE_TAINT_WRAPPER);
		if (!taintWrapperFile.exists())
		{
			System.err.println("Config File EasyTaintWrapperSource.txt missing\nAborted");
			throw new FileSystemNotFoundException("Config File EasyTaintWrapperSource.txt missing");
		}
		File androidCallbackFile = new File(Config.CONFIG_FILE_ANDROID_CALLBACK);
		if (!androidCallbackFile.exists())
		{
			System.err.println("Config File AndroidCallbacks.txt missing\nAborted");
			throw new FileSystemNotFoundException("Config File AndroidCallbacks.txt missing");
		}		
	}
	
	private static void AnalyzeApkWithFlowDroid(String androidJar, String apkFile)
	{
		//
		// Check parameters
		File androidJarFile = new File(androidJar);
		if (!androidJarFile.exists())
		{
			throw new FileSystemNotFoundException("ANDROID.JAR not found: " + androidJar);
		}
		File apkFileObject = new File(apkFile);
		if (!apkFileObject.exists())
		{
			throw new FileSystemNotFoundException("APK File not found: " + apkFile);
		}
		
		//
		// Initialize Soot and FlowDroid
		SetupApplication app = new SetupApplication(androidJar, apkFile);
		app.setStopAfterFirstFlow(false);
		app.setEnableImplicitFlows(false);
		app.setEnableStaticFieldTracking(true);
		app.setEnableCallbacks(true);
		app.setEnableExceptionTracking(true);
		app.setAccessPathLength(1);
		app.setLayoutMatchingMode(LayoutMatchingMode.NoMatch);
		app.setFlowSensitiveAliasing(true);
		app.setCallgraphAlgorithm(CallgraphAlgorithm.AutomaticSelection);
		
		try 
		{
			app.setTaintWrapper(new EasyTaintWrapper(Config.CONFIG_FILE_TAINT_WRAPPER));
		} 
		catch (IOException e) 
		{
			// Unexpected error
			// since we have checked the path of config file when program launch
			// Fail-fast
			throw new RuntimeException("Unexpected IO Error on " + Config.CONFIG_FILE_TAINT_WRAPPER, e);
		}
		
		//
		// Compute CFG and Call Graph 
		// by calcuate empty source and sinks
		// and info-flow analysis
		
		// Construct source-sink manager
		Set<AndroidMethod> sources = new HashSet<AndroidMethod>();
		Set<AndroidMethod> sinks = new HashSet<AndroidMethod>();
		try 
		{
			app.calculateSourcesSinksEntrypoints(sources, sinks, new HashSet<IMethodSpec>());
		} 
		catch (IOException e) 
		{
			// Unexpected error
			// since we have checked the path of APK file when program launch
			// Fail-fast
			throw new RuntimeException("Unexpected IO Error on specified APK file");
		}
		
		// Run info-flow analysis to construct CFG and Call Graph
		InfoflowResults infoFlowResults = app.runInfoflow();
	}
	
	/**
	 
	Find out the Jimple statments contains keywords in APK
	by scanning the classes with FlowDroid

	 */
	private static List<String> findOutJimpleWithKeywords()
	{
		// Check assumptions
		assert keywordList != null;
		
		List<String> jimpleWithKeywords = new ArrayList<String>();
		
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
					
					if (keywordList.hasKeyword(curUnitInString))
					{
						jimpleWithKeywords.add(curUnitInString);
					}
				}
			}
		}
		
		return jimpleWithKeywords;
	}
	
	public static void main(String[] args) 
	{
		//
		// Check if required files and libraries exist		
		CheckDependencies();
		
		//
		// Parse command line parameters
		
		// If no parameters supplied, print usage and exit
		if (args.length <= 1)
		{
			ShowUsage();
			
			// Exit normally
			return;
		}
		
		String apkFile = null;
		String androidJar = null;
		String keywordListFileName = null;
		
		// Get ANDROID.JAR and APP.APK
		for (int i=0; i<args.length; i++)
		{
			if (args[i].equals("--android-jar")) 
			{
				// Get the path of ANDROID.JAR
				i++;
				androidJar = args[i];
			}
			else if (args[i].endsWith(".apk")) 
			{
				apkFile = args[i];
			}
			else if (args[i].endsWith(".txt"))
			{
				keywordListFileName = args[i];
			}
		}
		
		// Check if some parameters not supplied
		if (apkFile == null)
		{
			System.err.println("No APK file supplied\n");
			ShowUsage();
			throw new IllegalArgumentException("APK File parameter is invalid");
		}
		if (androidJar == null)
		{
			System.err.println("No ANDROID.JAR file path supplied\n");
			ShowUsage();
			throw new IllegalArgumentException("ANDROID.JAR file parameter is invalid");
		}
		if (keywordListFileName == null)
		{
			System.err.println("No KEYWORD-LIST.TXT file supplied\n");
			ShowUsage();
			throw new IllegalArgumentException("KEYWORD-LIST.TXT file parameter is invalid");
		}
		
		// Check if parameters are valid
		File apk = new File(apkFile);
		if (!apk.exists())
		{
			System.err.println("APK File doesn't exist: " + apkFile);
			throw new FileSystemNotFoundException("Specified APK File doesn't exist");
		}
		File androidJarFile = new File(androidJar);
		if (!androidJarFile.exists())
		{
			System.err.println("ANDROID.JAR path doesn't exist: " + androidJar);
			throw new FileSystemNotFoundException("ANDROID.JAR path doesn't exist");
		}
		File keywordListFile = new File(keywordListFileName);
		if (!keywordListFile.exists())
		{
			System.err.println("KEYWORD-LIST.TXT path doesn't exist: " + keywordListFileName);
			throw new FileSystemNotFoundException("KEYWORD-LIST.TXT path doesn't exist");
		}
		
		//
		// Analyze APK with FlowDroid
		// The analysis result of FlowDroid is stored in Scene class of Soot.
		AnalyzeApkWithFlowDroid(androidJar, apkFile);
		
		//
		// Load keyword list
		keywordList = new KeywordList(keywordListFileName);
		
		//
		// Find out the Jimple statements contains keyword
		List<String> jimpleWithKeywords = findOutJimpleWithKeywords();
		
		//
		// Output the list of plugins
		System.out.println("Jimple with Keywords in APK >>>>>>>>>>>");
		for (String curJimple : jimpleWithKeywords)
		{
			System.out.println(curJimple);
		}
		System.out.println("Jimple with Keywords in APK <<<<<<<<<<");
		
		// Exit normally
	}

}
