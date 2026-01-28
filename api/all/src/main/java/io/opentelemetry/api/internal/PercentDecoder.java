/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Decodes percent-encoded strings in accordance with RFC 3986.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class PercentDecoder {

  private static final byte ESCAPE_CHAR = '%';
  private static final int RADIX = 16;

  private PercentDecoder() {}

  /**
   * Decode a percent-encoded string.
   *
   * @param value the percent-encoded string to decode
   * @return the decoded string
   * @throws IllegalArgumentException if the value contains invalid or incomplete percent-encoding
   */
  public static String decode(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i];
      if (b == ESCAPE_CHAR) {
        if (i + 2 >= bytes.length) {
          throw new IllegalArgumentException(
              "Invalid percent-encoding: incomplete escape sequence at end of string");
        }
        int u = Character.digit((char) bytes[++i], RADIX);
        if (u == -1) {
          throw new IllegalArgumentException(
              "Invalid percent-encoding: not a valid hex digit: " + (char) bytes[i]);
        }
        int l = Character.digit((char) bytes[++i], RADIX);
        if (l == -1) {
          throw new IllegalArgumentException(
              "Invalid percent-encoding: not a valid hex digit: " + (char) bytes[i]);
        }
        buffer.write((char) ((u << 4) + l));
      } else {
        buffer.write(b);
      }
    }
    return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
  }
}
