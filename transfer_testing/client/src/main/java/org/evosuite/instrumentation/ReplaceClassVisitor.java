package org.evosuite.instrumentation;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ReplaceClassVisitor extends ClassVisitor {
	
	private static final Logger logger = LoggerFactory.getLogger(ReplaceClassVisitor.class);
	// containing class
	// the replacement is only performed in this class 
	String parentOwner;

	// target replacement
	// target -> replacement
	String targetOwner;
	String targetName;
	String replacementOwner;
	String replacementName;

	// updated during visiting. Tracks the current class name
	String currentClazzName;
	
	// passed into the method visitor to track information that is used in this class
	MatchedInvocation matchedInvocation;
	public static class MatchedInvocation {
		public String matchedSignature;
	}
	

	public ReplaceClassVisitor(int api, ClassVisitor cv, String parentOwner, String targetOwner,
			String targetName, String replacementOwner, String replacementName) {
		super(api, cv);

		this.parentOwner = parentOwner;

		this.targetOwner = targetOwner;
		this.targetName = targetName;
		this.replacementOwner = replacementOwner;
		this.replacementName = replacementName;

	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.currentClazzName = name;
		super.visit(version, access, name, signature, superName, interfaces);

	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
		if (!currentClazzName.equals(this.parentOwner)) {
			return methodVisitor;
		}
//		System.out.println("passed clazz check");
		
		if (matchedInvocation == null) {
			matchedInvocation = new MatchedInvocation();
		}
		return new ReplaceMethodVisitor(Opcodes.ASM4, methodVisitor, 
				parentOwner, targetOwner, targetName,
				replacementOwner, replacementName, matchedInvocation);
	}

	@Override
	public void visitEnd() {
		if (matchedInvocation != null && matchedInvocation.matchedSignature != null) {
			appendDoNothing();
		}
		super.visitEnd();
	}
	
	//https://stackoverflow.com/questions/275944/how-do-i-count-the-number-of-occurrences-of-a-char-in-a-string
	private static int countOccurrences(String haystack, char needle)
	{
	    int count = 0;
	    for (int i=0; i < haystack.length(); i++)
	    {
	        if (haystack.charAt(i) == needle)
	        {
	             count++;
	        }
	    }
	    return count;
	}

	private void appendDoNothing() {
//		logger.warn("adding new method. descriptor= " +matchedInvocation.matchedSignature);
//		final MethodVisitor defVisitor = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "doNothing", matchedInvocation.matchedSignature, null, null);
//		defVisitor.visitCode();
////		
//		int numParamers = countOccurrences(matchedInvocation.matchedSignature, ';');
////		for (int i = 0; i < numParamers; i++) {
////			defVisitor.visitLdcInsn(i);
////			defVisitor.visitIntInsn(Opcodes.ALOAD, i);
////			defVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
////					"org/evosuite/testcase/execution/ExecutionTracer",
////			                   "enteredMethodWithArgument", "(Ljava/lang/Integer;Ljava/lang/Object;)V", false);
////		}
//		
//		defVisitor.visitInsn(Opcodes.ACONST_NULL);
//		defVisitor.visitInsn(Opcodes.ARETURN);
//		logger.warn("added new method. #params = " + numParamers);
	}

}