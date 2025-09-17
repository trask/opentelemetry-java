/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.MAX_INNER_LOOP_SIZE;
import static io.opentelemetry.exporter.internal.marshal.StringEncoderConstants.NEGATIVE_BYTE_MASK;

import java.io.IOException;
import javax.annotation.Nullable;

/**
 * This class contains shared logic for UTF-8 encoding operations while allowing subclasses to
 * implement different mechanisms for accessing String internal byte arrays (e.g., Unsafe vs
 * VarHandle).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
abstract class AbstractStringEncoder implements StringEncoder {

  @Override
  public final int getUtf8Size(String string) {
    if (string.isEmpty()) {
      return 0;
    }

    byte[] bytes = getStringBytes(string);
    if (bytes != null) {
      if (isLatin1(string)) {
        return string.length() + countNegative(bytes);
      } else {
        // UTF-16 case - fall back to standard calculation
        return string.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      }
    }

    // Fall back to standard calculation if we can't access internal bytes
    return string.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  @Override
  public final void writeUtf8(CodedOutputStream output, String string, int utf8Length)
      throws IOException {
    if (string.isEmpty()) {
      return;
    }

    byte[] bytes = getStringBytes(string);
    if (bytes != null && isLatin1(string)) {
      // Fast path for Latin-1 strings
      writeUtf8Latin1(output, bytes, string.length());
    } else {
      // Fall back to standard UTF-8 encoding
      byte[] utf8Bytes = string.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      output.write(utf8Bytes, 0, utf8Bytes.length);
    }
  }

  @Nullable
  protected abstract byte[] getStringBytes(String string);

  protected abstract boolean isLatin1(String string);

  protected abstract long getLong(byte[] bytes, int offset);

  private void writeUtf8Latin1(CodedOutputStream output, byte[] bytes, int length)
      throws IOException {
    int offset = 0;

    // Process 8 bytes at a time for performance
    for (int i = 1; i <= length / MAX_INNER_LOOP_SIZE + 1; i++) {
      int limit = Math.min((int) (i * MAX_INNER_LOOP_SIZE), length & ~7);
      for (; offset < limit; offset += 8) {
        long value = getLong(bytes, offset);
        long tmp = value & NEGATIVE_BYTE_MASK;
        if (tmp != 0) {
          // Handle bytes with high bit set
          for (int j = 0; j < 8; j++) {
            int b = (int) ((value >>> (j * 8)) & 0xFF);
            if (b < 0) {
              // Convert negative byte to UTF-8 sequence
              output.write((byte) (0xC0 | ((b & 0xFF) >>> 6)));
              output.write((byte) (0x80 | (b & 0x3F)));
            } else {
              output.write((byte) b);
            }
          }
        } else {
          // All bytes are ASCII, write directly
          for (int j = 0; j < 8; j++) {
            output.write((byte) ((value >>> (j * 8)) & 0xFF));
          }
        }
      }
    }

    // Process remaining bytes
    for (int i = offset; i < length; i++) {
      int b = bytes[i] & 0xFF;
      if (b >= 0x80) {
        // Convert to UTF-8 sequence
        output.write((byte) (0xC0 | (b >>> 6)));
        output.write((byte) (0x80 | (b & 0x3F)));
      } else {
        output.write((byte) b);
      }
    }
  }

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
}
