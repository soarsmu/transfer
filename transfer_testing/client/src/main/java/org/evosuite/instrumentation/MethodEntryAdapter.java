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
package org.evosuite.instrumentation;

import java.lang.reflect.Method;

import org.evosuite.PackageInfo;
import org.evosuite.coverage.line.ReachabilityCoverageFactory;
import org.evosuite.coverage.line.ReachabilitySpecUnderInferenceUtils;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.evosuite.utils.LoggingUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrument classes to keep track of method entry and exit
 *
 * @author Gordon Fraser
 */
public class MethodEntryAdapter extends AdviceAdapter {

	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(MethodEntryAdapter.class);

	String className;
	String methodName;
	String fullMethodName;
	int access;

	String desc;
	public LocalVariablesSorter lvs;

	/**
	 * <p>
	 * Constructor for MethodEntryAdapter.
	 * </p>
	 *
	 * @param mv         a {@link org.objectweb.asm.MethodVisitor} object.
	 * @param access     a int.
	 * @param className  a {@link java.lang.String} object.
	 * @param methodName a {@link java.lang.String} object.
	 * @param desc       a {@link java.lang.String} object.
	 * @param lvs
	 */
	public MethodEntryAdapter(MethodVisitor mv, int access, String className, String methodName, String desc) {
		super(Opcodes.ASM9, mv, access, methodName, desc);
		this.className = className;
		this.methodName = methodName;
		this.desc = desc;
		this.fullMethodName = methodName + desc;
		this.access = access;
		
//		logger.warn("adapting method " + className + " : " + methodName);

	}

