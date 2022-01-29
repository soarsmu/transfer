/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.testsuite;

import org.evosuite.Properties;
import org.evosuite.coverage.TestFitnessFactory;
import org.evosuite.coverage.line.LineCoverageFactory;
import org.evosuite.coverage.line.ReachabilityCoverageFactory;
import org.evosuite.coverage.line.ReachabilitySpecUnderInferenceUtils;
import org.evosuite.runtime.util.AtMostOnceLogger;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Historical concrete TestFitnessFactories only implement the getGoals() method
 * of TestFitnessFactory. Those old Factories can just extend these
 * AstractFitnessFactory to support the new method getFitness()
 * 
 * @author Sebastian Steenbuck
 */
public abstract class AbstractFitnessFactory<T extends TestFitnessFunction> implements
        TestFitnessFactory<T> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractFitnessFactory.class);
	
	/**
	 * A concrete factory can store the time consumed to initially compute all
	 * coverage goals in this field in order to track this information in
	 * SearchStatistics.
	 */
	public static long goalComputationTime = 0L;

	
	protected boolean isCUT(String className) {
		
		boolean result = Properties.TARGET_CLASS.equals("")
				|| (className.equals(Properties.TARGET_CLASS)
				|| className.startsWith(Properties.TARGET_CLASS + "$"))
				// TRANSFER: or is the vuln lib class
				|| ReachabilityCoverageFactory.targetCalleeClazzAsNormalName.equals(className)
				// TRANSFER: or is along call graph, or manually indicated by the human user
				|| ReachabilityCoverageFactory.additionalClasses.contains(className)
				;
//		AtMostOnceLogger.warn(logger, "checking isCUT: " + className + "\tresult = " + result);
		return result;
	}
	
	/** {@inheritDoc} */
	@Override
	public double getFitness(TestSuiteChromosome suite) {

		ExecutionTracer.enableTraceCalls();

		int coveredGoals = 0;
		for (T goal : getCoverageGoals()) {
			for (TestChromosome test : suite.getTestChromosomes()) {
				if (goal.isCovered(test)) {
					coveredGoals++;
					break;
				}
			}
		}

		ExecutionTracer.disableTraceCalls();

		return getCoverageGoals().size() - coveredGoals;
	}
}
