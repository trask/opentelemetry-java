/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NamedProcessorIntegrationTest {

  @Test
  void testSeverityBasedProcessorYamlParsing() {
    String yaml =
        "file_format: 1.0\n"
            + "logger_provider:\n"
            + "  processors:\n"
            + "    - severity_based:\n"
            + "        severity: WARN\n";

    InputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    OpenTelemetrySdk sdk = DeclarativeConfiguration.parseAndCreate(inputStream);

    assertThat(sdk).isNotNull();
    // Basic smoke test - if parsing succeeded, the processor was loaded
  }

  @Test
  void testTraceBasedProcessorYamlParsing() {
    String yaml =
        "file_format: 1.0\n"
            + "logger_provider:\n"
            + "  processors:\n"
            + "    - trace_based: {}\n";

    InputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    OpenTelemetrySdk sdk = DeclarativeConfiguration.parseAndCreate(inputStream);

    assertThat(sdk).isNotNull();
    // Basic smoke test - if parsing succeeded, the processor was loaded
  }

  @Test
  void testNestedProcessorConfiguration() {
    String yaml =
        "file_format: 1.0\n"
            + "logger_provider:\n"
            + "  processors:\n"
            + "    - severity_based:\n"
            + "        severity: INFO\n"
            + "        processors:\n"
            + "          - trace_based: {}\n";

    InputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    OpenTelemetrySdk sdk = DeclarativeConfiguration.parseAndCreate(inputStream);

    assertThat(sdk).isNotNull();
    // Basic smoke test - if parsing succeeded, the nested processor configuration worked
  }
}
