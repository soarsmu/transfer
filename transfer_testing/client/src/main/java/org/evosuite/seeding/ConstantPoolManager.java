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

package org.evosuite.seeding;

import org.evosuite.Properties;
import org.evosuite.ga.metaheuristics.mosa.structural.BranchFitnessGraph;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gordon Fraser
 * 
 */
public class ConstantPoolManager {

	private static final Logger logger = LoggerFactory.getLogger(ConstantPoolManager.class);
	
	private static ConstantPoolManager instance = new ConstantPoolManager();

	private ConstantPool[] pools;
	private double[] probabilities;

	/*
	 * We treat it in a special way, for now, just for making experiments
	 * easier to run
	 */
	private static final int DYNAMIC_POOL_INDEX = 2;

	private ConstantPoolManager() {
		init();
	}

	private void init() {
		if(!Properties.VARIABLE_POOL) {
			pools = new ConstantPool[]{new StaticConstantPool(), new StaticConstantPool(),
					new DynamicConstantPool()};
		} else {
			pools = new ConstantPool[]{new StaticConstantVariableProbabilityPool(), new StaticConstantVariableProbabilityPool(),
					new DynamicConstantVariableProbabilityPool()};
		}

		initDefaultProbabilities();
	}

	private void initDefaultProbabilities() {
		probabilities = new double[pools.length];
//		double p = 1d / probabilities.length;
//		double p = (1d - Properties.DYNAMIC_POOL) / (probabilities.length - 1);
		// TRANSFER notes: TRANSFER requires instrumentation of many things. this causes the non-sut pool to be extremely crowded, as compared to EvoSuite.
		// TRANSFER notes: we grant the SUT pool 2 times the chance.
		double p = (1d - Properties.DYNAMIC_POOL) / (probabilities.length);
		for (int i = 0; i < probabilities.length; i++) {
			probabilities[i] = i == 0 ? 2 * p : p;
		}
		probabilities[DYNAMIC_POOL_INDEX] = Properties.DYNAMIC_POOL;
		normalizeProbabilities();
	}

	private void normalizeProbabilities() {
		double sum = 0d;
		for (double p : probabilities) {
			sum += p;
		}
		double delta = 1d / sum;
		for (int i = 0; i < probabilities.length; i++) {
			probabilities[i] = probabilities[i] * delta;
		}
	}

	public static ConstantPoolManager getInstance() {
		return instance;
	}

	/*
	 * Note: the indexes are hard coded for now. We do it because maybe
	 * in the future we might want to extend this class, so still we need to
	 * use arrays 
	 */

	public void addSUTConstant(Object value) {
		if (value instanceof String) {
			if (((String)value).contains("quine")) {
				logger.warn("adding string containing `quine` to SUT constant pool");
			}
//			try {
//				throw new RuntimeException("adding new constant=" + value);
//			} catch (Exception e) {
//				logger.warn("new constant " + value, e);
//			}
		}
		
		pools[0].add(value);
	}

	public void addNonSUTConstant(Object value) {
		if (value instanceof String) {
			if (((String)value).contains("quine")) {
				logger.warn("adding string containing `quine` to non SUT constant pool");
			}
		}
		
		if (Randomness.nextDouble() < 0.001) { // TRANSFER: prevent non-SUT constants from dominating the random string scene. We inspect a lot more files so there are MANY constants
			pools[1].add(value);
		}
	}

	public void addDynamicConstant(Object value) {
		pools[DYNAMIC_POOL_INDEX].add(value);
	}

	public ConstantPool getConstantPool() {
		double p = Randomness.nextDouble();
		double k = 0d;
		for (int i = 0; i < probabilities.length; i++) {
			k += probabilities[i];
			if (p < k) {
				return pools[i];
				
			}
		}
		/*
		 * This should not happen, but you never know with double computations...
		 */
		return pools[0];
	}
	
	public ConstantPool getDynamicConstantPool() {
		return pools[DYNAMIC_POOL_INDEX];
	}

	public void reset() {
		init();
	}
}
