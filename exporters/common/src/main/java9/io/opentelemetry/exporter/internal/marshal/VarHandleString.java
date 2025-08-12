/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VarHandle-based String field access for Java 9+ using direct VarHandle APIs. This version avoids
 * reflection by using compile-time access to VarHandle, which is only available in Java 9+.
 */
class VarHandleString {
  private static final Logger logger = Logger.getLogger(VarHandleString.class.getName());

  private static final VarHandle valueHandle = getStringFieldHandle("value", byte[].class);
  private static final VarHandle coderHandle = getStringFieldHandle("coder", byte.class);
  private static final boolean available = valueHandle != null && coderHandle != null;

  static boolean isAvailable() {
    return available;
  }

  static boolean isLatin1(String string) {
    if (coderHandle == null) {
      return false;
    }

    try {
      byte coder = (Byte) coderHandle.get(string);
      // 0 represents latin1, 1 utf16
      return coder == 0;
    } catch (RuntimeException e) {
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
      return (byte[]) valueHandle.get(string);
    } catch (RuntimeException e) {
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
  private static VarHandle getStringFieldHandle(String fieldName, Class<?> expectedType) {
    try {
      // Direct VarHandle access - no reflection needed in Java 9+
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(String.class, lookup);
      VarHandle handle = privateLookup.findVarHandle(String.class, fieldName, expectedType);

      // Test the handle to ensure it works correctly
      String testString = "test";
      Object testValue = handle.get(testString);

      if (testValue == null || !expectedType.isAssignableFrom(testValue.getClass())) {
        logger.log(
            Level.WARNING,
            "VarHandle for String.{0} returned unexpected type: {1}",
            new Object[] {fieldName, testValue != null ? testValue.getClass() : "null"});
        return null;
      }

      return handle;
    } catch (RuntimeException | NoSuchFieldException | IllegalAccessException exception) {
      logger.log(
          Level.FINE,
          "Cannot create VarHandle for String."
              + fieldName
              + " - VarHandle not available or access denied",
          exception);
      return null;
    }
  }

  private VarHandleString() {}
}
