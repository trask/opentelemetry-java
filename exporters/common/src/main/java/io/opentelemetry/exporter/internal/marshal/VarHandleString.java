/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VarHandle-based String field access for Java 9+ as a replacement for sun.misc.Unsafe. This class
 * provides safe and performant access to internal String fields using reflection to maintain Java 8
 * compatibility.
 */
class VarHandleString {
  private static final Logger logger = Logger.getLogger(VarHandleString.class.getName());

  private static final Object valueHandle = getStringFieldHandle("value", byte[].class);
  private static final Object coderHandle = getStringFieldHandle("coder", byte.class);
  private static final boolean available = valueHandle != null && coderHandle != null;

  static boolean isAvailable() {
    return available;
  }

  static boolean isLatin1(String string) {
    if (coderHandle == null) {
      return false;
    }

    try {
      // Use reflection to call VarHandle.get(string)
      Method getMethod = coderHandle.getClass().getMethod("get", Object.class);
      byte coder = (Byte) getMethod.invoke(coderHandle, string);
      // 0 represents latin1, 1 utf16
      return coder == 0;
    } catch (Exception e) {
      logger.log(Level.FINE, "Failed to check String encoding", e);
      return false;
    }
  }

  @SuppressWarnings("NullAway")
  static byte[] getBytes(String string) {
    if (valueHandle == null) {
      return null;
    }

    try {
      // Use reflection to call VarHandle.get(string)
      Method getMethod = valueHandle.getClass().getMethod("get", Object.class);
      return (byte[]) getMethod.invoke(valueHandle, string);
    } catch (Exception e) {
      logger.log(Level.FINE, "Failed to get String bytes", e);
      return null;
    }
  }

  static long getLong(byte[] bytes, int byteIndex) {
    // Fallback: manually construct long from bytes
    // This is safe and doesn't require VarHandle array access which is more complex
    return getLongFallback(bytes, byteIndex);
  }

  private static long getLongFallback(byte[] bytes, int byteIndex) {
    // Manual reconstruction of 8 bytes into a long
    // This matches the behavior expected from unsafe access
    if (byteIndex + 7 >= bytes.length) {
      return 0L;
    }

    long result = 0L;
    for (int i = 0; i < 8; i++) {
      result |= (long) (bytes[byteIndex + i] & 0xFF) << (i * 8);
    }
    return result;
  }

  @SuppressWarnings("NullAway")
  private static Object getStringFieldHandle(String fieldName, Class<?> expectedType) {
    try {
      // Use reflection to avoid compile-time dependency on VarHandle (Java 9+)
      Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
      Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
      Class<?> varHandleClass = Class.forName("java.lang.invoke.VarHandle");

      // Get the lookup() method
      Method lookupMethod = methodHandlesClass.getMethod("lookup");
      Object lookup = lookupMethod.invoke(null);

      // Get privateLookupIn method (Java 9+)
      Method privateLookupInMethod =
          methodHandlesClass.getMethod("privateLookupIn", Class.class, lookupClass);
      Object privateLookup = privateLookupInMethod.invoke(null, String.class, lookup);

      // Get the findVarHandle method
      Method findVarHandleMethod =
          lookupClass.getMethod("findVarHandle", Class.class, String.class, Class.class);
      Object handle =
          findVarHandleMethod.invoke(privateLookup, String.class, fieldName, expectedType);

      // Test the handle to ensure it works correctly
      String testString = "test";
      Method getMethod = varHandleClass.getMethod("get", Object.class);
      Object testValue = getMethod.invoke(handle, testString);

      if (testValue == null || !expectedType.isAssignableFrom(testValue.getClass())) {
        logger.log(
            Level.WARNING,
            "VarHandle for String.{0} returned unexpected type: {1}",
            new Object[] {fieldName, testValue != null ? testValue.getClass() : "null"});
        return null;
      }

      return handle;
    } catch (Exception exception) {
      logger.log(
          Level.FINE,
          "Cannot create VarHandle for String."
              + fieldName
              + " - likely running on Java 8 or VarHandle not available",
          exception);
      return null;
    }
  }

  private VarHandleString() {}
}
