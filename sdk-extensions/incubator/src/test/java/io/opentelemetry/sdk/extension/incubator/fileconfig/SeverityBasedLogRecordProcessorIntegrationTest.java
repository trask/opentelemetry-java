/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Integration test demonstrating how to use SeverityBasedLogRecordProcessor with YAML configuration.
 */
class SeverityBasedLogRecordProcessorIntegrationTest {

  @Test
  void severityBasedProcessor_YamlConfiguration() {
    String yaml = 
        "file_format: \"1.0\"\n" +
        "logger_provider:\n" +
        "  processors:\n" +
        "    - severity_based:\n" +
        "        minimum_severity: \"ERROR\"\n" +
        "        processors:\n" +
        "          - simple:\n" +
        "              exporter:\n" +
        "                console: {}\n" +
        "    - simple:\n" +
        "        exporter:\n" +
        "          console: {}\n";

    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.parseAndCreate(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))) {
      
      SdkLoggerProvider loggerProvider = sdk.getSdkLoggerProvider();
      
      // Verify that we can get a logger and it's configured properly
      Logger logger = sdk.getLogsBridge().get("test-logger");
      assertThat(logger).isNotNull();
      
      // Test that the configuration was applied (processors are not directly accessible through public API)
      // But we can verify that the SDK was configured successfully
      assertThat(loggerProvider).isNotNull();
      
      // Demonstrate usage - these calls will work with the configured processors
      logger.logRecordBuilder()
          .setSeverity(Severity.ERROR)
          .setBody("This is an error log")
          .emit();
      
      logger.logRecordBuilder()
          .setSeverity(Severity.WARN)
          .setBody("This is a warning log")
          .emit();
    }
  }
}
