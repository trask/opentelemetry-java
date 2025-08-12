/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

/**
 * Fallback String field access for Java 8. This version provides a stub implementation that
 * indicates VarHandle is not available, allowing the system to fall back to other mechanisms like
 * sun.misc.Unsafe or safe implementations.
 */
class VarHandleString {

  static boolean isAvailable() {
    // VarHandle is not available in Java 8
    return false;
  }

  static boolean isLatin1(String string) {
    // Not available in Java 8 fallback
    return false;
  }

  @SuppressWarnings("NullAway")
  static byte[] getBytes(String string) {
    // Not available in Java 8 fallback
    return null;
  }

  static long getLong(byte[] bytes, int byteIndex) {
    // Not available in Java 8 fallback
    return 0L;
  }

  private VarHandleString() {}
}
