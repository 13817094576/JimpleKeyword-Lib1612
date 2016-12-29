package edu.fudan.JimpleKeyword;

import heros.InterproceduralCFG;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;
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
		System.out.println("-d\tOnly inspect reachable methods.");
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
		File sourceSinkFile = new File(Config.CONFIG_FILE_SOURCES_SINKS);
		if (!sourceSinkFile.isFile())
		{
			System.err.println("Config File SourcesAndSinks.txt missing\nAborted");
			throw new FileSystemNotFoundException("Config File SourcesAndSinks.txt missing");
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
		try 
		{
			app.calculateSourcesSinksEntrypoints(Config.CONFIG_FILE_SOURCES_SINKS);
		} 
		catch (IOException e) 
		{
			// Unexpected error
			// since we have checked the path of APK file when program launch
			// Fail-fast
			throw new RuntimeException("Unexpected IO Error on specified APK file");
		} 
		catch (XmlPullParserException e) 
		{
			// Unexpected error
			// Fail-fast
			throw new RuntimeException("Unexpected XML Parsing Error on specified APK file", e);
		}
		
		//
		// Run info-flow analysis to construct CFG and Call Graph
		
		//
		// Here we HACKed infoflow computation
		// in order to force FlowDroid always found source -> sink path.
		
		boolean forceAndroidJar = androidJarFile.isFile();
		InfoflowAndroidConfiguration infoFlowConfig = new InfoflowAndroidConfiguration();
		infoFlowConfig.setCodeEliminationMode(CodeEliminationMode.NoCodeElimination);
		DefaultPathBuilderFactory pathBuilderFactory =
				new DefaultPathBuilderFactory(infoFlowConfig.getPathBuilder(), infoFlowConfig.getComputeResultPaths());
		Infoflow infoFlow = new Infoflow(androidJar, forceAndroidJar, null, pathBuilderFactory);
		try 
		{
			infoFlow.setTaintWrapper(new EasyTaintWrapper(Config.CONFIG_FILE_TAINT_WRAPPER));
		} 
		catch (IOException e) 
		{
			// Unexpected error, Fail-fast
			throw new RuntimeException("Unexpected IO Exception:", e);
		}
		
		infoFlow.addResultsAvailableHandler(new ResultsAvailableHandler() 
		{
			@Override
			public void onResultsAvailable(IInfoflowCFG arg0,
					InfoflowResults arg1) 
			{
				// Save generated CFG
				cfgOfApk = arg0;
			}
			
		});
		
		infoFlow.setConfig(infoFlowConfig);
		infoFlow.setSootConfig(new SootConfigForAndroid());
		
		//
		// HACK: The SourceSinkManager is HACKED
		// so that we force to give FlowDroid some dummy source -> sink path.
		
		infoFlow.computeInfoflow(apkFile, androidJar, app.getEntryPointCreator(), new ISourceSinkManager() 
		{
			@Override
			public SourceInfo getSourceInfo(Stmt arg0,
					InterproceduralCFG<Unit, SootMethod> arg1) 
			{
				// Force found source
				return new SourceInfo(AccessPath.getEmptyAccessPath());
			}

			@Override
			public boolean isSink(Stmt arg0,
					InterproceduralCFG<Unit, SootMethod> arg1, AccessPath arg2) 
			{
				// Force found sink
				return arg0 instanceof ReturnStmt;
			}
			
		});
		
		//
		// Check if we got CFG of app
		if (cfgOfApk == null)
		{
			System.err.println("UNEXPECTED EXCEPTION: CFG of APK isn't generated.");
			System.err.println("In current version of FlowDroid, without any sources-sinks, we can't got CFG of APK");
			System.err.println("and we give FlowDroid some dummy source -> sink path currently");
			System.err.println("It's strange that we didn't get CFG of APK.");
			System.err.println("\nExecution Aborted.");
			
			System.exit(Config.EXIT_ERROR);
		}
		
		//
		// Process manifest file extract relating info
		
		// Process manifest file
		ProcessManifest manifestHandler;
		try 
		{
			manifestHandler = new ProcessManifest(apkFile);
		}
		catch (IOException e) 
		{
			// Unexpected error, Fail-fast
			throw new RuntimeException("Unexpected IO error on specified APK file", e);
		} 
		catch (XmlPullParserException e) 
		{
			// Unexpected error, Fail-fast
			throw new RuntimeException("Unexpected XML Parsing error on specified APK file", e);
		}
		
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
			else if (args[i].equals("-d"))
			{
				Config.reachableMethodsOnly = true;
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
		
		Set<String> rootActivityClassInfo = rootCallerInspector.getRootActivityClassInfo();
		System.out.println("Root Caller Activity Classes >>>>>>>>>>");
		for (String curRootActivityClassInfo : rootActivityClassInfo)
		{
			System.out.println(curRootActivityClassInfo);
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
		
		//
		// Do key taint tag data-flow analysis
		List<KeyTaintedVar> keyTaintedVars = keywordInspector.getKeyTaintedVars();
		KeyTaintAnalyzer keyTaintAnalyzer = new KeyTaintAnalyzer(keyTaintedVars);
		System.out.println("Key Tainted Sinks (Incorrect) >>>>>>>>>>");
		List<String> sinkInfoInStr = keyTaintAnalyzer.getSinkOutput();
		for (String curSinkInfo : sinkInfoInStr)
		{
			System.out.println(curSinkInfo);
		}
		System.out.println("Key Tainted Sinks (Incorrect) <<<<<<<<<<");
		
		String rootCallerMethodInfo = rootCallerInspector.getRootCallerMethodInfo();
		System.out.println("Root Caller Method >>>>>>>>>>");
		System.out.print(rootCallerMethodInfo);
		System.out.println("Root Caller Method <<<<<<<<<<");
		
		//
		// Find out the root caller methods of data blocks
		List<DataBlockRawStat> dataBlockWithKwRawStat = keywordInspector.getDataBlockWithKeywordsRawStat();
		RootCallerMethodInspector rootCallerMethodInspector = new RootCallerMethodInspector(dataBlockWithKwRawStat);
		
		String rootCallerMethodOfDataBlocks = rootCallerMethodInspector.getRootCallerMethodInfo();
		System.out.println("Root Caller Method of Data Blocks >>>>>>>>>>");
		System.out.print(rootCallerMethodOfDataBlocks);
		System.out.println("Root Caller Method of Data Blocks <<<<<<<<<<");
		
		//
		// Generate sensitive data info for TaintDroid dynamic tracking
		TDroidLink tdroidLink = new TDroidLink(dataBlockWithKwRawStat);
		Set<String> sensitiveDataInfo = tdroidLink.getSensitiveDataInfo();
		
		System.out.println("TDroid Sensitive Data >>>>>>>>>>");
		for (String curSensitiveDataInfo : sensitiveDataInfo)
		{
			System.out.println(curSensitiveDataInfo);
		}
		System.out.println("TDroid Sensitive Data <<<<<<<<<<");
		
		// Exit normally
	}

}