	/** {@inheritDoc} */
	@Override
	public void onMethodEnter() {

		if (methodName.equals("<clinit>"))
			return; // FIXXME: Should we call super.onMethodEnter() here?

		if (ReachabilityCoverageFactory.targetCalleeMethod == null ) {
			
			// try to init again. maybe we forgot to call init.
			ReachabilitySpecUnderInferenceUtils.init();
			
			
		}
		
		if (
				ReachabilityCoverageFactory.targetCalleeMethod == null 
				|| !ReachabilityCoverageFactory.targetCalleeMethod.contains(ReachabilityCoverageFactory.descriptorToActualName(fullMethodName))
						|| !ReachabilityCoverageFactory.targetCalleeClazzAsNormalName.equals(className) 
				) {
			mv.visitLdcInsn(className);
			mv.visitLdcInsn(fullMethodName);
			if ((access & Opcodes.ACC_STATIC) > 0) {
				mv.visitInsn(Opcodes.ACONST_NULL);
			} else {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
			}
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, PackageInfo.getNameWithSlash(ExecutionTracer.class), "enteredMethod",
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V", false);
			

		}
		else {
			
			mv.visitLdcInsn(className);
			mv.visitLdcInsn(fullMethodName);
			if ((access & Opcodes.ACC_STATIC) > 0) {
				mv.visitInsn(Opcodes.ACONST_NULL);
			} else {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
			}
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, PackageInfo.getNameWithSlash(ExecutionTracer.class), "enteredMethod",
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V", false);
			
			
			logger.warn("matched. targetMethod = " + ReachabilityCoverageFactory.targetCalleeMethod);
			logger.warn("matched. current class name = " + className);
			logger.warn("matched. current method name = " + methodName);
			
			logger.warn("matched. is static? ="  + ((access & Opcodes.ACC_STATIC) != 0));
			
			logger.warn("add call to `enteredMethodWithArgument` in methodName=" + fullMethodName);
			
			// TRANSFER: record arguments?
			Type[] arg = Type.getArgumentTypes(this.desc);

			int newVarId = this.newLocal(Type.getType("[Ljava/lang/Object;"));
			
			int numberOfItemsToLoad = arg.length;
			if ((access & Opcodes.ACC_STATIC) > 0) {
				
				ReachabilityCoverageFactory.targetCalledMethodIsStatic = true;
				
			} else {
			
				ReachabilityCoverageFactory.targetCalledMethodIsStatic = false;
				numberOfItemsToLoad += 1;
			
			}

			mv.visitIntInsn(Opcodes.BIPUSH, numberOfItemsToLoad);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, Object.class.getName().replaceAll("\\.", "/"));

			mv.visitVarInsn(Opcodes.ASTORE, newVarId);
		
			
			for (int i = 0; i < numberOfItemsToLoad; i++) {
				mv.visitVarInsn(Opcodes.ALOAD, newVarId);
				mv.visitIntInsn(Opcodes.BIPUSH, i);
				mv.visitVarInsn(Opcodes.ALOAD, i);

				mv.visitInsn(Opcodes.AASTORE);
			}

			mv.visitVarInsn(Opcodes.ALOAD, newVarId);
//			
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, PackageInfo.getNameWithSlash(ExecutionTracer.class),
					"enteredMethodWithArgument", "([Ljava/lang/Object;)V", false);
			

//			if (!this.desc.endsWith("V")) {
//				// assuming object return
//				mv.visitInsn(Opcodes.ACONST_NULL);
//				mv.visitInsn(Opcodes.ARETURN);
//			}
//			
		}
		super.onMethodEnter();
	}

	public int countChar(String str, char c)
	{
		
	    int count = 0;

	    for(int i=0; i < str.length(); i++){    
	    	if(str.charAt(i) == c)
	            count++;
	    }

//	    logger.warn("countChar: " + str + " count=" + count);
	    return count;
	}
	
	int[] storeArguments(Type[] arg, int opcode, String name) {
		int nArg = arg.length;
		boolean withThis = opcode != Opcodes.INVOKESTATIC && !name.equals("<init>");
		if (withThis)
			nArg++;
		int[] vars = new int[nArg];

		int varsInt = Type.getArgumentsAndReturnSizes(desc) >> 2;
		int firstUnusedVar = varsInt;

		int slot = firstUnusedVar;
		for (int varIx = nArg - 1, argIx = arg.length - 1; argIx >= 0; varIx--, argIx--) {
			Type t = arg[argIx];
			mv.visitVarInsn(t.getOpcode(Opcodes.ISTORE), vars[varIx] = slot);
			slot += t.getSize();
		}
		if (withThis)
			mv.visitVarInsn(Opcodes.ASTORE, vars[0] = slot);
		return vars;
	}

	// https://stackoverflow.com/questions/62816194/how-to-record-all-parameters-of-any-invoked-java-method-dynamically-using-java-a
	// answer by Holger
	String getReportDescriptor(String descriptor, Type[] arg, int[] vars) {
		StringBuilder sb = new StringBuilder(descriptor.length() + 2);
		sb.append("(Ljava/lang/String;");
		if (arg.length != vars.length) {
//	        if(owner.charAt(0) == '[') sb.append(owner);
//	        else sb.append('L').append(owner).append(';');
		}
		sb.append(descriptor, 1, descriptor.lastIndexOf(')') + 1);
		return sb.append('V').toString();
	}

	/** {@inheritDoc} */
	@Override
	public void onMethodExit(int opcode) {
		// TODO: Check for <clinit>
//		if (methodName.equals("<clinit>"))
//			return; 

		if (opcode != Opcodes.ATHROW) {

			mv.visitLdcInsn(className);
			mv.visitLdcInsn(fullMethodName);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					PackageInfo.getNameWithSlash(org.evosuite.testcase.execution.ExecutionTracer.class), "leftMethod",
					"(Ljava/lang/String;Ljava/lang/String;)V", false);
		} else {
 
			mv.visitLdcInsn(className);
			mv.visitLdcInsn(fullMethodName);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					PackageInfo.getNameWithSlash(org.evosuite.testcase.execution.ExecutionTracer.class), "leftMethodByException",
					"(Ljava/lang/String;Ljava/lang/String;)V", false);
		}
		super.onMethodExit(opcode);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.objectweb.asm.commons.LocalVariablesSorter#visitMaxs(int, int)
	 */
	/** {@inheritDoc} */
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		int maxNum = 3;
		super.visitMaxs(Math.max(maxNum, maxStack), maxLocals);
	}
	
	
}
