/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.MAX_INNER_LOOP_SIZE;
import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.NEGATIVE_BYTE_MASK;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import javax.annotation.Nullable;

/**
 * StringEncoder implementation using VarHandle for high performance on Java 9+.
 *
 * <p>This implementation provides optimized string operations by directly accessing String internal
 * fields using VarHandle operations. It's only created if VarHandle is available and all required
 * handles can be resolved.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class VarHandleStringEncoder implements StringEncoder {

  private static final FallbackStringEncoder fallback = new FallbackStringEncoder();

  // VarHandle for accessing String fields
  private final VarHandle valueHandle;
  private final VarHandle coderHandle;
  private final VarHandle byteArrayHandle;

  private VarHandleStringEncoder(
      VarHandle valueHandle, VarHandle coderHandle, VarHandle byteArrayHandle) {
    this.valueHandle = valueHandle;
    this.coderHandle = coderHandle;
    this.byteArrayHandle = byteArrayHandle;
  }

  @Nullable
  static VarHandleStringEncoder createIfAvailable() {
    try {
      VarHandle valueHandle1 = getStringFieldHandle("value", byte[].class);
      VarHandle coderHandle1 = getStringFieldHandle("coder", byte.class);
      VarHandle byteHandle = MethodHandles.arrayElementVarHandle(byte[].class);

      if (valueHandle1 == null || coderHandle1 == null || byteHandle == null) {
        return null;
      }

      return new VarHandleStringEncoder(valueHandle1, coderHandle1, byteHandle);
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
      return ((byte) coderHandle.get(string)) == 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Nullable
  private byte[] getBytes(String string) {
    try {
      return (byte[]) valueHandle.get(string);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private long getLong(byte[] bytes, int index) {
    // VarHandle approach for getting long from byte array
    return (long) byteArrayHandle.get(bytes, index)
        | ((long) byteArrayHandle.get(bytes, index + 1) << 8)
        | ((long) byteArrayHandle.get(bytes, index + 2) << 16)
        | ((long) byteArrayHandle.get(bytes, index + 3) << 24)
        | ((long) byteArrayHandle.get(bytes, index + 4) << 32)
        | ((long) byteArrayHandle.get(bytes, index + 5) << 40)
        | ((long) byteArrayHandle.get(bytes, index + 6) << 48)
        | ((long) byteArrayHandle.get(bytes, index + 7) << 56);
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

  @Nullable
  private static VarHandle getStringFieldHandle(String fieldName, Class<?> expectedType) {
    try {
      Field field = String.class.getDeclaredField(fieldName);
      if (!expectedType.isAssignableFrom(field.getType())) {
        return null;
      }

      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
      return lookup.findVarHandle(String.class, fieldName, expectedType);
    } catch (Exception exception) {
      return null;
    }
  }
}
