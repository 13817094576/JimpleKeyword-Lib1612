package edu.fudan.JimpleKeyword;

public class Config 
{
	//
	// FlowDroid config files
	public static final String CONFIG_FILE_TAINT_WRAPPER = "EasyTaintWrapperSource.txt";
	public static final String CONFIG_FILE_ANDROID_CALLBACK = "AndroidCallbacks.txt";
	
	//
	// Config files for JimpleKeyword app
	public static final String CONFIG_FILE_INTERESTED_API = "InterestedAPIs.txt";
	
	//
	// Program switch
	
	// On default, recordJimpleUsingHashMap = false,
	// We can turn it on using "-m" command line switch
	public static boolean recordJimpleUsingHashMap;
	// On default, we only record Jimple invoke API we interested
	// we can turn off API filtering using "-a" command line switch
	public static boolean interestedApiOnly = true;
	
	//
	// Program exit status
	public static final int EXIT_NORMAL = 0;
	public static final int EXIT_ERROR = 1;
}
