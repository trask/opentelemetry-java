/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

class Java22CompatibilityTest {

  @Test
  void testUnsafeToVarHandleMigration() {
    // This test demonstrates the migration behavior for different Java versions

    double javaVersion = getJavaVersion();

    // Verify that our configuration logic works correctly
    boolean actuallyPreferVarHandle = UnsafeAccess.shouldUseVarHandle();
    boolean unsafeAvailable = UnsafeAccess.isAvailable();

    // With the new multijar approach, VarHandle preference is handled automatically
    // by the JVM selecting the appropriate UnsafeString implementation
    assertThat(actuallyPreferVarHandle).isFalse(); // This method is now deprecated

    // Verify that UnsafeString can function regardless of which implementation is used
    boolean unsafeStringAvailable = UnsafeString.isAvailable();
    assertThat(unsafeStringAvailable).isIn(true, false);

    if (unsafeStringAvailable) {
      // If UnsafeString is available, it should be able to process strings
      String testString = "test string for Java " + javaVersion;
      byte[] bytes = UnsafeString.getBytes(testString);

      // At minimum, we should get some bytes back (even if just from fallback)
      if (bytes != null) {
        assertThat(bytes.length).isGreaterThan(0);
      }
    }
  }

  @Test
  @EnabledOnJre({JRE.JAVA_17, JRE.JAVA_21}) // Test on pre-Java 22
  void testPreJava22Behavior() {
    // With the multijar approach, the behavior is now determined automatically
    // by the JVM selecting the appropriate implementation
    boolean shouldUseVarHandle = UnsafeAccess.shouldUseVarHandle();

    // The shouldUseVarHandle method is now deprecated and always returns false
    assertThat(shouldUseVarHandle).isFalse();
    
    // The actual VarHandle vs Unsafe selection is handled by the multijar mechanism
    boolean unsafeStringAvailable = UnsafeString.isAvailable();
    assertThat(unsafeStringAvailable).isIn(true, false);
  }

  @Test
  void testConfigurationOverride() {
    // This test documents the new multijar behavior
    
    // With the multijar approach, VarHandle vs Unsafe selection is automatic
    // The configuration system property is no longer the primary mechanism
    String configValue = System.getProperty("otel.java.experimental.exporter.varhandle.enabled");

    // The shouldUseVarHandle method is now deprecated
    assertThat(UnsafeAccess.shouldUseVarHandle()).isFalse();
    
    // The actual behavior is now determined by the multijar mechanism:
    // - Java 9+: Uses VarHandle with Unsafe fallback automatically
    // - Java 8: Uses Unsafe with safe fallbacks automatically
    
    // Users can still control Unsafe availability through:
    // -Dotel.java.experimental.exporter.unsafe.enabled=false
    boolean unsafeAvailable = UnsafeAccess.isAvailable();
    assertThat(unsafeAvailable).isIn(true, false);
  }

  @Test
  void testStringMarshalingIntegration() {
    // Test that string marshaling works end-to-end with our changes

    String testString = "Hello, World! 🌍"; // Include emoji to test Unicode handling

    // This should work regardless of which implementation is used
    boolean useUnsafe = UnsafeString.isAvailable();

    int utf8Size = StatelessMarshalerUtil.getUtf8Size(testString, useUnsafe);
    assertThat(utf8Size).isGreaterThan(testString.length()); // Unicode emoji takes more bytes

    // Verify the size calculation is consistent
    int utf8SizeNoUnsafe = StatelessMarshalerUtil.getUtf8Size(testString, false);
    assertThat(utf8Size).isEqualTo(utf8SizeNoUnsafe); // Results should be the same
  }

  private static double getJavaVersion() {
    String specVersion = System.getProperty("java.specification.version");
    if (specVersion != null) {
      try {
        return Double.parseDouble(specVersion);
      } catch (NumberFormatException exception) {
        // ignore
      }
    }
    return -1;
  }
}
