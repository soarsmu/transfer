package org.evosuite.instrumentation;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.evosuite.instrumentation.ReplaceClassVisitor.MatchedInvocation;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ReplaceMethodVisitor extends MethodVisitor {

	private static final Logger logger = LoggerFactory.getLogger(ReplaceMethodVisitor.class);
	
	String parentOwner;
	String parentName;

	String targetOwner;
	String targetName;
	String replacementOwner;
	String replacementName;
	
	
	// intermediate store
	List<Integer> storeOpcode = new ArrayList<>();
	List<Integer> storeVar = new ArrayList<>();
	
	// to pass information upwards to the class visitor (see ReplaceClassVisitor)
	MatchedInvocation matchedInvocation;

	public ReplaceMethodVisitor(int api, MethodVisitor methodVisitor, String parentOwner,
			String targetOwner, String targetName, String replacementOwner, String replacementName, MatchedInvocation matchedInvocation) {
		super(api, methodVisitor);
		this.parentOwner = parentOwner;
		this.targetOwner = targetOwner;
		this.targetName = targetName;
		this.replacementOwner = replacementOwner;
		this.replacementName = replacementName;
		
		this.matchedInvocation = matchedInvocation;
	}

	@Override
	public void visitInsn(int opcode) {
//		if (opcode == Opcodes.ALO
//				|| opcode == Opcodes.ALOAD_1|| opcode == Opcodes.ALOAD_2|| opcode == Opcodes.ALOAD_3) {
//			storeOpcode.add(opcode);
//		}
		super.visitInsn(opcode);
		
	}
	
	@Override
	public void visitVarInsn(int opcode,
            int var) {
		if (opcode == Opcodes.ALOAD) {
			storeOpcode.add(Opcodes.ALOAD);
			storeVar.add(var);
		} 
		super.visitVarInsn(opcode, var);

	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {

		if (!targetOwner.equals(owner) || !targetName.equals(name)) {
			logger.warn("\t\tdid not match: " + owner + " : " + name);

			super.visitMethodInsn(opcode, owner, name, desc, itf);
			storeOpcode.clear();
			storeVar.clear();
			return;
		}
		logger.warn("detected! replacing with " + replacementOwner + " : " + replacementName + " desc=" + 
		"(L" + replacementOwner + ";)" + desc.split(";\\)")[1]);
		logger.warn("matched owner= " + owner + " name=" + name + " desc=" + desc);
		matchedInvocation.matchedSignature = desc;


		// transform nothing 
//		super.visitMethodInsn(opcode, owner, name, desc, itf);
//		storeOpcode.clear();
//		storeVar.clear();
		
		// if static, keep arguments, no receiver -> nothing to pop
		// if not static, want to keep arguments but pop away the method receiver
		if (opcode != Opcodes.INVOKESTATIC) { // need to pop the receiver at least (if original wasn't static).
			logger.warn("not invoke static, need to do some popping");
			if (!storeOpcode.isEmpty()) {
				logger.warn("stored opcode size = " + storeOpcode.size());
				for (int i = 0; i < storeOpcode.size(); i++) {
					super.visitInsn(Opcodes.POP);
				}
				for (int i = 1; i < storeOpcode.size(); i++) { // start from 1 to skip the method receiver
					if (storeOpcode.get(i) == Opcodes.ALOAD) {
						super.visitVarInsn(storeOpcode.get(i), storeVar.get(i));
					} else {
						// assuming its ALOAD_X
						super.visitInsn(storeOpcode.get(i));
					}
					
				}
			}
		}
		storeOpcode.clear();
		storeVar.clear();
		
		super.visitMethodInsn(Opcodes.INVOKESTATIC, 
				replacementOwner, replacementName, 
				desc, false);
	}
}
