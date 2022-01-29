package org.evosuite.coverage.line;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.ga.archive.Archive;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;

// copied from LineCoverageSuitFitness, but different in ReachabilityCoverageFactory().getCoverageGoals();
// probably, many things here can be removed
public class ReachabilityCoverageSuiteFitness extends TestSuiteFitnessFunction {
	
	private static final long serialVersionUID = 1123322325654L;
	
	Set<TestFitnessFunction> goals = new HashSet<>();
	
	public ReachabilityCoverageSuiteFitness() {
		
		goals = new HashSet<>(new ReachabilityCoverageFactory().getCoverageGoals());
		
		for (TestFitnessFunction goal : goals) {
			if(Properties.TEST_ARCHIVE)
				Archive.getArchiveInstance().addTarget(goal);
		}
		
	}
	
	
	/**
	 * {@inheritDoc}
	 *
	 * Execute all tests and count covered branches
	 */
	@Override
	public double getFitness(TestSuiteChromosome suite) {
		double fitness = 0.0;

		List<ExecutionResult> results = runTestSuite(suite);
		int coveredGoals = 0;
		
		for(TestFitnessFunction goal : this.goals) {
	        for(ExecutionResult result : results) {
	            if(goal.isCovered(result)) {
	            	coveredGoals += 1;
	                break;
	            }
	        }
	    }
		
		
		updateIndividual(suite, fitness);

		suite.setNumOfCoveredGoals(this, coveredGoals);
		
		assert (fitness >= 0.0);

		assert (suite.getCoverage(this) <= 1.0) && (suite.getCoverage(this) >= 0.0) : "Wrong coverage value "
		        + suite.getCoverage(this);

		return fitness;
	}


	@Override
	public boolean updateCoveredGoals() {
		if (!Properties.TEST_ARCHIVE) {
			return false;
		}
		
		return true;

	}
}