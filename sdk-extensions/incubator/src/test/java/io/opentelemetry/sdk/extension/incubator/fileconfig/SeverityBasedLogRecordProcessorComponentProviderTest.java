/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SeverityBasedLogRecordProcessor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SeverityBasedLogRecordProcessorComponentProviderTest {

  @Test
  void createSeverityBasedProcessor_DirectComponentProvider() {
    SeverityBasedLogRecordProcessorComponentProvider provider = 
        new SeverityBasedLogRecordProcessorComponentProvider();
    
    assertThat(provider.getType()).isEqualTo(LogRecordProcessor.class);
    assertThat(provider.getName()).isEqualTo("severity_based");
  }
  
  @Test
  void createSeverityBasedProcessor_ValidConfig() {
    String yaml = 
        "minimum_severity: \"WARN\"\n" +
        "processors:\n" +
        "  - simple:\n" +
        "      exporter:\n" +
        "        console: {}\n";

    Object yamlObj = DeclarativeConfiguration.loadYaml(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), 
        Collections.emptyMap());
        
    DeclarativeConfigProperties config = 
        DeclarativeConfiguration.toConfigProperties(yamlObj, 
            ComponentLoader.forClassLoader(getClass().getClassLoader()));

    SeverityBasedLogRecordProcessorComponentProvider provider = 
        new SeverityBasedLogRecordProcessorComponentProvider();
    
    LogRecordProcessor processor = provider.create(config);

    assertThat(processor).isInstanceOf(SeverityBasedLogRecordProcessor.class);
    SeverityBasedLogRecordProcessor severityProcessor = (SeverityBasedLogRecordProcessor) processor;
    assertThat(severityProcessor.getMinimumSeverity()).isEqualTo(Severity.WARN);
    assertThat(severityProcessor.getProcessors()).hasSize(1);
  }

  @Test
  void createSeverityBasedProcessor_MissingMinimumSeverity() {
    String yaml = 
        "processors:\n" +
        "  - simple:\n" +
        "      exporter:\n" +
        "        console: {}\n";

    Object yamlObj = DeclarativeConfiguration.loadYaml(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), 
        Collections.emptyMap());
        
    DeclarativeConfigProperties config = 
        DeclarativeConfiguration.toConfigProperties(yamlObj, 
            ComponentLoader.forClassLoader(getClass().getClassLoader()));

    SeverityBasedLogRecordProcessorComponentProvider provider = 
        new SeverityBasedLogRecordProcessorComponentProvider();
    
    assertThatThrownBy(() -> provider.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("minimum_severity is required for severity_based log processor");
  }

  @Test
  void createSeverityBasedProcessor_InvalidSeverity() {
    String yaml = 
        "minimum_severity: \"INVALID\"\n" +
        "processors:\n" +
        "  - simple:\n" +
        "      exporter:\n" +
        "        console: {}\n";

    Object yamlObj = DeclarativeConfiguration.loadYaml(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), 
        Collections.emptyMap());
        
    DeclarativeConfigProperties config = 
        DeclarativeConfiguration.toConfigProperties(yamlObj, 
            ComponentLoader.forClassLoader(getClass().getClassLoader()));

    SeverityBasedLogRecordProcessorComponentProvider provider = 
        new SeverityBasedLogRecordProcessorComponentProvider();
    
    assertThatThrownBy(() -> provider.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid severity value: INVALID");
  }
}
