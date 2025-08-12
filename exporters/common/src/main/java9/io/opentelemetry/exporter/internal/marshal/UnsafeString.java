/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * VarHandle-based String field access for Java 9+ using direct VarHandle APIs. This version uses
 * VarHandle when available, falls back to sun.misc.Unsafe, and provides safe fallbacks when
 * neither is available.
 */
class UnsafeString {
  private static final Logger logger = Logger.getLogger(UnsafeString.class.getName());

  private static final VarHandle valueHandle = getStringFieldHandle("value", byte[].class);
  private static final VarHandle coderHandle = getStringFieldHandle("coder", byte.class);
  
  // Unsafe fallback fields
  private static final long valueOffset = getStringFieldOffset("value", byte[].class);
  private static final long coderOffset = getStringFieldOffset("coder", byte.class);
  private static final int byteArrayBaseOffset =
      UnsafeAccess.isAvailable() ? UnsafeAccess.arrayBaseOffset(byte[].class) : -1;
  
  private static final boolean available = 
      (valueHandle != null && coderHandle != null) ||
      (valueOffset != -1 && coderOffset != -1);

  static boolean isAvailable() {
    return available;
  }

  static boolean isLatin1(String string) {
    // Try VarHandle first
    if (coderHandle != null) {
      try {
        byte coder = (Byte) coderHandle.get(string);
        // 0 represents latin1, 1 utf16
        return coder == 0;
      } catch (RuntimeException e) {
        logger.log(Level.FINE, "VarHandle failed to check String encoding", e);
      }
    }

    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && coderOffset != -1) {
      try {
        // 0 represents latin1, 1 utf16
        return UnsafeAccess.getByte(string, coderOffset) == 0;
      } catch (RuntimeException e) {
        logger.log(Level.FINE, "Unsafe failed to check String encoding", e);
      }
    }

    return false;
  }

  @SuppressWarnings("NullAway")
  static byte[] getBytes(String string) {
    // Try VarHandle first
    if (valueHandle != null) {
      try {
        return (byte[]) valueHandle.get(string);
      } catch (RuntimeException e) {
        logger.log(Level.FINE, "VarHandle failed to get String bytes", e);
      }
    }

    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && valueOffset != -1) {
      try {
        return (byte[]) UnsafeAccess.getObject(string, valueOffset);
      } catch (RuntimeException e) {
        logger.log(Level.FINE, "Unsafe failed to get String bytes", e);
      }
    }

    return null;
  }

  static long getLong(byte[] bytes, int index) {
    // Try VarHandle approach with manual reconstruction
    if (valueHandle != null) {
      return getLongFallback(bytes, index);
    }

    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && byteArrayBaseOffset != -1) {
      try {
        return UnsafeAccess.getLong(bytes, byteArrayBaseOffset + index);
      } catch (RuntimeException e) {
        logger.log(Level.FINE, "Unsafe failed to get long from byte array", e);
      }
    }

    return getLongFallback(bytes, index);
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

  private static long getStringFieldOffset(String fieldName, Class<?> expectedType) {
    if (!UnsafeAccess.isAvailable()) {
      return -1;
    }

    try {
      Field field = String.class.getDeclaredField(fieldName);
      if (field.getType() != expectedType) {
        return -1;
      }
      return UnsafeAccess.objectFieldOffset(field);
    } catch (Exception exception) {
      return -1;
    }
  }

  private UnsafeString() {}
}