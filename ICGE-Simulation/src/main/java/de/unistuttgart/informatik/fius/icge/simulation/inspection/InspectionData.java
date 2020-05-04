/*
 * This source file is part of the FIUS ICGE project.
 * For more information see github.com/FIUS/ICGE2
 * 
 * Copyright (c) 2019 the ICGE project authors.
 * 
 * This software is available under the MIT license.
 * SPDX-License-Identifier:    MIT
 */
package de.unistuttgart.informatik.fius.icge.simulation.inspection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Represents all data available for the inspection for a class
 * 
 * @author Tim Neumann
 */
public class InspectionData {
    private final Class<?> c;
    
    private final Map<String, AttributeInspectionPoint> inspectableAttributes;
    private final Map<String, Method>                   inspectableMethods;
    
    /**
     * Creates a new inspection data object for the given class
     * 
     * @param cls
     *     The class to create a inspection data object for.
     */
    public InspectionData(final Class<?> cls) {
        this.c = cls;
        this.inspectableAttributes = new HashMap<>();
        this.inspectableMethods = new HashMap<>();
        this.initAttributes();
        this.initMethods();
    }
    
    /**
     * Get the value of the attribute with the given name from the given object.
     * 
     * @param obj
     *     The object to get the value from
     * @param name
     *     The name of the attribute to get the value from
     * @return The value
     */
    public Object getAttributeValue(final Object obj, final String name) {
        final AttributeInspectionPoint p = this.inspectableAttributes.get(name);
        if (p != null) {
            try {
                return p.getValue(obj);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    /**
     * Set's the value of the attribute with the given name for the given object
     * 
     * @param obj
     *     The object to set the value in.
     * @param name
     *     The name of the attribute to set.
     * @param value
     *     The value to set.
     * @return Whether it worked.
     */
    public boolean setAttributeValue(final Object obj, final String name, final Object value) {
        final AttributeInspectionPoint p = this.inspectableAttributes.get(name);
        if (p != null) {
            try {
                p.setValue(obj, value);
                return true;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    /**
     * Get the attribute names of the class for this inspection data.
     * 
     * @return A list of attribute names.
     */
    public List<String> getAttributeNames() {
        return Collections.unmodifiableList(new ArrayList<>(this.inspectableAttributes.keySet()));
    }
    
    /**
     * Get the type of the attribute with the given name.
     * 
     * @param attributeName
     *     The name of the attribute
     * @return The type of the attribute.
     */
    public Class<?> getAttributeType(final String attributeName) {
        final AttributeInspectionPoint p = this.inspectableAttributes.get(attributeName);
        if (p == null) return null;
        return p.getType();
    }
    
    /**
     * Check whether the attribute with the given name is read only.
     * 
     * @param attributeName
     *     The name of the attribute.
     * @return Whether the attribute is read only.
     */
    public boolean isAttributeReadOnly(final String attributeName) {
        final AttributeInspectionPoint p = this.inspectableAttributes.get(attributeName);
        if (p == null) return true;
        return p.isReadOnly();
    }
    
    /**
     * @return Whether this inspection data has any inspectable elements.
     */
    public boolean hasAnyInspectableElements() {
        return !(this.inspectableAttributes.isEmpty() && this.inspectableMethods.isEmpty());
    }
    
    /**
     * Get the mathod names of the class for this inspection data.
     * 
     * @return A list of method names.
     */
    public List<String> getMethodNames() {
        return Collections.unmodifiableList(new ArrayList<>(this.inspectableMethods.keySet()));
    }
    
    /**
     * Get the method detail for the method with the given name
     * 
     * @param methodName
     *     The name of the method to get the detail for
     * @return The method detail.
     */
    public Method getMethodByName(final String methodName) {
        return this.inspectableMethods.get(methodName);
    }
    
    /**
     * Invoke the method with the given name in the given object, using the given arguments
     * 
     * @param obj
     *     The object to call the method on
     * @param methodName
     *     The name of the method to call
     * @param args
     *     The arguments to use
     * @return The return value.
     */
    public Object invokeMethod(final Object obj, final String methodName, final Object... args) {
        final Method m = this.inspectableMethods.get(methodName);
        if (m == null) throw new IllegalStateException("No such method!");
        try {
            return m.invoke(obj, args);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Invokation didn't work", e);
        }
    }
    
    private void initMethods() {
        final List<Method> methods = AnnotationReader.getAllMethodsWithAnnotationRecursively(this.c, InspectionMethod.class);
        
        for (final Method m : methods) {
            m.setAccessible(true);
            this.inspectableMethods.put(this.getDsiplayNameForInspectionMethod(m), m);
        }
    }
    
    private void initAttributes() {
        final List<Field> fields = AnnotationReader.getAllAttributesWithAnnotationRecursively(this.c, InspectionAttribute.class);
        final List<Method> methods = AnnotationReader.getAllMethodsWithAnnotationRecursively(this.c, InspectionAttribute.class);
        
        for (final Field f : fields) {
            f.setAccessible(true);
            this.inspectableAttributes.put(this.getDisplayNameForField(f), new AttributeInspectionPoint(f));
        }
        
        final Map<String, Method> getters = new HashMap<>();
        final Map<String, Method> setters = new HashMap<>();
        
        for (int i = 0; i < methods.size(); i++) {
            final Method m = methods.get(i);
            
            if (this.isGetter(m)) {
                getters.put(this.getDisplayNameForMethod(m, "get"), m);
            } else if (this.isSetter(m)) {
                setters.put(this.getDisplayNameForMethod(m, "set"), m);
            } else throw new InspectionPointException("Method is neither a getter nor a setter! : " + m.getName());
        }
        
        for (final Entry<String, Method> entry : getters.entrySet()) {
            final String name = entry.getKey();
            final Method setter = setters.remove(name);
            final Method getter = entry.getValue();
            final boolean readOnly = getter.getAnnotation(InspectionAttribute.class).readOnly();
            if (readOnly || (setter == null)) {
                if (setter != null) throw new InspectionPointException("Getter specifies read only, but setter found! : " + name);
                this.validateReadOnlyGetter(getter);
                getter.setAccessible(true);
                this.inspectableAttributes.put(name, new AttributeInspectionPoint(getter));
                
            } else {
                this.validateMethodPair(getter, setter);
                getter.setAccessible(true);
                setter.setAccessible(true);
                this.inspectableAttributes.put(name, new AttributeInspectionPoint(getter, setter));
            }
            
        }
        
        if (setters.size() > 0) throw new InspectionPointException("No getter for setter! : " + setters.values().iterator().next());
        
    }
    
    private boolean isGetter(final Method met) {
        return (met.getParameterTypes().length == 0) && (met.getReturnType() != Void.TYPE);
    }
    
    private boolean isSetter(final Method met) {
        return (met.getParameterTypes().length == 1) && (met.getReturnType() == Void.TYPE);
    }
    
    private void validateMethodPair(final Method getter, final Method setter) {
        final Class<?> type = getter.getReturnType();
        
        if (type.equals(Void.TYPE)) throw new InspectionPointException("Getter should return something! : " + getter.getName());
        
        if (
            getter.getParameterTypes().length != 0
        ) throw new InspectionPointException("Getter should not have parameters! : " + getter.getName());
        
        if (
            !setter.getReturnType().equals(Void.TYPE)
        ) throw new InspectionPointException("Setter should not be return type null. : " + setter.getName());
        
        final Class<?>[] setterParas = setter.getParameterTypes();
        
        if (setterParas.length != 1) throw new InspectionPointException("Setter should have exactly one parameter! : " + setter.getName());
        
        if (
            !setterParas[0].equals(type)
        ) throw new InspectionPointException("Getter parameter is not the same type as getter return value");
        
    }
    
    private void validateReadOnlyGetter(final Method getter) {
        if (
            getter.getReturnType().equals(Void.TYPE)
        ) throw new InspectionPointException("Getter should return something! : " + getter.getName());
        
        if (
            getter.getParameterTypes().length != 0
        ) throw new InspectionPointException("Getter should not have parameters! : " + getter.getName());
    }
    
    private String getDisplayNameForField(final Field f) {
        final InspectionAttribute anno = f.getAnnotation(InspectionAttribute.class);
        if (anno != null) {
            final String name = anno.name();
            if ((name != null) && !name.isEmpty()) return name;
        }
        return f.getName();
    }
    
    private String getDsiplayNameForInspectionMethod(final Method m) {
        final InspectionMethod anno = m.getAnnotation(InspectionMethod.class);
        if (anno != null) {
            final String name = anno.name();
            if ((name != null) && !name.isEmpty()) return name;
        }
        return m.getName();
    }
    
    private String getDisplayNameForMethod(final Method m, final String possiblePrefixToRemove) {
        final InspectionAttribute anno = m.getAnnotation(InspectionAttribute.class);
        if (anno != null) {
            final String name = anno.name();
            if ((name != null) && !name.isEmpty()) return name;
        }
        
        String name = m.getName();
        
        if (name.toLowerCase().startsWith(possiblePrefixToRemove)) {
            name = name.substring(3);
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        }
        return name;
    }
    
    private static class AttributeInspectionPoint {
        private static final Map<Class<?>, Class<?>> primitiveToWrapperMap = new HashMap<>();
        static {
            AttributeInspectionPoint.primitiveToWrapperMap.put(Integer.TYPE, Integer.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Long.TYPE, Long.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Character.TYPE, Character.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Byte.TYPE, Byte.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Float.TYPE, Float.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Double.TYPE, Double.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Short.TYPE, Short.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Boolean.TYPE, Boolean.class);
            AttributeInspectionPoint.primitiveToWrapperMap.put(Void.TYPE, Void.class);
        }
        private final boolean  usesField;
        private final Field    f;
        private final Method   getter;
        private final Method   setter;
        private final Class<?> type;
        private final boolean  readOnly;
        
        /**
         * Creates a new attribute inspection point for a field
         * 
         * @param field
         *     The field for the inspection point.
         */
        public AttributeInspectionPoint(final Field field) {
            this.usesField = true;
            this.f = field;
            this.getter = null;
            this.setter = null;
            this.type = this.convertTypeToWrappers(field.getType());
            this.readOnly = field.getAnnotation(InspectionAttribute.class).readOnly();
        }
        
        public AttributeInspectionPoint(final Method getter) {
            this.usesField = false;
            this.f = null;
            this.getter = getter;
            this.setter = null;
            this.type = this.convertTypeToWrappers(getter.getReturnType());
            this.readOnly = true;
        }
        
        public AttributeInspectionPoint(final Method getter, final Method setter) {
            this.usesField = false;
            this.f = null;
            this.getter = getter;
            this.setter = setter;
            this.type = this.convertTypeToWrappers(getter.getReturnType());
            this.readOnly = false;
        }
        
        private Class<?> convertTypeToWrappers(final Class<?> cls) {
            if (!cls.isPrimitive()) return cls;
            return AttributeInspectionPoint.primitiveToWrapperMap.get(cls);
        }
        
        public Object getValue(final Object obj) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            if (this.usesField) return this.f.get(obj);
            return this.getter.invoke(obj);
        }
        
        public void setValue(final Object obj, final Object value)
                throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
            if (this.readOnly) throw new InspectionPointException("Attribute is read only.");
            if (!this.type.isAssignableFrom(value.getClass())) throw new IllegalArgumentException("Not the correct attribute type.");
            if (this.usesField) {
                this.f.set(obj, value);
            } else {
                this.setter.invoke(obj, value);
            }
        }
        
        /**
         * Get's {@link #type type}
         * 
         * @return type
         */
        public Class<?> getType() {
            return this.type;
        }
        
        /**
         * Get's {@link #readOnly readOnly}
         * 
         * @return readOnly
         */
        public boolean isReadOnly() {
            return this.readOnly;
        }
    }
    
    /**
     * A exception that is thrown when an error with a inspection point occurs.
     * 
     * @author Tim Neumann
     */
    public static class InspectionPointException extends RuntimeException {
        /**
         * generated
         */
        private static final long serialVersionUID = 6324656121971704376L;
        
        /**
         * Constructs a new runtime exception with {@code null} as its detail message. The cause is not initialized, and
         * may subsequently be initialized by a call to {@link #initCause}.
         */
        public InspectionPointException() {
            super();
        }
        
        /**
         * Constructs a new runtime exception with the specified detail message. The cause is not initialized, and may
         * subsequently be initialized by a call to {@link #initCause}.
         *
         * @param message
         *     the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
         */
        public InspectionPointException(final String message) {
            super(message);
        }
        
        /**
         * Constructs a new runtime exception with the specified detail message and cause.
         * <p>
         * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this
         * runtime exception's detail message.
         *
         * @param message
         *     the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
         * @param cause
         *     the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value
         *     is permitted, and indicates that the cause is nonexistent or unknown.)
         * @since 1.4
         */
        public InspectionPointException(final String message, final Throwable cause) {
            super(message, cause);
        }
        
        /**
         * Constructs a new runtime exception with the specified cause and a detail message of
         * {@code (cause==null ? null : cause.toString())} (which typically contains the class and detail message of
         * {@code cause}). This constructor is useful for runtime exceptions that are little more than wrappers for
         * other throwables.
         *
         * @param cause
         *     the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value
         *     is permitted, and indicates that the cause is nonexistent or unknown.)
         * @since 1.4
         */
        public InspectionPointException(final Throwable cause) {
            super(cause);
        }
        
        /**
         * Constructs a new runtime exception with the specified detail message, cause, suppression enabled or disabled,
         * and writable stack trace enabled or disabled.
         *
         * @param message
         *     the detail message.
         * @param cause
         *     the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
         * @param enableSuppression
         *     whether or not suppression is enabled or disabled
         * @param writableStackTrace
         *     whether or not the stack trace should be writable
         *
         * @since 1.7
         */
        protected InspectionPointException(
                final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace
        ) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
