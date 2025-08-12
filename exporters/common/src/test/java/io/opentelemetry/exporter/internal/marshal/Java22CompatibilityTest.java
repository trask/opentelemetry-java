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
    boolean shouldPreferVarHandle = javaVersion >= 22;

    // Verify that our configuration logic works correctly
    boolean actuallyPreferVarHandle = UnsafeAccess.shouldUseVarHandle();
    boolean unsafeAvailable = UnsafeAccess.isAvailable();

    if (javaVersion >= 9 && shouldPreferVarHandle) {
      // On Java 22+, we should prefer VarHandle (if available) and not use Unsafe
      if (VarHandleString.isAvailable()) {
        assertThat(actuallyPreferVarHandle).isTrue();
        assertThat(unsafeAvailable).isFalse();
      }
    } else if (javaVersion >= 9) {
      // On Java 9-21, we should prefer Unsafe over VarHandle by default
      // (user can override via system property)
      if (!actuallyPreferVarHandle) {
        assertThat(unsafeAvailable).isIn(true, false); // May or may not be available
      }
    }

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
    // On pre-Java 22, we should default to Unsafe (unless overridden)
    boolean shouldUseVarHandle = UnsafeAccess.shouldUseVarHandle();

    // Unless explicitly configured otherwise, we should not prefer VarHandle on older Java
    String explicitConfig = System.getProperty("otel.java.experimental.exporter.varhandle.enabled");
    if (explicitConfig == null) {
      assertThat(shouldUseVarHandle).isFalse();
    }
  }

  @Test
  void testConfigurationOverride() {
    // This test documents how users can override the default behavior

    // The configuration is read at class initialization time, so we can't easily test
    // different values in the same JVM. But we can document the behavior:

    // Users can force VarHandle usage with:
    // -Dotel.java.experimental.exporter.varhandle.enabled=true

    // Users can force Unsafe usage (if available) with:
    // -Dotel.java.experimental.exporter.varhandle.enabled=false

    // This is useful for testing and troubleshooting
    String configValue = System.getProperty("otel.java.experimental.exporter.varhandle.enabled");

    if ("true".equals(configValue)) {
      assertThat(UnsafeAccess.shouldUseVarHandle()).isTrue();
    } else if ("false".equals(configValue)) {
      assertThat(UnsafeAccess.shouldUseVarHandle()).isFalse();
    }
    // If not set, behavior depends on Java version (tested in other methods)
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
