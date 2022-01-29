package org.evosuite.coverage.line;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.evosuite.Properties;
import org.evosuite.assertion.Inspector;
import org.evosuite.assertion.InspectorManager;
import org.evosuite.assertion.SerializableInspector;
import org.evosuite.testcase.execution.CodeUnderTestException;
import org.evosuite.testcase.execution.Scope;
import org.evosuite.testcase.statements.EntityWithParametersStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.ArrayIndex;
import org.evosuite.testcase.variable.ConstantValue;
import org.evosuite.testcase.variable.FieldReference;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReachingSpec {
	private static final Logger logger = LoggerFactory.getLogger(ReachingSpec.class);

	private static boolean debug = true;


	// arguments: the i'th element stores a map of inspector methods to their
	// expected value
	// we only care about boolean and string inspectors now
	public List<Map<Inspector, Boolean>> argBoolInspectors = new ArrayList<>();
	public List<Map<Inspector, String>> argEnumInspectors = new ArrayList<>();
	public List<Map<Inspector, Set<String>>> argStringInspectors = new ArrayList<>();
	
//	public List<Map<Inspector, Map<Inspector, Set<String>>>> nestedArgStringInspectors = new ArrayList<>();
	public List<Map<Inspector, NestedInspectors>> nestedStringInspectors = new ArrayList<>();

	public List<Set<String>> argStringValue = new ArrayList<>(); // arg string membership check
	public List<Boolean> argIsNull = new ArrayList<>(); // arg is null check
	public volatile List<Integer> partitionSizes = new ArrayList<>(); // used for computing fitness during comparison. The highest number of inspectors for any argument.
	
	static Instant start;
	
	public static class NestedInspectors {
		
		boolean goDeeper;
		Set<String> value;
		
		Map<Inspector, NestedInspectors> nested;
	}

	public ReachingSpec() {

	}
	
	public boolean itIsKnown() {
		return !argBoolInspectors.isEmpty();
	}

	public boolean isReceiverSatisfied() {
		return true; // todo
	}


	public static class SatisfactionResult {
		public  enum Label {
			MET, PARTIAL, PARTIAL_1, FAILED
		}
		Label label = Label.MET;
		int numViolations = 0;
		private static SatisfactionResult FAILED_INSTANCE = new SatisfactionResult(Label.FAILED);
		
		public SatisfactionResult(SatisfactionResult.Label label) {
			this.label = label;
			if (label == Label.FAILED) {
				numViolations = 99;
			}
		}
		
		public void increaseViolation() {
			this.label = Label.PARTIAL;
			numViolations += 1;
		}
		
		public void increaseViolation(int count) {
			if (count >= 1) {
				this.label = Label.PARTIAL;
				numViolations += count;
			}
			
		}
		
		public int numViolations() {
			return numViolations;
		}
		
		
	}

	// usually run for checking callee spec.
	// For the caller spec, use the other isArgumentsSatisfied((Statement statement, Scope scope))
	public SatisfactionResult isArgumentsSatisfied(List<Object> arguments) {
		if (arguments == null) {
			// did not get arguments from concrete execution. it was not reached.
			return SatisfactionResult.FAILED_INSTANCE;
		}
		LoggingUtils.logWarnAtMostOnce(logger, "reached callee method");

		if (argStringInspectors.isEmpty()) {
			for (int j = 0; j < arguments.size(); j++) {
				argStringInspectors.add(new HashMap<>());
				argBoolInspectors.add(new HashMap<>());
				argStringValue.add(new HashSet<>());
				
				nestedStringInspectors.add(new HashMap<>());
			}

		}
		if (argStringInspectors.size() != argBoolInspectors.size()) {
			throw new RuntimeException("sizes differ");
		}
		if (argStringInspectors.size() != argStringValue.size()) {
			throw new RuntimeException("sizes differ");
		}
		boolean isCalleeStatic = ReachabilityCoverageFactory.targetCalledMethodIsStatic;
		if (!ReachabilityCoverageFactory.targetCalledMethodIsStatic) {
			// instance methods. Will have the `this` object
			// argStringInspectors are about the arguments, not `this`
			
			if (argStringInspectors.size() != arguments.size() - 1) {
				logger.warn("expected argStringInspectors.len == arguments.len - 1");
				logger.warn("\t\tinspectors len : " + argStringInspectors.size());
				logger.warn("\t\targuments len : " + arguments.size());
				return SatisfactionResult.FAILED_INSTANCE; // wrong function
			}
		} else {
			if (argStringInspectors.size() != arguments.size()) {
				logger.warn("expected argStringInspectors to have the same len as argument");
				logger.warn("\t\tinspectors len : " + argStringInspectors.size());
				logger.warn("\t\targuments len : " + arguments.size());
				return SatisfactionResult.FAILED_INSTANCE; // wrong function
			}
		}
		SatisfactionResult result = new SatisfactionResult(SatisfactionResult.Label.MET);
		
		LoggingUtils.logWarnAtMostOnce(logger, "reached isArgumentsSatisfied, at least");
		LoggingUtils.logWarnAtMostOnce(logger, "checking spec= " + ReachabilitySpecUnderInferenceUtils.serialize(this));

		for (int argNum = 0; argNum < argStringInspectors.size(); argNum++) { // OR semantics

			 // for non-static methods, arguments will contain the method receiver object too at index=0
			Object argument = arguments.get(isCalleeStatic ? argNum : argNum+1);
			if (argument == null) {
				// argument may be null, cannot call inspector
				// just fail all inspector checks
				logger.warn("===");
//				logger.warn("There are " +  arguments.size() + " arguments in total");
				logger.warn("argument num:" + argNum + " object is null");
				logger.warn("\tfail all inspector check as argument is null. increased by " + 
						(argStringInspectors.get(argNum).size() + argBoolInspectors.get(argNum).size()));
				logger.warn("previous # =" + result.numViolations);
				
				result.increaseViolation(argStringInspectors.get(argNum).size());
				result.increaseViolation(argBoolInspectors.get(argNum).size());
				logger.warn("now # =" + result.numViolations);
				logger.warn("/===");
				continue;
			}

			List<Inspector> inspectors = InspectorManager.getInstance().getInspectors(argument.getClass());
			

//			logger.warn("concrete values are: argnum=" + argNum + "/ " + (arguments.size() - 1) + "; value=" + argument
//					+ " #inspectors=" + sis.size() + " arg is of type=" + argument.getClass().getName());

			if (!this.argStringInspectors.get(argNum).isEmpty() || !this.argBoolInspectors.get(argNum).isEmpty()) {
				if (inspectors.isEmpty()) {
					// if sis is empty, `checkInspector` may not return even if the arguments are not valid
					logger.warn("===");
					logger.warn("There are " +  arguments.size() + " arguments in total");
					try {
						logger.warn("argument num:" + argNum + " object is " + argument + " type is " + argument.getClass());
					} catch (Exception e) {
						logger.warn("\tfailed to print argument for argNum:" + argNum);
					}
					logger.warn("\tincrease violation count as sis is empty, but spec indicates there should be inspectors. increased by " + (this.argStringInspectors.get(argNum).size() + this.argBoolInspectors.get(argNum).size()));
					logger.warn("previous # =" + result.numViolations);
					
					
					result.increaseViolation(this.argStringInspectors.get(argNum).size() + this.argBoolInspectors.get(argNum).size());
					logger.warn("now # =" + result.numViolations);
					logger.warn("/===");
				}
			}
			logger.warn("===");
			logger.warn("There are " +  arguments.size() + " arguments in total");
			try {
				logger.warn("argument num:" + argNum + " object is " + argument + " type is " + argument.getClass());
			} catch (Exception e) {
				logger.warn("\tfailed to print argument for argNum:" + argNum);
			}
			logger.warn("previous # =" + result.numViolations);
			result.increaseViolation(checkInspector(inspectors, argNum, argStringInspectors, argBoolInspectors, argument));
			logger.warn("now # =" + result.numViolations);
			logger.warn("/===");

		}

		// 2. Next, if string, directly compare to argStringValue
		for (int i = 0; i < argStringInspectors.size(); i++) {
			Object argObject = arguments.get(isCalleeStatic ? i : i+1);
			
			if (!argStringValue.get(i).isEmpty() && argObject == null) {
				result.increaseViolation();
				continue;
			}
			if (argObject instanceof String) {
				String argObjectString = (String) argObject;
				// TODO the semantics of argStringValue being null or empty should be thought
				// through later.
				if (!argStringValue.get(i).isEmpty() && !argStringValue.get(i).contains(argObjectString)) {
//					LoggingUtils.logWarnAtMostOnce(logger, "fail on argStringValue " + i + " values=" + argStringValue + " but value is " + argObjectString);
					
					logger.warn("fail on argStringValue " + i + " values=" + argStringValue);
					logger.warn("previous # =" + result.numViolations);
					logger.warn("/===");
					result.increaseViolation();
				} else {
					LoggingUtils.logWarnAtMostOnce(logger, "pass argStringValue at least once");
				}
			}
		}
		if (result.label == SatisfactionResult.Label.MET) {
			logger.warn("one true result of isArgumentsSatisfied. ");
			logger.warn("arguments were " + arguments);
		}
		return result;

	}


	// update spec:(argStringInspectors, argBooleanInspectors) to include the
	// execution (var, scope)
	private static void updateSpecOfInspector(List<SerializableInspector> inspectors, VariableReference var,
			Scope scope, int argNum, List<Map<SerializableInspector, Set<String>>> argStringInspectors,
			List<Map<SerializableInspector, Boolean>> argBooleanInspectors) {
		for (int j = 0; j < inspectors.size(); j++) {
			SerializableInspector si = inspectors.get(j);
			Inspector i = new Inspector(si.clazz, si.method);

			// No inspectors from java.lang.Object
			if (i.getMethod().getDeclaringClass().equals(Object.class))
				continue;

			try {
				Object target = var.getObject(scope);
				if (target != null) {

					// Don't call inspector methods on mock objects
					if (target.getClass().getCanonicalName().contains("EnhancerByMockito"))
						continue;

					Object value = i.getValue(target);
//					logger.warn("Inspector " + i.getMethodCall() + " is: " + value);

					// We need no assertions that include the memory location
					if (value instanceof String || value instanceof byte[] || value instanceof char[]) {
//						logger.warn("\t\t\tstring inspector spotted");
						// String literals may not be longer than 32767
						if (((String) value).length() >= 32767)
							continue;

						// Maximum length of strings we look at
						if (((String) value).length() > Properties.MAX_STRING)
							continue;

						// Avoid asserting anything on values referring to mockito proxy objects
						if (((String) value).toLowerCase().contains("EnhancerByMockito"))
							continue;
						if (((String) value).toLowerCase().contains("$MockitoMock$"))
							continue;

						if (!argStringInspectors.get(argNum).containsKey(si)) {
							argStringInspectors.get(argNum).put(si, new HashSet<>());
						}

//						logger.warn("\t\t\tstring inspector spotted. Before interesting check. method is " + i.getMethodCall() + " . value is " + value);
						logger.warn("try updating  arg string inspector :" + i.getMethodCall());
//						if (ReachabilitySpecUnderInferenceUtils.interestingStrings.contains((String) value)) {
//							logger.warn("updating  arg string inspector : " + i.getMethodCall() + " with value =" + value);
//
//							argStringInspectors.get(argNum).get(si).add((String) value);
//						}

					} else if (value instanceof Boolean) {

						argBooleanInspectors.get(argNum).put(si, (Boolean) value);
//						logger.warn("updating  arg boolean inspector :" + i.getMethodCall() + " val= " + value);

					}

				}
			} catch (Exception e) {
				if (e instanceof TimeoutException) {
					logger.debug("Timeout during inspector call - deactivating inspector " + i.getMethodCall());
					InspectorManager.getInstance().removeInspector(var.getVariableClass(), i);
				}
				logger.warn("Exception " + e + " / " + e.getCause());
				e.printStackTrace();
				logger.error("error", e);
				if (e.getCause() != null && !e.getCause().getClass().equals(NullPointerException.class)) {
					logger.debug("Exception during call to inspector: " + e + " - " + e.getCause());
				}
			}
		}
	}

	public static boolean checkInspector(List<Inspector> inspectors, VariableReference var, Scope scope,
			int argNum, List<Map<Inspector, Set<String>>> argStringInspectors,
			List<Map<Inspector, Boolean>> argBooleanInspectors) {

		Object target = null;
		try {
			target = var.getObject(scope);
		} catch (CodeUnderTestException e) {
			logger.warn("Exception " + e + " / " + e.getCause());
			logger.error("error", e);
		}

		debug = false;
		boolean result = checkInspector(inspectors, argNum, argStringInspectors, argBooleanInspectors, target) == 0;
		debug = true;
		return result;
	}

	private static int checkInspector(List<Inspector> inspectors, int argNum,
			List<Map<Inspector, Set<String>>> argStringInspectors,
			List<Map<Inspector, Boolean>> argBooleanInspectors, Object target) {
		int violationsForString = 0;
		boolean oneStringInspectorPass = false;
		int violationsForBoolean = 0;
		for (int j = 0; j < inspectors.size(); j++) {
			Inspector i = inspectors.get(j);
			 

			// No inspectors from java.lang.Object
			if (i.getMethod().getDeclaringClass().equals(Object.class))
				continue;

			try {

				if (target != null) {

					// Don't call inspector methods on mock objects
					if (target.getClass().getCanonicalName().contains("EnhancerByMockito"))
						continue;

					Object value = i.getValue(target);
					
					// We need no assertions that include the memory location
					if (value instanceof String) {
						// String literals may not be longer than 32767
						if (((String) value).length() >= 32767)
							continue;

						// Maximum length of strings we look at
						if (((String) value).length() > Properties.MAX_STRING)
							continue;

						// Avoid asserting anything on values referring to mockito proxy objects
						if (((String) value).toLowerCase().contains("EnhancerByMockito"))
							continue;
						if (((String) value).toLowerCase().contains("$MockitoMock$"))
							continue;


						logger.warn("\tInspector " + i.getMethodCall() + " of argnum=" + argNum + " is: " + value);

						if (argStringInspectors.get(argNum).get(i) != null
								&& !argStringInspectors.get(argNum).get(i).contains(value)) {
							
							logger.warn("\tfail on argStringInspectors " + argNum + " inspector=" + i + " values=" 
										+ argStringInspectors.get(argNum).get(i) 
										+ " but value is + " + value);

							violationsForString += 1;
							
						} else {
							logger.warn("\tdid not fail argStringInspectors:");
							logger.warn("\t has expected values for inspector? :" + (argStringInspectors.get(argNum).get(i) != null));
							logger.warn("\t expected values ? : " + (argStringInspectors.get(argNum).get(i)));
							logger.warn("\t concrete value: " + value);
							if (argStringInspectors.get(argNum).get(i) != null) {
								logger.warn("\tpass argStringInspectors");
								// reset
								violationsForString = 0; 
								oneStringInspectorPass = true;
								break;
							} 
//							else { 
//								logger.warn("\tfaile 1 argStringInspectors");
//								violationsForString += 1;
//							}
						}

					} else if (value instanceof Boolean) {

						logger.warn("\tInspector " + i.getMethodCall() + " of argnum=" + argNum + " is: " + value);

						if (argBooleanInspectors.get(argNum).get(i) != null
								&& !argBooleanInspectors.get(argNum).get(i).equals(value)) {
							logger.warn("\tfail on argBooleanInspectors " + argNum + " inspector=" + i + " values=" 
									+ argBooleanInspectors.get(argNum).get(i)
									+ " but value is = " + value);
							violationsForBoolean += 2;
						} else if (argBooleanInspectors.get(argNum).get(i) != null) {
							logger.warn("\tpass argBooleanInspectors");
						}

					} else if (value == null && argStringInspectors.get(argNum).get(i) != null) {
//						LoggingUtils.logWarnAtMostOnce(logger, "is null. make it fail");
						
						logger.warn("\tis null. make it fail (+1) for " + argStringInspectors.get(argNum).get(i));
						violationsForString += 1;
					}

				}
			} catch (Exception e) {

				logger.warn("Exception " + e + " / " + e.getCause());
				logger.error("error", e);
				if (e.getCause() != null && !e.getCause().getClass().equals(NullPointerException.class)) {
					logger.debug("Exception during call to inspector: " + e + " - " + e.getCause());
				}
			}
		}
		if (oneStringInspectorPass) {
			violationsForString = 0;
		}
		return violationsForString + violationsForBoolean;
	}

	

	private <T> void mergeInspectorMaps(Map<SerializableInspector, T> oneThis,
			Map<SerializableInspector, T> oneConcrete) {
//		Set<Inspector> sharedKeys = new HashSet<>(oneThis.keySet());
//		sharedKeys.retainAll(oneConcrete.keySet());

		for (SerializableInspector key : oneConcrete.keySet()) {
			T thisValue = oneThis.get(key);
			T concreteValue = oneConcrete.get(key);

			if (thisValue == null) {
				oneThis.put(key, concreteValue);
			} else if (!thisValue.equals(concreteValue)) { // conflicting values
				// all values permitted for this inspector
				oneThis.remove(key);
			}
		}
	}

	private void mergeInspectorSetMaps(Map<SerializableInspector, Set<String>> oneThis,
			Map<SerializableInspector, Set<String>> oneConcrete) {

		for (SerializableInspector key : oneConcrete.keySet()) {
			Set<String> thisValue = oneThis.get(key);
			Set<String> concreteValues = new HashSet<>(oneConcrete.get(key));

			// new values that reached
			if (thisValue != null) {
				concreteValues.removeAll(thisValue);
			}
			if (!concreteValues.isEmpty()) {
				logger.warn("has concrete value (before interesting strings filter)? random sample="
						+ concreteValues.iterator().next());
			}

			if (!concreteValues.isEmpty()) {
				logger.warn("add string to string set map. key = " + key + " concrete = " + concreteValues.toString());

				if (thisValue == null) {
					oneThis.put(key, new HashSet<>());
					thisValue = oneThis.get(key);
				}
				thisValue.addAll(concreteValues);
			} else {
				// maybe strings don't matter
//				logger.warn("dropping string constraint...");
//				oneThis.clear();
			}
		}
	}

	private void mergeStringSets(Set<String> oneThis, Set<String> oneConcrete) {
		Set<String> thisValue = oneThis;
		Set<String> concreteValues = new HashSet<>(oneConcrete);

		// new values that reached
		concreteValues.removeAll(thisValue);
//		concreteValues.retainAll(ReachabilitySpecUnderInferenceUtils.interestingStrings);

		if (!concreteValues.isEmpty()) {
//			logger.warn("add string to string set = " + concreteValues.toString());
			thisValue.addAll(concreteValues);
		} else {
			// maybe strings don't matter
//			logger.warn("dropping string constraint...");
//			oneThis.clear();
		}

	}


	@Override
	public String toString() {
		// temporary, for debugging
		// TODO remove
		return ReachabilitySpecUnderInferenceUtils.serialize(this);
	}
	
	
	public static void printTimeOfFirstMerge() {
		
		
	}

}
