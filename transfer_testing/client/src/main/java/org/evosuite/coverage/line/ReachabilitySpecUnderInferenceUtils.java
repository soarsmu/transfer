package org.evosuite.coverage.line;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.assertion.Inspector;
import org.evosuite.assertion.InspectorManager;
import org.evosuite.assertion.SerializableInspector;
import org.evosuite.coverage.line.ReachingSpec.NestedInspectors;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.instrumentation.testability.StringHelper;
import org.evosuite.seeding.CastClassManager;
import org.evosuite.seeding.ConstantPoolManager;
import org.evosuite.testsuite.TransferTestSuiteAnalyser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ReachabilitySpecUnderInferenceUtils {

	protected static final Logger logger = LoggerFactory.getLogger(ReachabilitySpecUnderInferenceUtils.class);

	public static ReachingSpec specUnderInference; // caller specification
	public static ReachingSpec calleeSpecification; // what was previously inferred.

	public static Map<String, Class<?>> favourConcreteTypes = new HashMap<>();
	
	public static int MAX_DEPTH = 2;
	
	public static int progressHalted = 0;
	public static double progressHaltedFitness = 1.1;
	public static double iterationFitness = 1.1;

	static boolean alreadyInit = false; 
	
	static {
		init();
	}

	private ReachabilitySpecUnderInferenceUtils() {

	}

	public static void init() {
		if (alreadyInit) {
			logger.warn("called init twice");
			return;
		}

		if (ReachingSpec.start == null) {
			ReachingSpec.start = Instant.now();
		}
		
		specUnderInference = null; // what we infer
//		calleeSpecification = deserialize("calleeMethod.spec.evosuite_target");
		
		

		List<String> lines;
		try {
			lines = Files.readAllLines(new File(ReachabilityCoverageFactory.CALLER_METHOD_EVOSUITE_TARGET_FILE).toPath());
			
			logger.info("read target class and method. lines size = " + lines.size());
			
			
			// first line is the code location
			String line = lines.get(0);
			
			ReachabilityCoverageFactory.targetCallerClazz = line.split(":")[0];
			ReachabilityCoverageFactory.targetCallerMethod = line.split(":")[1];
			
			
			lines = Files.readAllLines(new File(ReachabilityCoverageFactory.CALLEE_METHOD_EVOSUITE_TARGET_FILE).toPath());
			
			line = lines.get(0);
			if (!line.trim().isEmpty()) {
				ReachabilityCoverageFactory.targetCalleeClazz = line.split(",")[0];
				ReachabilityCoverageFactory.targetCalleeClazzAsNormalName = ReachabilityCoverageFactory.targetCalleeClazz.replaceAll("/", ".");
				
				
				ReachabilityCoverageFactory.targetCalleeMethod = line.substring(line.indexOf(',') + 1);
				
				ReachabilityCoverageFactory.targetCalleeMethodIsCtor = ReachabilityCoverageFactory.targetCalleeMethod.contains("init>");
				
				ReachabilityCoverageFactory.targetCalledClassPrefix = ReachabilityCoverageFactory.targetCalleeClazzAsNormalName.substring(0,
						ReachabilityCoverageFactory.targetCalleeClazzAsNormalName.lastIndexOf('.'));
				logger.warn("set targetCalledClazz and method: ReachabilityCoverageFactory.targetCalleeClazzAsNormalName=" + ReachabilityCoverageFactory.targetCalleeClazzAsNormalName);
				logger.warn("	 	method: =" + ReachabilityCoverageFactory.targetCalleeMethod);
			}

			lines = Files.readAllLines(new File(ReachabilityCoverageFactory.CALLEE_METHOD_TEST_EVOSUITE_TARGET_FILE).toPath());
			
			line = lines.get(0);
			if (!line.trim().isEmpty()) {
				ReachabilityCoverageFactory.targetCalledClazzTestMethodNames = Arrays.asList(line.split(","));
 			}
			
			lines = Files.readAllLines(new File(ReachabilityCoverageFactory.ADDITIONAL_CLASSES_FILE).toPath());
			
			line = lines.get(0);
			if (!line.trim().isEmpty()) {
				for (String additionalClass : Arrays.asList(line.split(","))) {
					ReachabilityCoverageFactory.additionalClasses.add(additionalClass);
				}
			}
			
			ReachabilityCoverageFactory.checkAtStart = !new File(ReachabilityCoverageFactory.CHECK_AT_END_FILE).exists();
			
			ReachabilityCoverageFactory.abalation_turnOffOtherGoals = new File(ReachabilityCoverageFactory.TURN_OFF_OTHER_GOALS).exists();
			if (ReachabilityCoverageFactory.abalation_turnOffOtherGoals) {
				logger.warn("==== DISABLING COMPUTATION OF OTHER FITNESS FUNCTIONS ========");
				logger.warn("==== THIS MAKES SENSE ONLY FOR ABLATION STUDY========");
			}
			
			if (new File("call_graph_methods.log").exists()) {
				lines = Files.readAllLines(new File("call_graph_methods.log").toPath());
			} else {
				lines = new ArrayList<>();
			}
			for (String classPlusMethod: lines) {
				String clazz = classPlusMethod.split(":")[0];
				String method = classPlusMethod.split(":")[1];
				
				if (!ReachabilityCoverageFactory.classToFunctionAlongCallGraph.containsKey(clazz)) {
					ReachabilityCoverageFactory.classToFunctionAlongCallGraph.put(clazz, new HashSet<>());
				}
				ReachabilityCoverageFactory.classToFunctionAlongCallGraph.get(clazz).add(method);
				
				ReachabilityCoverageFactory.additionalClasses.add(clazz);
			}
			
			
			// TODO make this configurable
			if (ReachabilityCoverageFactory.targetCalleeMethodIsCtor) {
				ReachabilityCoverageFactory.doNestedChecks = false;
			} else {
				ReachabilityCoverageFactory.doNestedChecks = !new File(ReachabilityCoverageFactory.NESTED_CHECK_FILE).exists();
				logger.warn("do nested checked?" + ReachabilityCoverageFactory.doNestedChecks);
			}
			
			if (new File(ReachabilityCoverageFactory.FORBIDDEN_CALLS).exists()) {
				lines = Files.readAllLines(new File(ReachabilityCoverageFactory.FORBIDDEN_CALLS).toPath());
				for (String oneLine : lines) {
					ReachabilityCoverageFactory.forbiddenMethodCallsOnCallee.add(oneLine);
				}
			}
			
		} catch (IOException e) {
			logger.warn("error reading callee method);");
			throw new RuntimeException(e);
		}
		
		
		
		
		alreadyInit = true; // for testing my own assumptions about the workings of evosuite
	}
	
	
	

	public static double compareObjects(ReachingSpec specUnderAnalysis, List<Object> realArgs) {
		double total = 0; // the higher, the more similar
		
		// split 1.0 into `number of args` partitions.
		//  in each partition, split again by the number of inspectors
		// for each inspector, edit distance/1 or 0 if boolean 
		int totalPossible = 0;
		for (Integer size: specUnderAnalysis.partitionSizes) {
			totalPossible += size;
		}
		
		for (int i = 0; i < realArgs.size(); i++) {
			Object arg = realArgs.get(i);

			// nullity checks
			// if epxected is null (check against argIsNull)
			// and actual is null, increase score and move to to next arg 
			if (specUnderAnalysis.argIsNull.get(i)) {
				if (arg == null) {
					total += specUnderAnalysis.partitionSizes.get(i);
				}
				continue;
			}
			
			if (arg == null) {
				continue;
			}

			// otherwise, check the return type to know what comparison to perform
			// first, check against string-like stuff
			if (arg instanceof String || arg instanceof byte[] || arg instanceof char[]) {

				String expected = specUnderAnalysis.argStringValue.get(i).iterator().next();

				String argString;
				if ( arg instanceof char[]) {
					argString =  new String((char[]) arg);
				} else {
					argString = arg instanceof String ? (String) arg : new String((byte[]) arg, StandardCharsets.ISO_8859_1);
				}

				float similarity = stringSimilarity(expected, argString);
				
				total += similarity * specUnderAnalysis.partitionSizes.get(i);
				
			} else {
				// otherwise, invoke inspectors 
				List<Inspector> inspectors = InspectorManager.getInstance().getInspectors(arg.getClass());

				for (Inspector inspector : inspectors) {

					// Don't call inspector methods on mock objects
					if (arg.getClass().getCanonicalName().contains("EnhancerByMockito"))
						continue;

					Object value;
					try {
						value = inspector.getValue(arg);
					} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
						e.printStackTrace();
						logger.error("error inspecting object... inspector= " + inspector + " arg= " + arg, e);
						logger.error("error is " , e );
						continue;
					}

					// if string-like
					if (inspector.getReturnType().equals(String.class) || inspector.getReturnType().equals(byte[].class)) {
						if (specUnderAnalysis.argStringInspectors.get(i).get(inspector) == null) {
							continue;
						}
						String expected = specUnderAnalysis.argStringInspectors.get(i).get(inspector).iterator().next();
						if (value == null) {
							if (expected == null) { 
								// match on nulls
								total += 1;
								
							}
							continue;
						}

						String valueString = value instanceof String ? (String) value : new String((byte[]) value, StandardCharsets.ISO_8859_1);
						if (valueString.length() > Properties.MAX_STRING)
							continue;

						// Avoid asserting anything on values referring to mockito proxy objects
						if (valueString.toLowerCase().contains("EnhancerByMockito"))
							continue;
						if (valueString.toLowerCase().contains("$MockitoMock$"))
							continue;

						if (!specUnderAnalysis.argStringInspectors.get(i).containsKey(inspector)) {
							continue;
						}
						
						if (expected == null) {
							continue;
						} else {
							total += 1; // increase if expected was not null, and current value is not null either
						}
						
						float similarity = stringSimilarity(expected, valueString);

						total += similarity;

					} else if (inspector.getReturnType().equals(Boolean.class)) { 
						// if boolean-like
						
						if (!specUnderAnalysis.argBoolInspectors.get(i).containsKey(inspector)) {
							continue;
						}
						
						boolean expected = specUnderAnalysis.argBoolInspectors.get(i).get(inspector);

						total += expected == (Boolean) value ? 1 : 0;
					} else if (inspector.getReturnType().isEnum()) {
						// if enum-like
						
						if (!specUnderAnalysis.argEnumInspectors.get(i).containsKey(inspector)) {
							continue;
						}

						String expected = specUnderAnalysis.argEnumInspectors.get(i).get(inspector);
						
						float similarity = stringSimilarity(expected, value.toString());
						total += similarity;

					} else {
						// if object-like, we compare strings inspectors on it
						if (!specUnderAnalysis.nestedStringInspectors.get(i).containsKey(inspector)) {
							continue;
						}
						if (!ReachabilityCoverageFactory.doNestedChecks) {
							continue;
						}
						
						NestedInspectors expected = specUnderAnalysis.nestedStringInspectors.get(i).get(inspector);
						
						if (value != null) {
							total = recursivelyCheckNestedInspector(expected, total, value, 0);
						}

					}
				}

			}

		}
		

		if (total == totalPossible) {

			logger.warn("satisfied condition");
			
		} else {
		}

		return total / totalPossible;
	}

	public static float stringSimilarity(String expected, String argString) {
		int longerLen = Integer.max(expected.length(), argString.length());
		int edit = StringHelper.editDistance(expected, argString);
		
		float similarity = 1 - (float) edit / longerLen; // max similarity when no edits required. i.e., same string
				// worst similarity is when all characters are to be replaced/added.
		return similarity;
	}
	
	public static void recordObjects(ReachingSpec specUnderAnalysis, List<Object> realArgs) {
		
		
		List<Type> looseTypes = getTypesListedInCalleSignature();
		
		for (int i = 0; i < realArgs.size(); i++) {
			Object arg = realArgs.get(i);

			specUnderAnalysis.argStringInspectors.add(new HashMap<>());
			specUnderAnalysis.argBoolInspectors.add(new HashMap<>());
			specUnderAnalysis.argEnumInspectors.add(new HashMap<>());
			specUnderAnalysis.argStringValue.add(new HashSet<>());
			
			specUnderAnalysis.nestedStringInspectors.add(new HashMap<>());
			
			if (arg == null) {
				specUnderAnalysis.argIsNull.add(true);
				specUnderAnalysis.partitionSizes.add(1);
				continue;
			}
			specUnderAnalysis.argIsNull.add(false);
			
			int numberOfInpectors = 0;

			if (arg instanceof String || arg instanceof byte[] || arg instanceof char[] ) {
				String convertedToString ;
				if ( arg instanceof char[]) {
					convertedToString =  new String((char[]) arg);
				} else {
					convertedToString = arg instanceof String ? (String) arg : new String((byte[]) arg, StandardCharsets.ISO_8859_1);
				}
				specUnderAnalysis.argStringValue.get(i).add(convertedToString);
				numberOfInpectors += 1;
				
				ConstantPoolManager.getInstance().addSUTConstant(convertedToString);

			} else {
				if (arg instanceof Class) {
					String typeName = ((Class) arg).getTypeName();
					ConstantPoolManager.getInstance().addSUTConstant(org.objectweb.asm.Type.getObjectType(typeName));
					CastClassManager.getInstance().addCastClass(typeName, 1); // TRANSFER: make it possible to instantiate generic with the typname
				} 
				
				numberOfInpectors = putInspectorOutputIntoSpec(specUnderAnalysis, i, arg, numberOfInpectors);
			}

			specUnderAnalysis.partitionSizes.add(numberOfInpectors);
			
			
			
			if (looseTypes.size() <= i) {
				continue;
			}
			Type looseType = looseTypes.get(i);
			Object actualArg = realArgs.get(i);
			if (actualArg != null) {
				Class<? extends Object> actualClass = actualArg.getClass();
				
				if (!actualClass.toString().equals(looseType.toString())) {
					// then this means we can do better when we try to satisfy the parameters when we get a variable of this type
					if (!favourConcreteTypes.containsKey(looseType.toString())) {
						favourConcreteTypes.put(looseType.toString(), actualClass);
						
						// hack, some classes just wrap around others and are useless, we need to obtain the real concrete classes that is wrapped
						// TODO keep track of the arguments to the constructor of these "wrapper"/"decorator" classes
						// applies for BufferedReader, BufferedWriter, BufferedOutputStream
						if (actualClass.toString().contains("BufferedInputStream")) {
							java.io.BufferedInputStream outer = (java.io.BufferedInputStream) actualArg;
						
							try {
								favourConcreteTypes.put(looseType.toString(), Class.forName("java.io.FileInputStream"));
							} catch (ClassNotFoundException e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}
				
			
		}
		
		
	}

	public static List<Type> typesCalleeSignatureCache = null;
	private static List<Type> getTypesListedInCalleSignature() {
		if (typesCalleeSignatureCache == null) {
			if (!ReachabilityCoverageFactory.targetCalleeMethod.contains(",")) {
				typesCalleeSignatureCache = new ArrayList<>();
			} else {
				typesCalleeSignatureCache = Arrays.asList(ReachabilityCoverageFactory.targetCalleeMethod.split("\\(")[1].split("\\)")[0].split(","))
						.stream()
						.map(string -> {
							try {
								return Class.forName(string);
							} catch (ClassNotFoundException e) {
	//							e.printStackTrace();
								throw new RuntimeException(e);
							}
						})
						.collect(Collectors.toList());
			}
			
			String methodReceiver = ReachabilityCoverageFactory.targetCalleeClazzAsNormalName;
			
			try {
				Class<?> methodReceiverClass = Class.forName(methodReceiver);
				
				Class<?>[] interfaces = methodReceiverClass.getInterfaces();
				Class<?> interfaceToInsert = null;
				if (interfaces.length == 0) {
					List<Class> parents = new ArrayList<>();
					Class<?> superClass = methodReceiverClass.getSuperclass();
					parents.add(superClass);
					
					while (!parents.isEmpty()) {
						Class current = parents.remove(0);
						Class[] currentInterfaces = current.getInterfaces();
						if (currentInterfaces.length == 0) {
							superClass = current.getSuperclass();

							if (superClass != null) {
								parents.add(superClass);
							}
							
						} else {
							interfaceToInsert = currentInterfaces[0];
							break;
						}
					}
					
				} else if (interfaces.length == 1) {
					interfaceToInsert = interfaces[0];
				}
				
				if (interfaceToInsert != null) {
					typesCalleeSignatureCache.add(0, interfaceToInsert);
				} else {
					typesCalleeSignatureCache.add(0, methodReceiverClass);
				}
				
				
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		
		return typesCalleeSignatureCache;
	}

	private static int putInspectorOutputIntoSpec(ReachingSpec specUnderAnalysis, int i, Object arg,
			int numberOfInpectors) {
		List<Inspector> inspectors = InspectorManager.getInstance().getInspectors(arg.getClass());

		for (Inspector inspector : inspectors) {

			// Don't call inspector methods on mock objects
			if (arg.getClass().getCanonicalName().contains("EnhancerByMockito"))
				continue;

			Object value;
			try {
				value = inspector.getValue(arg);
			} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
				logger.error("error inspecting object. inspector= " + inspector + " arg= " + arg, e);
				continue;
			}


			if (inspector.getReturnType().equals(String.class) || inspector.getReturnType().equals(byte[].class)) {
				if (value == null) {
					specUnderAnalysis.argStringInspectors.get(i).put(inspector, new HashSet<>());
					specUnderAnalysis.argStringInspectors.get(i).get(inspector).add(null);
					
					numberOfInpectors += 1;
					continue;
				} else {
					numberOfInpectors += 1;
				}

				String convertValue = value instanceof String ? (String) value : new String((byte[]) value, StandardCharsets.ISO_8859_1);
				if (convertValue.length() > Properties.MAX_STRING)
					continue;

				// Avoid asserting anything on values referring to mockito proxy objects
				if (convertValue.toLowerCase().contains("EnhancerByMockito"))
					continue;
				if (convertValue.toLowerCase().contains("$MockitoMock$"))
					continue;

				if (specUnderAnalysis.argStringInspectors.get(i).containsKey(inspector)) {
					throw new RuntimeException("should be uninitialized");
				}
				specUnderAnalysis.argStringInspectors.get(i).put(inspector, new HashSet<>());
				specUnderAnalysis.argStringInspectors.get(i).get(inspector).add(convertValue);
				
				numberOfInpectors += 1;
				
				ConstantPoolManager.getInstance().addSUTConstant(convertValue);

			} else if (inspector.getReturnType().equals(Boolean.class)) {
				specUnderAnalysis.argBoolInspectors.get(i).put(inspector, (Boolean) value);
				
				numberOfInpectors += 1;

			} else if (inspector.getReturnType().isEnum()) {
				specUnderAnalysis.argEnumInspectors.get(i).put(inspector, value.toString());
				numberOfInpectors += 1;
			} else  if (!(inspector.getReturnType().equals(Long.class) || inspector.getReturnType().equals(Integer.class) 
							|| inspector.getReturnType().equals(Double.class) || inspector.getReturnType().equals(Float.class)
							|| inspector.getReturnType().equals(Boolean.class)
							|| inspector.getReturnType().equals(Byte.class)
							|| inspector.getReturnType().equals(long.class) || inspector.getReturnType().equals(int.class)
							|| inspector.getReturnType().equals(double.class) || inspector.getReturnType().equals(float.class)
							|| inspector.getReturnType().equals(boolean.class) || inspector.getReturnType().equals(short.class)
							|| inspector.getReturnType().equals(byte.class))) {
				
				if (!ReachabilityCoverageFactory.doNestedChecks) {
					continue;
				}
				
				NestedInspectors nestedArgStringInspectors = new NestedInspectors();
				specUnderAnalysis.nestedStringInspectors.get(i).put(inspector, nestedArgStringInspectors);
				if (value != null) {
					numberOfInpectors = recursivelyInsertIntoNestedInspector(nestedArgStringInspectors, numberOfInpectors, value, 0);
				}
				
			}
		}
		return numberOfInpectors;
	}


	private static double recursivelyCheckNestedInspector(
			NestedInspectors nestedArgStringInspectors, double totalMatchingScore, Object value, int depth) {
		
		List<Inspector> nestedInspectors = InspectorManager.getInstance().getInspectors(value.getClass());

		for (Inspector nestedInspector : nestedInspectors) {
			
			// don't check if same type
//			if (nestedInspector.getReturnType().equals(value.getClass())) {
//				continue;
//			}
			
			
			Object nestedValue;
			try {
				nestedValue = nestedInspector.getValue(value);
			} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
				logger.error("error inspecting nested object. inspector= " + nestedInspector + " arg= " + value, e);
				
				continue;
			}
			
			if (nestedValue == null) {
				totalMatchingScore += 1;
				continue;
			}
			
			if (nestedInspector.getReturnType().equals(String.class) || nestedInspector.getReturnType().equals(byte[].class)) {

				totalMatchingScore += 1;
				
				if (nestedArgStringInspectors.value.isEmpty()) {
					totalMatchingScore += 1; // same as null?
					continue;
				}
				
				String expected = nestedArgStringInspectors.value.iterator().next();
				String argString = nestedValue instanceof String ? (String) nestedValue : new String((byte[]) nestedValue, StandardCharsets.ISO_8859_1);

				float similarity = stringSimilarity(expected, argString);
				
				totalMatchingScore += similarity;
	
			} else {
				
				if (nestedArgStringInspectors.goDeeper) {
					if (nestedInspector.getReturnType().equals(Long.class) || nestedInspector.getReturnType().equals(Integer.class) 
							|| nestedInspector.getReturnType().equals(Double.class) || nestedInspector.getReturnType().equals(Float.class)
							|| nestedInspector.getReturnType().equals(Boolean.class)
							|| nestedInspector.getReturnType().equals(long.class) || nestedInspector.getReturnType().equals(int.class)
							|| nestedInspector.getReturnType().equals(double.class) || nestedInspector.getReturnType().equals(float.class)
							|| nestedInspector.getReturnType().equals(boolean.class) || nestedInspector.getReturnType().equals(short.class)) {
						// ignore?
					} else {
						if (nestedArgStringInspectors.nested != null) {
							NestedInspectors next = nestedArgStringInspectors.nested.get(nestedInspector);
							totalMatchingScore = recursivelyCheckNestedInspector(next, totalMatchingScore, nestedValue, depth + 1);
						}
					}
				}
				
			}
		}
		return totalMatchingScore;
	}
	
	private static int recursivelyInsertIntoNestedInspector(
			NestedInspectors nestedArgStringInspectors, int numberOfInpectors, Object value, int depth) {
		
		List<Inspector> nestedInspectors = InspectorManager.getInstance().getInspectors(value.getClass());

		for (Inspector nestedInspector : nestedInspectors) {
			
			// don't check if same type?
			if (nestedInspector.getReturnType().equals(value.getClass())) {
				continue;
			}
			
			Object nestedValue;
			try {
				nestedValue = nestedInspector.getValue(value);
			} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
				logger.error("error inspecting nested object. inspector= " + nestedInspector + " arg= " + value);
				continue;
			}
			if (nestedInspector.getReturnType().equals(Boolean.class) || nestedInspector.getReturnType().equals(boolean.class)) {
				continue;
			}
  			if (nestedInspector.getReturnType().equals(String.class) || nestedInspector.getReturnType().equals(byte[].class)) {
				
				nestedArgStringInspectors.goDeeper = false;
				nestedArgStringInspectors.value = new HashSet<>(); 
				if (nestedValue == null) {
					
					numberOfInpectors += 1;
					continue;
				} else {
					numberOfInpectors += 1;
				}
				
				
				String convertValue = nestedValue instanceof String ? (String) nestedValue : new String((byte[]) nestedValue, StandardCharsets.ISO_8859_1);
				if (convertValue.length() > Properties.MAX_STRING)
					continue;

				// Avoid asserting anything on values referring to mockito proxy objects
				if (convertValue.toLowerCase().contains("EnhancerByMockito"))
					continue;
				if (convertValue.toLowerCase().contains("$MockitoMock$"))
					continue;

				
				nestedArgStringInspectors.goDeeper = false;
				nestedArgStringInspectors.value.add(convertValue);
				
				numberOfInpectors += 1;
				
				ConstantPoolManager.getInstance().addSUTConstant(convertValue);
			} else {
				
				
				if (depth < MAX_DEPTH && nestedValue != null) {
					if (nestedInspector.getReturnType().equals(Long.class) || nestedInspector.getReturnType().equals(Integer.class) 
							|| nestedInspector.getReturnType().equals(Double.class) || nestedInspector.getReturnType().equals(Float.class)
							|| nestedInspector.getReturnType().equals(Boolean.class)
							|| nestedInspector.getReturnType().equals(Byte.class)
							|| nestedInspector.getReturnType().equals(long.class) || nestedInspector.getReturnType().equals(int.class)
							|| nestedInspector.getReturnType().equals(double.class) || nestedInspector.getReturnType().equals(float.class)
							|| nestedInspector.getReturnType().equals(boolean.class) || nestedInspector.getReturnType().equals(short.class)
							|| nestedInspector.getReturnType().equals(byte.class)) {
						
						// ignore?
					} else { 
						nestedArgStringInspectors.goDeeper = true;
						nestedArgStringInspectors.nested = new HashMap<>();
						nestedArgStringInspectors.nested.put(nestedInspector, new NestedInspectors());
					
						numberOfInpectors = recursivelyInsertIntoNestedInspector(
								nestedArgStringInspectors.nested.get(nestedInspector), numberOfInpectors, nestedValue, depth + 1);
					}
				}
			}
		}
		return numberOfInpectors;
	}
	
	public static void serialize(ReachingSpec spec, String filePath) {
		GsonBuilder gson = new GsonBuilder();

		try (FileWriter fw = new FileWriter(filePath)) {
			gson.create().toJson(spec, fw);
		} catch (JsonIOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (IOException e) {

			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public static String serialize(ReachingSpec spec) {
		return new GsonBuilder().create().toJson(spec);
	}

	public static ReachingSpec deserialize(String filePath) {
		GsonBuilder gson = new GsonBuilder().registerTypeAdapter(SerializableInspector.class,
				new SerializableInspectorDeserializer());

		try (FileReader fr = new FileReader(filePath)) {
			ReachingSpec object = gson.create().fromJson(fr, ReachingSpec.class);
			return object;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	public static void main(String... args) {


	}

	public static class SerializableInspectorDeserializer implements JsonDeserializer<SerializableInspector> {

		@Override
		public SerializableInspector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			String formatedInspector = json.getAsJsonPrimitive().getAsString();

			String[] split = formatedInspector.split(":");
			Class<?> clazz;
			try {
				clazz = TestGenerationContext.getInstance().getClassLoaderForSUT().loadClass(split[0]);
//				clazz = Class.forName(split[0]);
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				logger.error("cannot get class name", e1);
				return null;
			}

			String methodName = split[1];

			String[] splitParams = null;
			if (split.length == 3) {
				String parameters = split[2];
				splitParams = parameters.split(",");
			}

			List<Class<?>> splitParamsClazz = splitParams != null ? Arrays.asList(splitParams).stream().map(str -> {
				try {
					return TestGenerationContext.getInstance().getClassLoaderForSUT().loadClass(str);
//					return Class.forName(str);
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}).collect(Collectors.toList()) : new ArrayList<>();

			Method method = null;
			for (int i = 0; i < clazz.getMethods().length; i++) {
				Method m = clazz.getMethods()[i];

				if (!m.getName().equals(methodName)) {
					continue;
				}

				if (splitParams != null) {

					if (m.getParameterCount() != splitParams.length) {
						continue;
					}
					if (!m.getParameterTypes()[i].equals(splitParamsClazz.get(i))) {
						continue;
					}
				}

				method = m;
				break;
			}
			return new SerializableInspector(clazz, method);

		}

	}
	
	
	private static String resetSourceFile = null;
	public static void performResetByCopyingFile() {
		if (resetSourceFile != null && resetSourceFile.isEmpty()) {
			return;
		}
		

		// needed for cases that rely on a file
		// sometimes, the generated test case overwrites the file
		// let's restore it
		if (resetSourceFile != null) {
			 try {
				 
				String fileName = resetSourceFile.substring(resetSourceFile.lastIndexOf("/"));
				if (new File(fileName).exists()) {
					new File(fileName).delete();
				}
				
				Files.copy(Paths.get(resetSourceFile), Paths.get(fileName));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			 logger.warn("resetting with " + resetSourceFile);
		}
		
		// uninitialized
		if (!new File("reset.evosuite").exists()) {
			resetSourceFile = "";
			return;
		}
		
		try {
			List<String> lines = Files.readAllLines(new File("reset.evosuite").toPath());
			for (String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				resetSourceFile = line;
			}
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		
	}
	
	
	public static void writeTestToOutputFile(String outputLines) {
		Path path = Paths.get("test.transfer_evosuite_output");
	    byte[] strToBytes = outputLines.getBytes();

	    try {
			Files.write(path, strToBytes);
		} catch (IOException e) {
			logger.error("failed to write to output", e);
		}
		
	}
	

}
