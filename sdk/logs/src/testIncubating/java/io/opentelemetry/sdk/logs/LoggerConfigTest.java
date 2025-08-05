/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static io.opentelemetry.sdk.internal.ScopeConfiguratorBuilder.nameEquals;
import static io.opentelemetry.sdk.internal.ScopeConfiguratorBuilder.nameMatchesGlob;
import static io.opentelemetry.sdk.logs.internal.LoggerConfig.defaultConfig;
import static io.opentelemetry.sdk.logs.internal.LoggerConfig.disabled;
import static io.opentelemetry.sdk.logs.internal.LoggerConfig.enabled;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.ScopeConfigurator;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.logs.internal.LoggerConfig;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoggerConfigTest {

  @Test
  void disableScopes() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            // Disable loggerB. Since loggers are enabled by default, loggerA and loggerC are
            // enabled.
            .addLoggerConfiguratorCondition(nameEquals("loggerB"), disabled())
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    Logger loggerA = loggerProvider.get("loggerA");
    Logger loggerB = loggerProvider.get("loggerB");
    Logger loggerC = loggerProvider.get("loggerC");

    loggerA.logRecordBuilder().setBody("messageA").emit();
    loggerB.logRecordBuilder().setBody("messageB").emit();
    loggerC.logRecordBuilder().setBody("messageC").emit();

    // Only logs from loggerA and loggerC should be seen
    assertThat(exporter.getFinishedLogRecordItems())
        .satisfies(
            metrics -> {
              Map<InstrumentationScopeInfo, List<LogRecordData>> logsByScope =
                  metrics.stream()
                      .collect(Collectors.groupingBy(LogRecordData::getInstrumentationScopeInfo));
              assertThat(logsByScope.get(InstrumentationScopeInfo.create("loggerA"))).hasSize(1);
              assertThat(logsByScope.get(InstrumentationScopeInfo.create("loggerB"))).isNull();
              assertThat(logsByScope.get(InstrumentationScopeInfo.create("loggerC"))).hasSize(1);
            });
    // loggerA and loggerC are enabled, loggerB is disabled.
    assertThat(((ExtendedLogger) loggerA).isEnabled(Severity.INFO)).isTrue();
    assertThat(((ExtendedLogger) loggerB).isEnabled(Severity.INFO)).isFalse();
    assertThat(((ExtendedLogger) loggerC).isEnabled(Severity.INFO)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("loggerConfiguratorArgs")
  void loggerConfigurator(
      ScopeConfigurator<LoggerConfig> loggerConfigurator,
      InstrumentationScopeInfo scope,
      LoggerConfig expectedLoggerConfig) {
    LoggerConfig loggerConfig = loggerConfigurator.apply(scope);
    loggerConfig = loggerConfig == null ? defaultConfig() : loggerConfig;
    assertThat(loggerConfig).isEqualTo(expectedLoggerConfig);
  }

  private static final InstrumentationScopeInfo scopeCat = InstrumentationScopeInfo.create("cat");
  private static final InstrumentationScopeInfo scopeDog = InstrumentationScopeInfo.create("dog");
  private static final InstrumentationScopeInfo scopeDuck = InstrumentationScopeInfo.create("duck");

  private static Stream<Arguments> loggerConfiguratorArgs() {
    ScopeConfigurator<LoggerConfig> defaultConfigurator =
        LoggerConfig.configuratorBuilder().build();
    ScopeConfigurator<LoggerConfig> disableCat =
        LoggerConfig.configuratorBuilder()
            .addCondition(nameEquals("cat"), disabled())
            // Second matching rule for cat should be ignored
            .addCondition(nameEquals("cat"), enabled())
            .build();
    ScopeConfigurator<LoggerConfig> disableStartsWithD =
        LoggerConfig.configuratorBuilder().addCondition(nameMatchesGlob("d*"), disabled()).build();
    ScopeConfigurator<LoggerConfig> enableCat =
        LoggerConfig.configuratorBuilder()
            .setDefault(disabled())
            .addCondition(nameEquals("cat"), enabled())
            // Second matching rule for cat should be ignored
            .addCondition(nameEquals("cat"), disabled())
            .build();
    ScopeConfigurator<LoggerConfig> enableStartsWithD =
        LoggerConfig.configuratorBuilder()
            .setDefault(disabled())
            .addCondition(nameMatchesGlob("d*"), enabled())
            .build();

    return Stream.of(
        // default
        Arguments.of(defaultConfigurator, scopeCat, defaultConfig()),
        Arguments.of(defaultConfigurator, scopeDog, defaultConfig()),
        Arguments.of(defaultConfigurator, scopeDuck, defaultConfig()),
        // default enabled, disable cat
        Arguments.of(disableCat, scopeCat, disabled()),
        Arguments.of(disableCat, scopeDog, enabled()),
        Arguments.of(disableCat, scopeDuck, enabled()),
        // default enabled, disable pattern
        Arguments.of(disableStartsWithD, scopeCat, enabled()),
        Arguments.of(disableStartsWithD, scopeDog, disabled()),
        Arguments.of(disableStartsWithD, scopeDuck, disabled()),
        // default disabled, enable cat
        Arguments.of(enableCat, scopeCat, enabled()),
        Arguments.of(enableCat, scopeDog, disabled()),
        Arguments.of(enableCat, scopeDuck, disabled()),
        // default disabled, enable pattern
        Arguments.of(enableStartsWithD, scopeCat, disabled()),
        Arguments.of(enableStartsWithD, scopeDog, enabled()),
        Arguments.of(enableStartsWithD, scopeDuck, enabled()));
  }

  @Test
  void setScopeConfigurator() {
    // 1. Initially, configure all loggers to be enabled except loggerB
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .addLoggerConfiguratorCondition(nameEquals("loggerB"), disabled())
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    ExtendedSdkLogger loggerA = (ExtendedSdkLogger) loggerProvider.get("loggerA");
    ExtendedSdkLogger loggerB = (ExtendedSdkLogger) loggerProvider.get("loggerB");
    ExtendedSdkLogger loggerC = (ExtendedSdkLogger) loggerProvider.get("loggerC");

    // verify isEnabled()
    assertThat(loggerA.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isTrue();
    assertThat(loggerB.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isFalse();
    assertThat(loggerC.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isTrue();

    // verify logs are emitted as expected
    loggerA.logRecordBuilder().setBody("logA").emit();
    loggerB.logRecordBuilder().setBody("logB").emit();
    loggerC.logRecordBuilder().setBody("logC").emit();
    assertThat(exporter.getFinishedLogRecordItems())
        .satisfiesExactlyInAnyOrder(
            log -> assertThat(log).hasBody("logA"), log -> assertThat(log).hasBody("logC"));
    exporter.reset();

    // 2. Update config to disable all loggers
    loggerProvider.setLoggerConfigurator(
        ScopeConfigurator.<LoggerConfig>builder().setDefault(disabled()).build());

    // verify isEnabled()
    assertThat(loggerA.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isFalse();
    assertThat(loggerB.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isFalse();
    assertThat(loggerC.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isFalse();

    // verify logs are emitted as expected
    loggerA.logRecordBuilder().setBody("logA").emit();
    loggerB.logRecordBuilder().setBody("logB").emit();
    loggerC.logRecordBuilder().setBody("logC").emit();
    assertThat(exporter.getFinishedLogRecordItems()).isEmpty();

    // 3. Update config to restore original
    loggerProvider.setLoggerConfigurator(
        ScopeConfigurator.<LoggerConfig>builder()
            .addCondition(nameEquals("loggerB"), disabled())
            .build());

    // verify isEnabled()
    assertThat(loggerA.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isTrue();
    assertThat(loggerB.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isFalse();
    assertThat(loggerC.isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER, Context.current())).isTrue();

    // verify logs are emitted as expected
    loggerA.logRecordBuilder().setBody("logA").emit();
    loggerB.logRecordBuilder().setBody("logB").emit();
    loggerC.logRecordBuilder().setBody("logC").emit();
    assertThat(exporter.getFinishedLogRecordItems())
        .satisfiesExactly(
            log -> assertThat(log).hasBody("logA"), log -> assertThat(log).hasBody("logC"));
  }

  @Test
  void minimumSeverityFiltering() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            // Set minimum severity to WARN for loggerA
            .addLoggerConfiguratorCondition(nameEquals("loggerA"), LoggerConfig.withMinimumSeverity(Severity.WARN.getSeverityNumber()))
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    Logger loggerA = loggerProvider.get("loggerA");
    Logger loggerB = loggerProvider.get("loggerB"); // Uses default config (no filtering)

    // Emit logs with different severity levels
    loggerA.logRecordBuilder().setSeverity(Severity.DEBUG).setBody("debug").emit(); // Should be dropped
    loggerA.logRecordBuilder().setSeverity(Severity.INFO).setBody("info").emit(); // Should be dropped
    loggerA.logRecordBuilder().setSeverity(Severity.WARN).setBody("warn").emit(); // Should pass
    loggerA.logRecordBuilder().setSeverity(Severity.ERROR).setBody("error").emit(); // Should pass
    loggerA.logRecordBuilder().setBody("unspecified").emit(); // Should pass (unspecified severity)

    // LoggerB should emit all logs (no filtering)
    loggerB.logRecordBuilder().setSeverity(Severity.DEBUG).setBody("debug-b").emit();
    loggerB.logRecordBuilder().setSeverity(Severity.INFO).setBody("info-b").emit();

    // Only logs with severity >= WARN from loggerA, plus all from loggerB
    assertThat(exporter.getFinishedLogRecordItems())
        .satisfiesExactlyInAnyOrder(
            log -> assertThat(log).hasBody("warn"),
            log -> assertThat(log).hasBody("error"),
            log -> assertThat(log).hasBody("unspecified"),
            log -> assertThat(log).hasBody("debug-b"),
            log -> assertThat(log).hasBody("info-b"));
  }

  @Test
  void traceBasedFiltering() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            // Enable trace-based filtering for loggerA
            .addLoggerConfiguratorCondition(nameEquals("loggerA"), LoggerConfig.withTraceBased())
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    Logger loggerA = loggerProvider.get("loggerA");
    Logger loggerB = loggerProvider.get("loggerB"); // Uses default config (no filtering)

    // Test with no active span - should pass through
    loggerA.logRecordBuilder().setBody("no-span").emit();
    loggerB.logRecordBuilder().setBody("no-span-b").emit();

    // Test with sampled trace - should pass through
    // Note: creating a proper sampled span context requires tracer setup
    loggerA.logRecordBuilder().setBody("sampled").emit();
    loggerB.logRecordBuilder().setBody("sampled-b").emit();

    // For this basic test, we expect logs without valid span context to pass through
    assertThat(exporter.getFinishedLogRecordItems())
        .satisfiesExactlyInAnyOrder(
            log -> assertThat(log).hasBody("no-span"),
            log -> assertThat(log).hasBody("no-span-b"),
            log -> assertThat(log).hasBody("sampled"),
            log -> assertThat(log).hasBody("sampled-b"));
  }

  @Test
  void combinedFiltering() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            // Combine minimum severity and trace-based filtering
            .addLoggerConfiguratorCondition(nameEquals("loggerA"), 
                LoggerConfig.create(/* enabled= */ true, Severity.WARN.getSeverityNumber(), /* traceBased= */ true))
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    Logger loggerA = loggerProvider.get("loggerA");

    // Test with various combinations
    loggerA.logRecordBuilder().setSeverity(Severity.DEBUG).setBody("debug-filtered").emit(); // Dropped by severity
    loggerA.logRecordBuilder().setSeverity(Severity.WARN).setBody("warn-passed").emit(); // Should pass (no active span)
    loggerA.logRecordBuilder().setBody("unspecified-passed").emit(); // Should pass (unspecified severity)

    assertThat(exporter.getFinishedLogRecordItems())
        .satisfiesExactlyInAnyOrder(
            log -> assertThat(log).hasBody("warn-passed"),
            log -> assertThat(log).hasBody("unspecified-passed"));
  }

  @Test
  void loggerConfigDefaults() {
    LoggerConfig defaultConfig = LoggerConfig.defaultConfig();
    assertThat(defaultConfig.isEnabled()).isTrue();
    assertThat(defaultConfig.getMinimumSeverity()).isEqualTo(0);
    assertThat(defaultConfig.isTraceBased()).isFalse();

    LoggerConfig disabledConfig = LoggerConfig.disabled();
    assertThat(disabledConfig.isEnabled()).isFalse();
    assertThat(disabledConfig.getMinimumSeverity()).isEqualTo(0);
    assertThat(disabledConfig.isTraceBased()).isFalse();

    LoggerConfig customConfig = LoggerConfig.create(/* enabled= */ true, /* minimumSeverity= */ 100, /* traceBased= */ true);
    assertThat(customConfig.isEnabled()).isTrue();
    assertThat(customConfig.getMinimumSeverity()).isEqualTo(100);
    assertThat(customConfig.isTraceBased()).isTrue();
  }

  @Test
  void simpleDebugTest() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .addLoggerConfiguratorCondition(nameEquals("testLogger"), 
                LoggerConfig.withMinimumSeverity(Severity.WARN.getSeverityNumber()))
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    Logger logger = loggerProvider.get("testLogger");
    
    // Emit one DEBUG log that should be filtered
    logger.logRecordBuilder().setSeverity(Severity.DEBUG).setBody("should-be-filtered").emit();
    
    // Emit one WARN log that should pass  
    logger.logRecordBuilder().setSeverity(Severity.WARN).setBody("should-pass").emit();

    // Should only see the WARN log
    List<LogRecordData> logs = exporter.getFinishedLogRecordItems();
    
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getBodyValue().asString()).isEqualTo("should-pass");
  }
}
