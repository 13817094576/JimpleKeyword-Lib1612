package edu.fudan.JimpleKeyword;

public class Config 
{
	//
	// FlowDroid config files
	public static final String CONFIG_FILE_TAINT_WRAPPER = "EasyTaintWrapperSource.txt";
	public static final String CONFIG_FILE_ANDROID_CALLBACK = "AndroidCallbacks.txt";
	
	//
	// Program switch
	public static boolean recordJimpleUsingHashMap;
	
	//
	// Program exit status
	public static final int EXIT_NORMAL = 0;
	public static final int EXIT_ERROR = 1;
}
