/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.lang.reflect.Field;
import javax.annotation.Nullable;
import sun.misc.Unsafe;

/**
 * StringEncoder implementation using sun.misc.Unsafe for high performance on Java 8+.
 *
 * <p>This implementation provides optimized string operations by directly accessing String internal
 * fields using Unsafe operations. It's only created if Unsafe is available and all required field
 * offsets can be resolved.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class UnsafeStringEncoder extends AbstractStringEncoder {

  // Field offsets for direct memory access
  private final long valueOffset;
  private final long coderOffset;
  private final long byteArrayBaseOffset;

  private UnsafeStringEncoder(long valueOffset, long coderOffset, long byteArrayBaseOffset) {
    this.valueOffset = valueOffset;
    this.coderOffset = coderOffset;
    this.byteArrayBaseOffset = byteArrayBaseOffset;
  }

  @Nullable
  public static UnsafeStringEncoder createIfAvailable() {
    try {
      if (UnsafeHolder.UNSAFE == null) {
        return null;
      }

      long valueOffset = getStringFieldOffset("value", byte[].class);
      long coderOffset = getStringFieldOffset("coder", byte.class);

      if (valueOffset == -1 || coderOffset == -1) {
        return null;
      }

      long byteArrayBaseOffset = UnsafeHolder.UNSAFE.arrayBaseOffset(byte[].class);

      return new UnsafeStringEncoder(valueOffset, coderOffset, byteArrayBaseOffset);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  @Nullable
  protected byte[] getStringBytes(String string) {
    try {
      return (byte[]) UnsafeHolder.UNSAFE.getObject(string, valueOffset);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  protected boolean isLatin1(String string) {
    try {
      return UnsafeHolder.UNSAFE.getByte(string, coderOffset) == 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  protected long getLong(byte[] bytes, int offset) {
    return UnsafeHolder.UNSAFE.getLong(bytes, byteArrayBaseOffset + offset);
  }

  private static long getStringFieldOffset(String fieldName, Class<?> expectedType) {
    try {
      if (UnsafeHolder.UNSAFE == null) {
        return -1;
      }

      Field field = String.class.getDeclaredField(fieldName);
      if (!expectedType.isAssignableFrom(field.getType())) {
        return -1;
      }
      return UnsafeHolder.UNSAFE.objectFieldOffset(field);
    } catch (Exception exception) {
      return -1;
    }
  }

  /** Holder class for lazy initialization of Unsafe. */
  private static final class UnsafeHolder {
    private static final Unsafe UNSAFE;

    static {
      UNSAFE = getUnsafe();
    }

    private UnsafeHolder() {}

    @SuppressWarnings("NullAway")
    private static Unsafe getUnsafe() {
      try {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
      } catch (Exception ignored) {
        return null;
      }
    }
  }
}
