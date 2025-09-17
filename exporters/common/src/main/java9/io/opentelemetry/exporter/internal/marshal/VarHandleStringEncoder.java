/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

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
final class VarHandleStringEncoder extends AbstractStringEncoder {

  private final VarHandle valueHandle;
  private final VarHandle coderHandle;

  private VarHandleStringEncoder(VarHandle valueHandle, VarHandle coderHandle) {
    this.valueHandle = valueHandle;
    this.coderHandle = coderHandle;
  }

  @Nullable
  public static VarHandleStringEncoder createIfAvailable() {
    try {
      VarHandle valueHandle1 = getStringFieldHandle("value", byte[].class);
      VarHandle coderHandle1 = getStringFieldHandle("coder", byte.class);

      if (valueHandle1 == null || coderHandle1 == null) {
        return null;
      }

      return new VarHandleStringEncoder(valueHandle1, coderHandle1);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  @Nullable
  protected byte[] getStringBytes(String string) {
    try {
      return (byte[]) valueHandle.get(string);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  protected boolean isLatin1(String string) {
    try {
      return ((byte) coderHandle.get(string)) == 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  protected long getLong(byte[] bytes, int offset) {
    if (offset + 8 > bytes.length) {
      // Handle bounds - pad with zeros
      long result = 0;
      for (int i = 0; i < 8 && offset + i < bytes.length; i++) {
        result |= ((long) (bytes[offset + i] & 0xFF)) << (i * 8);
      }
      return result;
    }

    // Read 8 bytes in little-endian order (matching Unsafe behavior)
    return ((long) (bytes[offset] & 0xFF))
        | (((long) (bytes[offset + 1] & 0xFF)) << 8)
        | (((long) (bytes[offset + 2] & 0xFF)) << 16)
        | (((long) (bytes[offset + 3] & 0xFF)) << 24)
        | (((long) (bytes[offset + 4] & 0xFF)) << 32)
        | (((long) (bytes[offset + 5] & 0xFF)) << 40)
        | (((long) (bytes[offset + 6] & 0xFF)) << 48)
        | (((long) (bytes[offset + 7] & 0xFF)) << 56);
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
