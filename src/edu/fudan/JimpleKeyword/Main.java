package edu.fudan.JimpleKeyword;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.Scene;
import soot.Unit;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.android.IMethodSpec;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

/**

	The Main class contains entry point of JimpleKeyword tool
	
	and app-wide program status

 */
public class Main 
{		
	//
	// App-wide program status
	
	// Info on APK
	public static IInfoflowCFG cfgOfApk;
	
	public static String apkPackageName;
	
	// The company identifier of an APK is 
	// extracted from the first 2 parts of package name
	public static String apkCompanyId;
	
	private static void ShowUsage()
	{
		System.out.println("Usage: java -jar JimpleKeyword.jar [options] --android-jar ANDROID.JAR APP.APK KEYWORD-LIST.TXT");
		System.out.println("This program is written and tested on Java 1.7");
		
		System.out.println("\nOptions:");
		System.out.println("-m\tRecord and print Jimple statements using HashMap class");
		System.out.println("-a\tDisable API filtering feature, inspect Jimple statement regardless of API it invokes");
		System.out.println("-p\tEnable API in libraries only filtering. However, the libraries list may be incomplete");
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
		if (!taintWrapperFile.isFile())
		{
			System.err.println("Config File EasyTaintWrapperSource.txt missing\nAborted");
			throw new FileSystemNotFoundException("Config File EasyTaintWrapperSource.txt missing");
		}
		File androidCallbackFile = new File(Config.CONFIG_FILE_ANDROID_CALLBACK);
		if (!androidCallbackFile.isFile())
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
		if (!androidJarFile.isDirectory())
		{
			throw new FileSystemNotFoundException("ANDROID.JAR not found: " + androidJar);
		}
		File apkFileObject = new File(apkFile);
		if (!apkFileObject.isFile())
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
		
		//
		// Save some analysis results to app-wide program status
		cfgOfApk = app.getCFG();
		
		//
		// Process manifest file extract relating info
		
		// Process manifest file
		ProcessManifest manifestHandler = new ProcessManifest();
		manifestHandler.loadManifestFile(apkFile);
		
		// Extract info from manifest file
		Main.apkPackageName = manifestHandler.getPackageName();
		Main.apkCompanyId = SootUtil.getLeadingPartsOfName(Main.apkPackageName, 2);
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
			//
			// Parse input file arguments
			if (args[i].equals("--android-jar")) 
			{
				// Get the path of ANDROID.JAR
				// and skip next argument
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
			
			//
			// Parse options
			else if (args[i].equals("-m"))
			{
				Config.recordJimpleUsingHashMap = true;
			}
			else if (args[i].equals("-a"))
			{
				Config.interestedApiOnly = false;
			}
			else if (args[i].equals("-p"))
			{
				Config.apiInLibrariesOnly = true;
			}
			
			//
			// Default handler for other arguments
			else
			{
				System.err.println("[WARN] Unknown argument ignored: " + args[i]);
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
		if (!apk.isFile())
		{
			System.err.println("APK File doesn't exist: " + apkFile);
			throw new FileSystemNotFoundException("Specified APK File doesn't exist");
		}
		File androidJarFile = new File(androidJar);
		if (!androidJarFile.isDirectory())
		{
			System.err.println("ANDROID.JAR path doesn't exist: " + androidJar);
			throw new FileSystemNotFoundException("ANDROID.JAR path doesn't exist");
		}
		File keywordListFile = new File(keywordListFileName);
		if (!keywordListFile.isFile())
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
		KeywordList keywordList = new KeywordList(keywordListFileName);
		
		//
		// Find out the Jimple statements contains keyword
		KeywordInspector keywordInspector = new KeywordInspector(keywordList);
		
		//
		// Output the list of Jimple statements with keywords
		List<String> jimpleWithKeywords = keywordInspector.getJimpleWithKeywords();
		System.out.println("Jimple with Keywords in APK >>>>>>>>>>>");
		for (String curJimple : jimpleWithKeywords)
		{
			System.out.println(curJimple);
		}
		System.out.println("Jimple with Keywords in APK <<<<<<<<<<");
		
		//
		// Output the list of keywords hit
		Set<String> keywordsHit = keywordInspector.getKeywordsHit();
		System.out.println("Keywords Hit >>>>>>>>>>");
		for (String curKeyword : keywordsHit)
		{
			System.out.println(curKeyword);
		}
		System.out.println("Keywords Hit <<<<<<<<<<");
		
		//
		// Output the list of keywords in package
		Set<String> keywordsInPackage = keywordInspector.getKeywordsInPackage();
		System.out.println("Keywords in Package >>>>>>>>>>");
		for (String curKeywordsInPackage : keywordsInPackage)
		{
			System.out.println(curKeywordsInPackage);
		}
		System.out.println("Keywords in Package <<<<<<<<<<");
		
		//
		// Output Jimple statements using HashMap class
		if (Config.recordJimpleUsingHashMap)
		{
			List<String> jimpleUsingHashMap = keywordInspector.getJimpleUsingHashMap();
			System.out.println("Jimple using HashMap >>>>>>>>>>");
			for (String curJimpleUsingHashMap : jimpleUsingHashMap)
			{
				System.out.println(curJimpleUsingHashMap);
			}
			System.out.println("Jimple using HashMap <<<<<<<<<<");			
		}
		
		//
		// Find out and print the root caller classes
		List<JimpleHit> jimpleHit = keywordInspector.getJimpleHit();
		RootCallerInspector rootCallerInspector = new RootCallerInspector(jimpleHit);
		
		Set<String> rootCallerClassInfo = rootCallerInspector.getRootCallerClassInfo();
		System.out.println("Root Caller Activity Classes >>>>>>>>>>");
		for (String curRootCallerClassInfo : rootCallerClassInfo)
		{
			System.out.println(curRootCallerClassInfo);
		}
		System.out.println("Root Caller Activity Classes <<<<<<<<<<");
		
		//
		// Print the names of library packages
		Set<String> libraryPackageName = keywordInspector.getLibraryPackageName();
		System.out.println("Library Packages >>>>>>>>>>");
		for (String curPackageName : libraryPackageName)
		{
			System.out.println(curPackageName);
		}
		System.out.println("Library Packages <<<<<<<<<<");
		
		//
		// Print the keywords in app package and lib package
		Set<String> keywordsInAppPackage = keywordInspector.getKeywordsInAppPackage();
		System.out.println("Keywords in App Package >>>>>>>>>>");
		for (String keyword : keywordsInAppPackage)
		{
			System.out.println(keyword);
		}
		System.out.println("Keywords in App Package <<<<<<<<<<");
		
		Set<String> keywordsInLibPackage = keywordInspector.getKeywordsInLibPackage();
		System.out.println("Keywords in Lib Package >>>>>>>>>>");
		for (String keyword : keywordsInLibPackage)
		{
			System.out.println(keyword);
		}
		System.out.println("Keywords in Lib Package <<<<<<<<<<");
		
		List<String> dataBlockStatements = keywordInspector.getDataBlockStatement();
		System.out.println("Raw Data Block >>>>>>>>>>");
		for (String stat : dataBlockStatements)
		{
			System.out.println(stat);
		}
		System.out.println("Raw Data Block <<<<<<<<<<");
		
		List<String> dataBlockWithKeywords = keywordInspector.getDataBlockWithKeywords();
		System.out.println("Data Block with Keywords >>>>>>>>>>");
		for (String stat : dataBlockWithKeywords)
		{
			System.out.println(stat);
		}
		System.out.println("Data Block with Keywords <<<<<<<<<<");
		
		Map<String, Integer> dataBlockKeywordStat = keywordInspector.getKeywordsInDataBlocks();
		System.out.println("Keywords in Data Blocks >>>>>>>>>>");
		for (Entry<String, Integer> curKeywordStat : dataBlockKeywordStat.entrySet())
		{
			System.out.println(curKeywordStat.getKey() + ',' + curKeywordStat.getValue());
		}
		System.out.println("Keywords in Data Blocks <<<<<<<<<<");
		
		Set<String> simplifiedDataBlockStat = keywordInspector.getSimplfiedDataBlocks();
		System.out.println("Simplified Data Blocks >>>>>>>>>>");
		for (String stat : simplifiedDataBlockStat)
		{
			System.out.println(stat);
		}
		System.out.println("Simplified Data Blocks <<<<<<<<<<");
		
		// Exit normally
	}

}
