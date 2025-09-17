/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

/** Shared constants and utilities for StringEncoder implementations. */
final class StringEncoderConstants {

  /** Maximum inner loop size for performance optimization in byte array processing. */
  static final long MAX_INNER_LOOP_SIZE = 1024;

  /** Bit mask for detecting negative bytes in a long value (8 bytes at once). */
  static final long NEGATIVE_BYTE_MASK = 0x8080808080808080L;

  /** Bit mask for extracting sign bits from 8 bytes packed in a long. */
  static final long SIGN_BIT_MASK = 0x0101010101010101L;

  private StringEncoderConstants() {}
}
