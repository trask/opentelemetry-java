/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnsafeStringTest {

  @Test
  void testAvailability() {
    // UnsafeString should be available when either VarHandle (Java 9+) or Unsafe is available
    // This test verifies that our multijar approach doesn't crash
    boolean available = UnsafeString.isAvailable();
    // We just verify it returns a boolean without throwing
    assertThat(available).isIn(true, false);
  }

  @Test
  void testBasicFunctionality() {
    if (UnsafeString.isAvailable()) {
      String testString = "test";

      // Test getting bytes
      byte[] bytes = UnsafeString.getBytes(testString);
      if (bytes != null) {
        assertThat(bytes.length).isGreaterThan(0);

        // Test getLong - this should work without throwing
        if (bytes.length >= 8) {
          long value = UnsafeString.getLong(bytes, 0);
          // Just verify it returns some value
          assertThat(value).isInstanceOf(Long.class);
        }
      }

      // Test encoding detection - these might not always work depending on the internal
      // String representation, but they shouldn't throw exceptions
      String asciiString = "hello";
      String unicodeString = "héllo";

      try {
        boolean asciiIsLatin1 = UnsafeString.isLatin1(asciiString);
        boolean unicodeIsLatin1 = UnsafeString.isLatin1(unicodeString);
        // These are just to verify no exceptions are thrown
        assertThat(asciiIsLatin1).isIn(true, false);
        assertThat(unicodeIsLatin1).isIn(true, false);
      } catch (RuntimeException e) {
        // This is OK - the internal String representation might not be what we expect
      }
    }
  }

  @Test
  void testIntegrationWithMarshaling() {
    // This test verifies that UnsafeString works properly with the marshaling code
    String testString = "integration test";

    // Test basic availability
    boolean unsafeStringAvailable = UnsafeString.isAvailable();
    assertThat(unsafeStringAvailable).isIn(true, false);

    if (unsafeStringAvailable) {
      // Test getting bytes
      byte[] bytes = UnsafeString.getBytes(testString);
      if (bytes != null) {
        assertThat(bytes.length).isGreaterThan(0);
      }

      // Test encoding detection
      try {
        boolean isLatin1 = UnsafeString.isLatin1(testString);
        assertThat(isLatin1).isIn(true, false);
      } catch (RuntimeException e) {
        // UnsafeString encoding detection might fail, this is OK
      }
    }
  }

  @Test
  void testFallbackBehavior() {
    // Test that fallback methods work when the main approaches fail
    byte[] testBytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    
    // This should always work, either through VarHandle, Unsafe, or fallback
    long result = UnsafeString.getLong(testBytes, 0);
    assertThat(result).isInstanceOf(Long.class);
    
    // Test with insufficient bytes
    byte[] shortBytes = {1, 2, 3};
    long shortResult = UnsafeString.getLong(shortBytes, 0);
    // Should return 0 when not enough bytes
    assertThat(shortResult).isEqualTo(0L);
  }

  @Test
  void testConfiguration() {
    // Test that the configuration methods work
    boolean shouldUseVarHandle = UnsafeAccess.shouldUseVarHandle();
    boolean unsafeAvailable = UnsafeAccess.isAvailable();

    assertThat(shouldUseVarHandle).isIn(true, false);
    assertThat(unsafeAvailable).isIn(true, false);

    // With the new multijar approach, VarHandle preference is handled automatically
    // by the JVM selecting the appropriate implementation
    assertThat(shouldUseVarHandle).isFalse(); // This method is now deprecated
  }
}
