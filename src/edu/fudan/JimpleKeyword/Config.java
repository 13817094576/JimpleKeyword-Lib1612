package edu.fudan.JimpleKeyword;

public class Config 
{
	//
	// FlowDroid config files
	public static final String CONFIG_FILE_TAINT_WRAPPER = "EasyTaintWrapperSource.txt";
	public static final String CONFIG_FILE_ANDROID_CALLBACK = "AndroidCallbacks.txt";
	
	//
	// Config files for JimpleKeyword tool
	public static final String CONFIG_FILE_INTERESTED_API = "InterestedAPIs.txt";
	public static final String CONFIG_FILE_LIBRARIES_LIST = "CommonLibraries.txt";
	
	//
	// Program switch
	
	// On default, recordJimpleUsingHashMap = false,
	// We can turn it on using "-m" command line switch
	public static boolean recordJimpleUsingHashMap;
	// On default, we only record Jimple invoke API we interested
	// we can turn off API filtering using "-a" command line switch
	public static boolean interestedApiOnly = true;
	// On default, we only care about APIs appeared in libraries list
	// Currently the switch is enabled by default.
	// We may add a command line switch in the future.
	public static boolean apiInLibrariesOnly = true;
	
	//
	// Program exit status
	public static final int EXIT_NORMAL = 0;
	public static final int EXIT_ERROR = 1;
}
