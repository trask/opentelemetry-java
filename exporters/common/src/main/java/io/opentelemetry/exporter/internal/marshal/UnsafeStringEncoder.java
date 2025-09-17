/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.MAX_INNER_LOOP_SIZE;
import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.NEGATIVE_BYTE_MASK;

import java.io.IOException;
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
final class UnsafeStringEncoder implements StringEncoder {

  private static final FallbackStringEncoder fallback = new FallbackStringEncoder();

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
  static UnsafeStringEncoder createIfAvailable() {
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
  public int getUtf8Size(String string) {
    // Latin1 bytes with negative value are encoded as 2 bytes in UTF-8
    if (isLatin1(string)) {
      byte[] bytes = getBytes(string);
      if (bytes != null) {
        return string.length() + countNegative(bytes);
      }
    }

    // Fallback to standard UTF-8 size calculation
    return fallback.getUtf8Size(string);
  }

  @Override
  public void writeUtf8(CodedOutputStream output, String string, int utf8Length)
      throws IOException {
    if (string.length() == utf8Length && isLatin1(string)) {
      byte[] bytes = getBytes(string);
      if (bytes != null) {
        output.write(bytes, 0, bytes.length);
        return;
      }
    }

    // Fallback to standard UTF-8 encoding
    fallback.writeUtf8(output, string, utf8Length);
  }

  private boolean isLatin1(String string) {
    try {
      return UnsafeHolder.UNSAFE.getByte(string, coderOffset) == 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Nullable
  private byte[] getBytes(String string) {
    try {
      return (byte[]) UnsafeHolder.UNSAFE.getObject(string, valueOffset);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private long getLong(byte[] bytes, int index) {
    return UnsafeHolder.UNSAFE.getLong(bytes, byteArrayBaseOffset + index);
  }

  /** Returns the count of bytes with negative value. */
  private int countNegative(byte[] bytes) {
    int count = 0;
    int offset = 0;

    // Process 8 bytes at a time for performance
    for (int i = 1; i <= bytes.length / MAX_INNER_LOOP_SIZE + 1; i++) {
      int limit = Math.min((int) (i * MAX_INNER_LOOP_SIZE), bytes.length & ~7);
      for (; offset < limit; offset += 8) {
        long value = getLong(bytes, offset);
        long tmp = value & NEGATIVE_BYTE_MASK;
        if (tmp != 0) {
          for (int j = 0; j < 8; j++) {
            if ((tmp & 0x80) != 0) {
              count++;
            }
            tmp = tmp >>> 8;
          }
        }
      }
    }

    // Process remaining bytes
    for (int i = offset; i < bytes.length; i++) {
      count += bytes[i] >>> 31;
    }

    return count;
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
