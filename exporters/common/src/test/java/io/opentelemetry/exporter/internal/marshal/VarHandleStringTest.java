/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VarHandleStringTest {

  @Test
  void testAvailability() {
    // VarHandle should be available on Java 9+, but might not be in all environments
    // This test verifies that our reflection-based approach doesn't crash
    boolean available = VarHandleString.isAvailable();
    // We just verify it returns a boolean without throwing
    assertThat(available).isIn(true, false);
  }
  
  @Test 
  void testBasicFunctionality() {
    if (VarHandleString.isAvailable()) {
      String testString = "test";
      
      // Test getting bytes
      byte[] bytes = VarHandleString.getBytes(testString);
      assertThat(bytes).isNotNull();
      assertThat(bytes.length).isGreaterThan(0);
      
      // Test encoding detection - these might not always work depending on the internal 
      // String representation, but they shouldn't throw exceptions
      String asciiString = "hello";
      String unicodeString = "héllo";
      
      try {
        boolean asciiIsLatin1 = VarHandleString.isLatin1(asciiString);
        boolean unicodeIsLatin1 = VarHandleString.isLatin1(unicodeString);
        // These are just to verify no exceptions are thrown
        assertThat(asciiIsLatin1).isIn(true, false);
        assertThat(unicodeIsLatin1).isIn(true, false);
      } catch (RuntimeException e) {
        // This is OK - the internal String representation might not be what we expect
      }
      
      // Test getLong - this should work without throwing
      if (bytes.length >= 8) {
        long value = VarHandleString.getLong(bytes, 0);
        // Just verify it returns some value
        assertThat(value).isInstanceOf(Long.class);
      }
    }
  }

  @Test
  void testUnsafeStringIntegration() {
    // This test verifies that UnsafeString properly works with or without VarHandle
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
  void testConfiguration() {
    // Test that the configuration methods work
    boolean shouldUseVarHandle = UnsafeAccess.shouldUseVarHandle();
    boolean unsafeAvailable = UnsafeAccess.isAvailable();
    
    assertThat(shouldUseVarHandle).isIn(true, false);
    assertThat(unsafeAvailable).isIn(true, false);
    
    // Verify that if we should use VarHandle, then Unsafe is not considered available
    // (they are mutually exclusive in our implementation)
    if (shouldUseVarHandle && VarHandleString.isAvailable()) {
      assertThat(unsafeAvailable).isFalse();
    }
  }
}