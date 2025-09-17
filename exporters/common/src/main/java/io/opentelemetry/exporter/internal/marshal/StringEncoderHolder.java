/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Factory and holder class for StringEncoder implementations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// public visibility only needed for benchmarking purposes
public final class StringEncoderHolder {
  private static final Logger logger = Logger.getLogger(StringEncoderHolder.class.getName());

  static final StringEncoder INSTANCE = createInstance();

  /**
   * Creates a FallbackStringEncoder instance.
   *
   * @return a new FallbackStringEncoder instance
   */
  public static StringEncoder createFallbackEncoder() {
    return new FallbackStringEncoder();
  }

  /**
   * Creates an UnsafeStringEncoder instance if available.
   *
   * @return an UnsafeStringEncoder instance if available, or null if not available
   */
  @Nullable
  public static StringEncoder createUnsafeEncoder() {
    return UnsafeStringEncoder.createIfAvailable();
  }

  /**
   * Creates a VarHandleStringEncoder instance if available.
   *
   * @return a VarHandleStringEncoder instance if available, or null if not available
   */
  @Nullable
  public static StringEncoder createVarHandleEncoder() {
    try {
      Class<?> varHandleClass =
          Class.forName("io.opentelemetry.exporter.internal.marshal.VarHandleStringEncoder");
      java.lang.reflect.Method createMethod = varHandleClass.getMethod("createIfAvailable");
      return (StringEncoder) createMethod.invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static StringEncoder createInstance() {
    // Try VarHandle implementation first (Java 9+)
    StringEncoder varHandleImpl = createVarHandleEncoder();
    if (varHandleImpl != null) {
      logger.log(Level.FINE, "Using VarHandleStringEncoder for optimal Java 9+ performance");
      return varHandleImpl;
    }

    // Try Unsafe implementation (Java 8+)
    StringEncoder unsafeImpl = createUnsafeEncoder();
    if (unsafeImpl != null) {
      logger.log(Level.FINE, "Using UnsafeStringEncoder for optimized Java 8+ performance");
      return unsafeImpl;
    }

    // Use fallback implementation
    logger.log(Level.FINE, "Using FallbackStringEncoder");
    return createFallbackEncoder();
  }

  private StringEncoderHolder() {}
}
