package org.evosuite.coverage.io.input;

import org.evosuite.coverage.line.ReachabilitySpecUnderInferenceUtils;
import org.evosuite.coverage.line.ReachingSpec;
import org.evosuite.instrumentation.LinePool;
import org.evosuite.testcase.execution.CodeUnderTestException;
import org.evosuite.testcase.execution.ExecutionObserver;
import org.evosuite.testcase.execution.ExecutionResult;
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

import java.util.*;

/**
 * Probably useless now. Knowing the arguments to the caller method is useless for us.
 */
public class HJInputObserver extends ExecutionObserver {

	// set as deprecated for easy spotting of errors. I still kinda want this around
	// for debugging
	@Deprecated
	private int lineNumber;
	private int lineNumberInTest; // a statement gives us only its relative position in the method/test, not the
									// file

	public String targetCallerMethod;

	private ReachingSpec currentConcreteExecution;
	
	private ReachingSpec callerConcreteExecution;
	private ReachingSpec calleeConcreteExecution;
	
	private boolean callerSpecificationSatisfied;
	private boolean calleeSpecificationSatisfied;


	private static final Logger logger = LoggerFactory.getLogger(HJInputObserver.class);

	public HJInputObserver(String methodName) {
//		this.lineNumber = lineNumber;
//
//		this.lineNumberInTest = this.lineNumber - Collections.min(LinePool.getLines(className, methodName));
//
//		this.targetCallerMethod = targetCallerMethod;
		this.targetCallerMethod = methodName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.evosuite.testcase.ExecutionObserver#output(int, java.lang.String)
	 */
	@Override
	public void output(int position, String output) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.evosuite.testcase.ExecutionObserver#beforeStatement(org.evosuite.testcase
	 * .StatementInterface, org.evosuite.testcase.Scope)
	 */
	@Override
	public void beforeStatement(Statement statement, Scope scope) {
		// do nothing
		// reset concreteExecution
		currentConcreteExecution = new ReachingSpec();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.evosuite.testcase.ExecutionObserver#afterStatement(org.evosuite.testcase.
	 * StatementInterface, org.evosuite.testcase.Scope, java.lang.Throwable)
	 */
	@Override
	public void afterStatement(Statement statement, Scope scope, Throwable exception) {

		
		if (statement instanceof EntityWithParametersStatement) {
			EntityWithParametersStatement parameterisedStatement = (EntityWithParametersStatement) statement;

			// `caller` refers to the (caller, callee) pair during the incremental/modular
			// testing process
			boolean isCallToTargetCallerMethod = false; // odd name, but whatever.

			// match caller method
			if (statement instanceof MethodStatement) { // TODO ctor
				MethodStatement methodStmt = (MethodStatement) statement;
				
				String methodStmtFull = methodStmt.getMethodName() + methodStmt.getMethod().getDescriptor();
//				if (methodStmtFull.equals(targetCallerMethod)) {
				if (methodStmtFull.contains(targetCallerMethod)) {
					isCallToTargetCallerMethod = true;
					
					logger.warn("matched call to " + targetCallerMethod + " : ");
					
					
					List<VariableReference> parRefs = parameterisedStatement.getParameterReferences();
					
					List<Object> realObjs = getRealObjectsOfArguments(scope, parRefs);
					for (Object realObj : realObjs){ 
						logger.warn("\treal obj= " + realObj);
					}
					
					
				}

			}

			if (!isCallToTargetCallerMethod) {
				// lineNumerInTest: for callee spec
				return;
			}
//			logger.warn("==match==");
			// match 
			

//			ReachingSpec specificationToCheck = null;
//			if (isCallToTargetCallerMethod) {
////				logger.warn("detected call to target caller method. targetCallerMethod=" + targetCallerMethod);
////				currentConcreteExecution.updateWithConcreteExecution(statement, scope);
//				specificationToCheck = ReachabilitySpecUnderInferenceUtils.specUnderInference;
//				this.callerConcreteExecution = currentConcreteExecution;
//			} 

//			logger.warn("==end match==");
			
//			boolean argumentsSatisfied = specificationToCheck == null || specificationToCheck.isArgumentsSatisfied(statement, scope);
			
			// TODO `receiver satisfied?` should be considered as part of specificationSatisfied
			if (isCallToTargetCallerMethod) {
//				this.callerSpecificationSatisfied = argumentsSatisfied;
//				if (this.callerSpecificationSatisfied) {
////					logger.warn("callerSpecificationSatisfied is true ");
////					if ( specificationToCheck == null ) {
////						logger.warn("\tnote that specification to check is null" );
////					}
//				}
			} 
			
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.evosuite.testcase.ExecutionObserver#testExecutionFinished(org.evosuite.
	 * testcase.ExecutionResult)
	 */
	@Override
	public void testExecutionFinished(ExecutionResult r, Scope s) {
//		r.setSpecificationSatisfied(this.callerSpecificationSatisfied, true);
		
//		logger.warn("test execution finished");
//		if (callerConcreteExecution != null) {
//			logger.warn("test execution finished. setting result concrete execution. is null? " + (callerConcreteExecution == null));	
//		}
		
//		r.setConcreteExecution(callerConcreteExecution, true);
//		
//		r.setConcreteExecution(calleeConcreteExecution, false);
		

		
	}
	
	private List<Object> getRealObjectsOfArguments(Scope scope, List<VariableReference> parRefs) {
		List<Object> argObjects = new ArrayList<>(parRefs.size());
		for (VariableReference parRef : parRefs) {
			// get real objects for the non-inspector checks
			Object parObject = null;
			try {
				if (parRef instanceof ArrayIndex || parRef instanceof FieldReference) {
					parObject = parRef.getObject(scope);
				} else if (parRef instanceof ConstantValue) {
					parObject = ((ConstantValue) parRef).getValue();
				} else {
					parObject = parRef.getObject(scope);
				}
			} catch (CodeUnderTestException e) {
				e.printStackTrace();
				logger.error("error", e);
			}
			argObjects.add(parObject);
		}
		return argObjects;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.evosuite.testcase.ExecutionObserver#clear()
	 */
	@Override
	public void clear() {
		logger.info("Clearing InputObserver data");
//        inputCoverage = new LinkedHashMap<>();
		currentConcreteExecution = new ReachingSpec();
		callerConcreteExecution = null;
		calleeConcreteExecution = null;
		
		this.callerSpecificationSatisfied = false;
		this.calleeSpecificationSatisfied = false;
	}

}
