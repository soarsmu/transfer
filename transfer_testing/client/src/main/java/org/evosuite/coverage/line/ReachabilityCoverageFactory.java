package org.evosuite.coverage.line;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.instrumentation.LinePool;
import org.evosuite.runtime.util.AtMostOnceLogger;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testsuite.AbstractFitnessFactory;
import org.evosuite.testsuite.TransferTestSuiteAnalyser;
//import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReachabilityCoverageFactory extends AbstractFitnessFactory<ReachabilityCoverageTestFitness> {

	//both CALLER/CALLEE_METHOD_EVOSUITE_TARGET_FILE set in ReachabilitySpecUnderInferenceUtils
	static final String CALLER_METHOD_EVOSUITE_TARGET_FILE = "callerMethod.evosuite_target";
	static final String CALLEE_METHOD_EVOSUITE_TARGET_FILE = "calleeMethod.evosuite_target";
	static final String CALLEE_METHOD_TEST_EVOSUITE_TARGET_FILE = "calleeMethod.test";
	static final String ADDITIONAL_CLASSES_FILE = "additionalClasses.evosuite_target";
	static final String CHECK_AT_END_FILE = "check_at_end.evosuite_target"; // indicate how to perform the inspector check
	static final String NESTED_CHECK_FILE = "nested_checks.evosuite_target";
	static final String FORBIDDEN_CALLS = "forbidden_calls.evosuite_target";
	static final String TURN_OFF_OTHER_GOALS = "disable_other_goals.evosuite_target"; 
	
	
	private static final Logger logger = LoggerFactory.getLogger(ReachabilityCoverageFactory.class);
	
	public static String targetCallerClazz = null; 
	public static String targetCallerMethod = null; 
	
	public static String targetCalleeClazz = null; 
	public static String targetCalleeClazzAsNormalName = null; 
	public static String targetCalleeMethod= null;
	
	public static boolean targetCalleeMethodIsCtor = false;

	public static List<String> targetCalledClazzTestMethodNames = new ArrayList<>();
	
	public static String targetCalledClassPrefix = null;
	public static boolean targetCalledMethodIsStatic = false;
	
	public static Set<String> additionalClasses = new HashSet<>();
	
	public static Set<String> forbiddenMethodCallsOnCallee = new HashSet<>();
	
	public static boolean doNestedChecks = true;
	
	public static boolean abalation_turnOffOtherGoals = false;
	
	
	// the code in the SUT that we care about
	// functionsCovered are the functions containing search goals covered by the initial vuln-revealing test
	public static Set<String> functionsCovered = new HashSet<>();
	// 
	public static Map<String, Set<String>> classToFunctionAlongCallGraph = new HashMap<>();
	
	

	// the size of the map is equals to the total number of times the provided junit test cases executed the vulnerable method
	public static Map<String, ReachingSpec> reachingSpec = new HashMap<>();
	
	public static boolean isRecording = true;
	
	// check the state of the objects at the start or at the end
	public static boolean checkAtStart = true;
	
	public static float outputWeight;
	public static Throwable recordedException = null;
	public static boolean hasRecordedOutput = false;
	public static Object recordedOutput = null;
	public static boolean matchedOutput = false;
	
	// copied from LineCoverageFactory
	private boolean isEnumDefaultConstructor(String className, String methodName) {
		if (!methodName.equals("<init>(Ljava/lang/String;I)V")) {
			return false;
		}
		try {
			Class<?> targetClass = Class.forName(className, false,
					TestGenerationContext.getInstance().getClassLoaderForSUT());
			if (!targetClass.isEnum()) {
				logger.debug("Class is not enum");
				return false;
			}
			return Modifier.isPrivate(targetClass.getDeclaredConstructor(String.class, int.class).getModifiers());
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			logger.debug("Exception " + e);
			return false;
		}
	}


	@Override
	public List<ReachabilityCoverageTestFitness> getCoverageGoals() {
		List<ReachabilityCoverageTestFitness> goals = new ArrayList<>();
		
		// assume each test method invokes the vuln method once.
		int expectedLength = targetCalledClazzTestMethodNames.size();

		logger.warn("getting reachability coverage goals. ");
		for (int i = 0; i < expectedLength; i++) {
		
			String name = targetCalledClazzTestMethodNames.get(i);
			
			ReachabilityCoverageTestFitness goal = new ReachabilityCoverageTestFitness(name);
			if (!reachingSpec.containsKey(name)) {
				// recording state
				logger.warn("prepare to record");
				reachingSpec.put(name, new ReachingSpec());
			} else {
				AtMostOnceLogger.warn(logger, "previously recorded");
				if (!TransferTestSuiteAnalyser.goalsOfJunit.contains(goal)) {
					logger.warn("is it in goalsOfJunit?" + TransferTestSuiteAnalyser.goalsOfJunit.contains(goal));
				}
			
				
			}
			goals.add(goal);
		}
		
		return goals;
	}
	

	public static String descriptorToActualName(String methodNameDescriptor) {
		String methodName;
		
		methodName = methodNameDescriptor.split("\\)")[0] // strip off return type
				.replace("(L", "(").replace(";L", ",").replace("(B", "(byte")
				.replace(";B", ",byte").replace("(C", "(char").replace(";C", ",char")
				.replace("(D", "(double").replace(";D", ",double").replace("(F", "(float")
				.replace(";F", ",float").replace("(I", "(int").replace(";I", ",int")
				.replace("(J", "(long").replace(";J", ",long").replace("(S", "(short")
				.replace(";S", ",short").replace("(Z", "(boolean").replace(";Z", ",boolean")
				.replace("/", ".") + ")";
		methodName = methodName.replace(";)", ")");
		AtMostOnceLogger.warn(logger, "descriptorToActualName = " + methodNameDescriptor + " -> " + methodName);
		return methodName;
	}

	public static String targetIdentifier() {
		return targetCallerClazz + ":" + targetCallerMethod ;
	}
	
//	public static void main(String... args) {
//		
//		String uri = "@notexample.com/mypath";
//		    System.out.println(URI.create(uri).getScheme());
//		  
//	}
}
