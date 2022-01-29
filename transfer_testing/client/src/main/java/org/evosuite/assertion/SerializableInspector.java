package org.evosuite.assertion;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.evosuite.TestGenerationContext;
import org.evosuite.runtime.sandbox.Sandbox;
import org.evosuite.setup.TestClusterUtils;
import org.evosuite.utils.LoggingUtils;
import org.objectweb.asm.Type;

public class SerializableInspector {

	public Class<?> clazz;

	public Method method;

	/**
	 * <p>
	 * Constructor for Inspector.
	 * </p>
	 * 
	 * @param clazz a {@link java.lang.Class} object.
	 * @param m     a {@link java.lang.reflect.Method} object.
	 */
	public SerializableInspector(Class<?> clazz, Method m) {
		this.clazz = clazz;
		method = m;
		method.setAccessible(true);
	}

	// no args ctor for gson?
	public SerializableInspector() {

	}

	public static SerializableInspector from(Inspector i) {
		return new SerializableInspector(i.getClazz(), i.getMethod());
	}

	@Override
	public String toString() {
		return this.clazz.getCanonicalName() + ":" + this.method.getName() + ":"
				+ String.join(",", 
						Arrays.asList(this.method.getParameterTypes()).stream()
						.map(clazz -> clazz.getCanonicalName())
						.collect(Collectors.toList()));
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SerializableInspector other = (SerializableInspector) obj;
		if (clazz == null) {
			if (other.clazz != null)
				return false;
		} else if (!clazz.equals(other.clazz))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		return true;
	}
}
