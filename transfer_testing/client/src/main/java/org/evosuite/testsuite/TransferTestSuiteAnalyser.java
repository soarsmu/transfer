package org.evosuite.testsuite;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.coverage.TestFitnessFactory;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.line.LineCoverageTestFitness;
import org.evosuite.coverage.line.ReachabilityCoverageFactory;
import org.evosuite.coverage.method.MethodCoverageTestFitness;
import org.evosuite.coverage.method.MethodNoExceptionCoverageTestFitness;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.junit.CoverageAnalysis;
import org.evosuite.runtime.util.AtMostOnceLogger;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferTestSuiteAnalyser {

	protected static final Logger logger = LoggerFactory.getLogger(TransferTestSuiteAnalyser.class);
	
	public static Set<TestFitnessFunction> goalsOfJunit = new HashSet<>(); 
	
//	public static String vulnLibClass;
//	public static String clientLibClass;
//
//	public static void init() {
//		try {
//			Files.lines(new File("calleeMethod.evosuite_target").toPath()).forEach(line -> {
//				String clazz = line.split(":")[0];
//				vulnLibClass = clazz;
//			});
//			Files.lines(new File("callerMethod.evosuite_target").toPath()).forEach(line -> {
//				String clazz = line.split(":")[0];
//				clientLibClass = clazz;
//			});
//			logger.warn("initialized vulnLibClass: " + vulnLibClass + "\tclientLibClass:" + clientLibClass);
//		} catch (IOException e) {
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}
//
//	}
//	
	

	/**
	 * goalsInVulnLib are all the coverage goals in vulnLibClass
	 * 
	 * @param goalsInVulnLib
	 * @return
	 */
	public  static Set<TestFitnessFunction> getJUnitCoveredGoals(List<TestFitnessFactory<? extends TestFitnessFunction>> factories) {
		if (Properties.JUNIT.isEmpty())
			return new HashSet<>();

		if (!goalsOfJunit.isEmpty()) {
			return goalsOfJunit; // already known
		}
		AtMostOnceLogger.warn(logger, "Getting junit covered goals");
		List<TestFitnessFunction> goalsInVulnLib = new ArrayList<>();
		for (TestFitnessFactory<?> ff : factories) {
        	goalsInVulnLib.addAll(ff.getCoverageGoals());
        }
		
		LoggingUtils.getEvoLogger().info("* Determining coverage of existing tests");
		String[] testClasses = Properties.JUNIT.split(":");
		LoggingUtils.getEvoLogger().info("* Tests are " + Properties.JUNIT);
		Set<TestFitnessFunction> allGoalsCovered = new HashSet<>();
		for (String testClass : testClasses) {
			if (testClass == null) {
				continue;
			}
			try {
				LoggingUtils.getEvoLogger().info("* 1. " + testClass);
				LoggingUtils.getEvoLogger().info("* 2. " + TestGenerationContext.getInstance().getClassLoaderForSUT());
				Class<?> junitClass = Class.forName(testClass, true,
						TestGenerationContext.getInstance().getClassLoaderForSUT());
				
				// disable archive since the provided junit tests are not part of the final test suite
				// we use the provided tests to guide our choice of coverage goals.
				boolean archive = Properties.TEST_ARCHIVE;
				Properties.TEST_ARCHIVE = false;
				
				Set<TestFitnessFunction> coveredGoals = CoverageAnalysis.getCoveredGoals(junitClass, goalsInVulnLib);
				LoggingUtils.getEvoLogger().info("* Finding " + coveredGoals.size() + " goals covered by JUnit (total: "
						+ goalsInVulnLib.size() + ")");

				allGoalsCovered.addAll(coveredGoals);
				
				Properties.TEST_ARCHIVE = archive;
			} catch (ClassNotFoundException e) {
				LoggingUtils.getEvoLogger().warn("* Failed to find JUnit test suite: " + Properties.JUNIT);

			}
		}
		goalsOfJunit = allGoalsCovered;
		
		try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File("vuln_coverage_goals.log")))) {
			for (TestFitnessFunction goal : goalsOfJunit) {
				writer.append(goal.toString());
				writer.append("\n");
			}
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
		
		// update functionsCovered
		for (TestFitnessFunction goal : goalsOfJunit) {
			logger.warn("goal type = " + goal.getClass());
			if (goal instanceof LineCoverageTestFitness) {
				LineCoverageTestFitness lineGoal = (LineCoverageTestFitness) goal;
				ReachabilityCoverageFactory.functionsCovered.add(lineGoal.getTargetMethod());
				logger.warn("added to functionsCovered. Method " + lineGoal.getTargetMethod());
			}
			if (goal instanceof BranchCoverageTestFitness) {
				BranchCoverageTestFitness branchGoal = (BranchCoverageTestFitness) goal;
				ReachabilityCoverageFactory.functionsCovered.add(branchGoal.getTargetMethod());
				logger.warn("added to functionsCovered. Method " + branchGoal.getTargetMethod());
			}
		}
		
		if (ReachabilityCoverageFactory.functionsCovered.isEmpty()) {
			throw new RuntimeException("functionsCovered shouldn't be empty, but it is empty right now.");
		}
		
		if (!new File("functions_covered.log").exists()) {
			logger.warn("writing to functions_covered.log");
			try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File("functions_covered.log")))) {
				for (String func : ReachabilityCoverageFactory.functionsCovered) {
					writer.append(func);
					writer.append("\n");
				}
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}
			logger.warn("wrote to functions_covered.log");
			throw new RuntimeException("quitting. Wrote  to functions_covered.log. Run this again.");
		}
		
		return allGoalsCovered;
	}
}
