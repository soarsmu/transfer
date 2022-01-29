package org.evosuite.coverage.line;

import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.evosuite.Properties;
import org.evosuite.assertion.Inspector;
import org.evosuite.assertion.InspectorManager;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.io.input.HJInputObserver;
import org.evosuite.coverage.line.ReachingSpec.SatisfactionResult;
import org.evosuite.ga.archive.Archive;
import org.evosuite.instrumentation.testability.StringHelper;
import org.evosuite.runtime.util.AtMostOnceLogger;
import org.evosuite.seeding.ConstantPoolManager;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionObserver;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.EntityWithParametersStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.utils.LoggingUtils;

/**
 * 
 * This fitness function has 2 states: 1. recording the objects during the vuln
 * junit test run. 2. comparing the objects during test generation against the
 * objects in (1)
 * 
 * To determine which state, check ReachingSpec.java
 * 
 * @author kanghongjin
 *
 */
public class ReachabilityCoverageTestFitness extends TestFitnessFunction {

	private static final long serialVersionUID = 1344444L;

	public String nameOfTest;
	
	public static double bestFitnessSoFar = 1.0;

	public ReachabilityCoverageTestFitness(String name) {
		this.nameOfTest = name;

	}

	@Override
	public double getFitness(TestChromosome individual, ExecutionResult result) {

		double fitness = 1.0;
		

		if (!ReachabilityCoverageFactory.isRecording) {
		
			AtMostOnceLogger.warn(logger, "comparing recordedException");

			if (ReachabilityCoverageFactory.recordedException != null) {
				for ( Throwable exception : result.getAllThrownExceptions()) {
				
					Throwable recorded = ReachabilityCoverageFactory.recordedException;
					logger.warn("comparing exceptions : expected=" + recorded.getMessage() + "." + recorded.getClass() + "  actual=" + exception.getMessage() + "." + exception.getClass());
					if (recorded.getMessage().equals(exception.getMessage()) && recorded.getClass().equals(exception.getClass())) {
						ReachabilityCoverageFactory.matchedOutput = true;
						logger.warn("matched output, by comparing exceptions");
					}
				}
			} else {
				// compare outputs
			}
		}

		
		if (ReachabilityCoverageFactory.isRecording) {
		
			ReachingSpec recordedSpec = result.getTrace().getSpecOfArgumentsToTargetFunction(); // recorded spec
	
			logger.warn("update: " + recordedSpec);
	
			ReachabilityCoverageFactory.reachingSpec.put(nameOfTest, recordedSpec);
	
			// for computing final fitness, need to determin the weight of the output/exception to the fitness.
			if (recordedSpec != null) {
				ReachabilityCoverageFactory.outputWeight = 1 / (1 + (recordedSpec.argBoolInspectors != null ? recordedSpec.argBoolInspectors.size() : 0));
			} else {
				ReachabilityCoverageFactory.outputWeight = 0;
			}
			
			// for updating, fitness = 0, it is a covered goal
			fitness = 0.0;
		} else {
			
			if (hasCalleeMethodAsTestStatement(result)) {
//				logger.warn("has callee method"); // should not be called since we removed calleemethod from the possible testMethods to invoke.
				fitness = 1.0;
			} else {
	
				
				double realArgsFitness = 1.0;
	
	
	//				logger.warn("here we go!");
				// compute fitness if in state (2)
				// fitness is based on similarity to the existing objects
	
				double similarity = result.getTrace().similarityOfActualToSpec();
				if (result.getTrace().getArgumentsPassedToTargetFunction().size() > 0) {
					logger.warn("reached with non-null argument : " +result.getTrace().getArgumentsPassedToTargetFunction().get(0) );
					if (similarity <= 0) {
						similarity = 0.01;
					}
				}
	
				if (similarity < 0 || similarity > 1.0) {
					throw new RuntimeException("invalid object similarity value of " + similarity);
				}
				
	
				realArgsFitness = 1.0 - similarity;
				if (realArgsFitness < 1.0) {
					logger.warn("it is known. similarity = " + similarity);
	//					logger.warn("it is known. Comparing to " + specUnderAnalysis + " gives us a fitness of "
	//							+ realArgsFitness);
	//					logger.warn("args were = " + result.getTrace().getArgumentsPassedToTargetFunction());
				}
		
	
				fitness = realArgsFitness;
				
				// compare outputs
				if (!ReachabilityCoverageFactory.matchedOutput) {
					fitness += ReachabilityCoverageFactory.outputWeight; 
					logger.warn("penalize fitness because output did not match. final fitness=" + fitness);
				}
			}
			
			ReachabilitySpecUnderInferenceUtils.iterationFitness = Math.min(
					ReachabilitySpecUnderInferenceUtils.iterationFitness, fitness);
			
		}
		
		

		updateIndividual(individual, fitness);

		logger.warn("completed fitness evaluation.");
		if (fitness == 0.0 && !ReachabilityCoverageFactory.isRecording && ReachabilityCoverageFactory.matchedOutput) {
			logger.warn("adding covered reachability goal : ");
			individual.getTestCase().addCoveredGoal(this);
			ReachabilitySpecUnderInferenceUtils.writeTestToOutputFile(individual.toString());
			
		}

		if (Properties.TEST_ARCHIVE) {
			if (fitness < 0.01) {
				logger.warn("updating archive with fitness = " + fitness);
				logger.warn("test = \n" + individual);
				logger.warn("=====END TEST=====");
			} else {
				if (fitness < bestFitnessSoFar) {
					logger.warn("best fitness so far with test \n" + individual + "\n fitness:" + fitness);
					ReachabilitySpecUnderInferenceUtils.writeTestToOutputFile(individual.toString());
				}
			}
			bestFitnessSoFar = Math.min(bestFitnessSoFar, fitness);
			Archive.getArchiveInstance().updateArchive(this, individual, fitness);
		}

//		if (ReachabilityCoverageFactory.isRecording) {
//			logger.warn("when recording, fitness is " + fitness);
//		}
		return fitness;
	}

	@Override
	public int compareTo(TestFitnessFunction other) {
		if (other instanceof ReachabilityCoverageTestFitness) {
			return this.nameOfTest.compareTo(((ReachabilityCoverageTestFitness) other).nameOfTest);
		} else {
			return -1;
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((nameOfTest == null) ? 0 : nameOfTest.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReachabilityCoverageTestFitness other = (ReachabilityCoverageTestFitness) obj;
		if (nameOfTest == null) {
			if (other.nameOfTest != null)
				return false;
		} else if (!nameOfTest.equals(other.nameOfTest))
			return false;
		return true;
	}

	@Override
	public String getTargetClass() {
		return ReachabilityCoverageFactory.targetCalleeClazzAsNormalName;
	}

	@Override
	public String getTargetMethod() {
		return ReachabilityCoverageFactory.targetCalleeMethod;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "[Reachability] " + ReachabilityCoverageFactory.reachingSpec.get(nameOfTest);
	}

}
