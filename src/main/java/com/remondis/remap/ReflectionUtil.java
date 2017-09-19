package com.remondis.remap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * This is a util class that provides useful reflective methods. <b>Intended for internal use only!</b>.
 *
 * @author schuettec
 */
class ReflectionUtil {

  static final String IS = "is";
  static final String GET = "get";
  static final String SET = "set";

  private static final Set<Class<?>> BUILD_IN_TYPES;
  private static final Map<Class<?>, Object> DEFAULT_VALUES;

  static {
    // schuettec - 08.02.2017 : According to the spec:
    // https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html
    Map<Class<?>, Object> map = new HashMap<Class<?>, Object>();
    map.put(boolean.class, false);
    map.put(char.class, '\0');
    map.put(byte.class, (byte) 0);
    map.put(short.class, (short) 0);
    map.put(int.class, 0);
    map.put(long.class, 0L);
    map.put(float.class, 0f);
    map.put(double.class, 0d);
    DEFAULT_VALUES = Collections.unmodifiableMap(map);

    BUILD_IN_TYPES = new HashSet<>();
    BUILD_IN_TYPES.add(Boolean.class);
    BUILD_IN_TYPES.add(Character.class);
    BUILD_IN_TYPES.add(Byte.class);
    BUILD_IN_TYPES.add(Short.class);
    BUILD_IN_TYPES.add(Integer.class);
    BUILD_IN_TYPES.add(Long.class);
    BUILD_IN_TYPES.add(Float.class);
    BUILD_IN_TYPES.add(Double.class);
    BUILD_IN_TYPES.add(String.class);

  }

  private static final Map<String, Class<?>> primitiveNameMap = new HashMap<>();

  static {
    primitiveNameMap.put(boolean.class.getName(), boolean.class);
    primitiveNameMap.put(byte.class.getName(), byte.class);
    primitiveNameMap.put(char.class.getName(), char.class);
    primitiveNameMap.put(short.class.getName(), short.class);
    primitiveNameMap.put(int.class.getName(), int.class);
    primitiveNameMap.put(long.class.getName(), long.class);
    primitiveNameMap.put(double.class.getName(), double.class);
    primitiveNameMap.put(float.class.getName(), float.class);
    primitiveNameMap.put(void.class.getName(), void.class);
  }

  /**
   * Checks if the specified type is a Java build-in type. The build-in types are the object versions of the Java
   * primitives like {@link Integer}, {@link Long} but also {@link String}.
   *
   * @param type The type to check
   * @return Returns <code>true</code> if the specified type is a java build-in type.
   */
  public static boolean isBuildInType(Class<?> type) {
    return BUILD_IN_TYPES.contains(type);
  }

  /**
   * Returns the default value for the specified primitive type according to the Java Language Specification. See
   * https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html for more information.
   *
   * @param type The type of the primitive.
   * @return Returns the default value of the specified primitive type.
   */
  @SuppressWarnings("unchecked")
  static <T> T defaultValue(Class<T> type) {
    return (T) DEFAULT_VALUES.get(type);
  }

  /**
   * This method selects a {@link Collector} according to the specified {@link Collection} instance. This method
   * currently supports {@link Set} and {@link List}.
   *
   * @param collection The actual collection instance.
   * @return Returns the {@link Collector} that creates a new {@link Collection} of the same type.
   */
  @SuppressWarnings("rawtypes")
  static Collector getCollector(Collection collection) {
    if (collection instanceof Set) {
      return Collectors.toSet();
    } else if (collection instanceof List) {
      return Collectors.toList();
    } else {
      throw MappingException.unsupportedCollection(collection);
    }
  }

  /**
   * Checks if the method has a return type.
   *
   * @param method the method
   * @return <code>true</code>, if return type is not {@link Void} or <code>false</code> otherwise.
   */
  static boolean hasReturnType(Method method) {
    return !method.getReturnType()
      .equals(Void.TYPE);
  }

  static boolean isGetterOrSetter(Method method) {
    return isGetter(method) || isSetter(method);
  }

  static boolean isSetter(Method method) {
    boolean validName = method.getName()
      .startsWith(SET);
    boolean hasArguments = hasArguments(method, 1);
    boolean hasReturnType = hasReturnType(method);
    return validName && !hasReturnType && hasArguments;
  }

  static boolean isGetter(Method method) {
    boolean isBool = isBoolGetter(method);
    boolean validName = (isBool ? method.getName()
      .startsWith(IS)
        : method.getName()
          .startsWith(GET));
    boolean hasArguments = hasArguments(method);
    boolean hasReturnType = hasReturnType(method);
    return validName && hasReturnType && !hasArguments;
  }

  static boolean isBoolGetter(Method method) {
    return isBool(method.getReturnType());
  }

  static boolean isBool(Class<?> type) {
    return type == Boolean.TYPE || type == Boolean.class;
  }

  static boolean hasArguments(Method method) {
    return method.getParameterCount() != 0;
  }

  static boolean hasArguments(Method method, int count) {
    return method.getParameterCount() == count;
  }

  /**
   * Returns the name of a property represented with either a getter or setter method.
   *
   * @param method The getter or setter method.
   * @return Returns the name of the property.
   */
  static String toPropertyName(Method method) {
    String name = method.getName();
    if (isBoolGetter(method)) {
      return firstCharacterToLowerCase(name.substring(2, name.length()));
    } else {
      if (isGetterOrSetter(method)) {
        return firstCharacterToLowerCase(name.substring(3, name.length()));
      } else {
        throw new IllegalArgumentException("The specified method is neither a getter nor a setter method.");
      }
    }
  }

  private static String firstCharacterToLowerCase(String input) {
    char[] c = input.toCharArray();
    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }

  /**
   * This method calls a method on the specified object. <b>This method takes into account, that the specified object
   * can also be a proxy instance.</b> In this case, the method to be called must be redefined with searching it on the
   * proxy. (Proxy instances are not classes of the type the method was declared in.)
   *
   * @param method The method to be invoked
   * @param targetObject The target object or proxy instance.
   * @param args (Optional) Arguments to pass to the invoked method or <code>null</code> indicating no parameters.
   * @return Returns the return value of the method on demand.
   * @throws IllegalAccessException Thrown on any access error.
   * @throws InvocationTargetException Thrown on any invocation error.
   * @throws SecurityException Thrown if the reflective operation is not allowed
   * @throws NoSuchMethodException Thrown if the proxy instance does not provide the desired method.
   */
  static Object invokeMethodProxySafe(Method method, Object targetObject,
                                      Object... args) throws IllegalAccessException, InvocationTargetException,
                                                      SecurityException, NoSuchMethodException {
    Method effectiveMethod = method;
    Class<?> clazz = targetObject.getClass();
    if (Proxy.isProxyClass(clazz)) {
      // schuettec - 08.02.2017 : Find the method on the specified proxy.
      effectiveMethod = targetObject.getClass()
        .getMethod(method.getName(), method.getParameterTypes());
    }
    if (args == null) {
      return effectiveMethod.invoke(targetObject);
    } else {
      return effectiveMethod.invoke(targetObject, args);
    }
  }

  /**
   * Creates a new instance of the specified type.
   *
   * @param type The type to instantiate
   * @return Returns a new instance.
   */
  static <D> D newInstance(Class<D> type) {
    try {
      Constructor<D> constructor = type.getConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (InstantiationException e) {
      throw MappingException.noDefaultConstructor(type, e);
    } catch (Exception e) {
      throw MappingException.newInstanceFailed(type, e);
    }
  }

}
