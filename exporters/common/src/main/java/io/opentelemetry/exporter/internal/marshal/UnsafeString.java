/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.reflect.Field;

/**
 * Fallback String field access for Java 8. This version uses sun.misc.Unsafe when available and
 * provides safe fallbacks when not available. VarHandle is not available in Java 8.
 */
class UnsafeString {
  private static final long valueOffset = getStringFieldOffset("value", byte[].class);
  private static final long coderOffset = getStringFieldOffset("coder", byte.class);
  private static final int byteArrayBaseOffset =
      UnsafeAccess.isAvailable() ? UnsafeAccess.arrayBaseOffset(byte[].class) : -1;
  private static final boolean available = (valueOffset != -1 && coderOffset != -1);

  static boolean isAvailable() {
    return available;
  }

  static boolean isLatin1(String string) {
    // Use Unsafe if available
    if (UnsafeAccess.isAvailable() && coderOffset != -1) {
      // 0 represents latin1, 1 utf16
      return UnsafeAccess.getByte(string, coderOffset) == 0;
    }

    return false;
  }

  @SuppressWarnings("NullAway")
  static byte[] getBytes(String string) {
    // Use Unsafe if available
    if (UnsafeAccess.isAvailable() && valueOffset != -1) {
      return (byte[]) UnsafeAccess.getObject(string, valueOffset);
    }

    return null;
  }

  static long getLong(byte[] bytes, int index) {
    // Use Unsafe if available
    if (UnsafeAccess.isAvailable() && byteArrayBaseOffset != -1) {
      return UnsafeAccess.getLong(bytes, byteArrayBaseOffset + index);
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
