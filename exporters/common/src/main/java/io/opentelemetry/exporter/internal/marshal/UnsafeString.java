/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.reflect.Field;

class UnsafeString {
  private static final long valueOffset = getStringFieldOffset("value", byte[].class);
  private static final long coderOffset = getStringFieldOffset("coder", byte.class);
  private static final int byteArrayBaseOffset =
      UnsafeAccess.isAvailable() ? UnsafeAccess.arrayBaseOffset(byte[].class) : -1;
  private static final boolean available = 
      (valueOffset != -1 && coderOffset != -1) || VarHandleString.isAvailable();

  static boolean isAvailable() {
    return available;
  }

  static boolean isLatin1(String string) {
    // Prefer VarHandle if configured and available
    if (UnsafeAccess.shouldUseVarHandle()) {
      return VarHandleString.isLatin1(string);
    }
    
    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && coderOffset != -1) {
      // 0 represents latin1, 1 utf16
      return UnsafeAccess.getByte(string, coderOffset) == 0;
    }
    
    return false;
  }

  @SuppressWarnings("NullAway")
  static byte[] getBytes(String string) {
    // Prefer VarHandle if configured and available
    if (UnsafeAccess.shouldUseVarHandle()) {
      return VarHandleString.getBytes(string);
    }
    
    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && valueOffset != -1) {
      return (byte[]) UnsafeAccess.getObject(string, valueOffset);
    }
    
    return null;
  }

  static long getLong(byte[] bytes, int index) {
    // Prefer VarHandle if configured and available
    if (UnsafeAccess.shouldUseVarHandle()) {
      return VarHandleString.getLong(bytes, index);
    }
    
    // Fallback to Unsafe if available
    if (UnsafeAccess.isAvailable() && byteArrayBaseOffset != -1) {
      return UnsafeAccess.getLong(bytes, byteArrayBaseOffset + index);
    }
    
    return 0L;
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
