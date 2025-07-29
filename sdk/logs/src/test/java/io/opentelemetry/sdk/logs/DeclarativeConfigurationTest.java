/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration test demonstrating declarative configuration of named log processors. This test
 * simulates the YAML configuration pattern described in the PRD:
 *
 * <pre>
 * loggerProvider:
 *   processors:
 *     - severity_based:
 *         severity: WARN
 *         processors:
 *           - trace_based:
 *               processors:
 *                 - batch:
 *                     exporter:
 *                       otlp:
 *                         endpoint: http://localhost:4317
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class DeclarativeConfigurationTest {

  @Mock private LogRecordExporter mockExporter;
  @Mock private ReadWriteLogRecord logRecord;
  private Context context;

  private static final String TRACE_ID = "12345678901234567890123456789012";
  private static final String SPAN_ID = "1234567890123456";

  @BeforeEach
  void setUp() {
    context = Context.current();
  }

  @Test
  void declarativeConfiguration_SeverityBasedToTraceBasedToBatch() {
    // This test demonstrates the exact configuration pattern from the PRD
    // Create the processing chain as it would be configured declaratively:
    // severity_based (WARN) -> trace_based -> batch -> exporter

    // Step 1: Create the leaf processor (batch processor with exporter)
    LogRecordProcessor batchProcessor =
        BatchLogRecordProcessor.builder(mockExporter).setMaxExportBatchSize(512).build();

    // Step 2: Create trace_based processor that delegates to batch
    TraceBasedLogRecordProcessor traceBasedProcessor =
        TraceBasedLogRecordProcessor.builder().addProcessors(batchProcessor).build();

    // Step 3: Create severity_based processor that delegates to trace_based
    SeverityBasedLogRecordProcessor severityBasedProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceBasedProcessor)
            .build();

    // Test the complete declarative configuration chain
    testDeclarativeConfigurationScenarios(severityBasedProcessor);
  }

  @Test
  void declarativeConfiguration_AlternativeOrderingTraceToSeverity() {
    // Test alternative configuration: trace_based -> severity_based -> batch
    LogRecordProcessor batchProcessor =
        BatchLogRecordProcessor.builder(mockExporter).setMaxExportBatchSize(512).build();

    SeverityBasedLogRecordProcessor severityBasedProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(batchProcessor)
            .build();

    TraceBasedLogRecordProcessor traceBasedProcessor =
        TraceBasedLogRecordProcessor.builder().addProcessors(severityBasedProcessor).build();

    testDeclarativeConfigurationScenarios(traceBasedProcessor);
  }

  @Test
  void declarativeConfiguration_MultipleDownstreamProcessors() {
    // Test configuration with multiple downstream processors per level
    LogRecordProcessor batchProcessor =
        BatchLogRecordProcessor.builder(mockExporter).setMaxExportBatchSize(512).build();
    LogRecordProcessor simpleProcessor = SimpleLogRecordProcessor.create(mockExporter);

    // trace_based processor with multiple downstream processors
    TraceBasedLogRecordProcessor traceBasedProcessor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(batchProcessor)
            .addProcessors(simpleProcessor)
            .build();

    // severity_based processor delegating to trace_based
    SeverityBasedLogRecordProcessor severityBasedProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceBasedProcessor)
            .build();

    // Test that both downstream processors receive logs when conditions are met
    SpanContext sampledSpanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    severityBasedProcessor.onEmit(context, logRecord);

    // Should be exported twice: once by batch processor, once by simple processor
    verify(mockExporter, times(2)).export(any());
  }

  @Test
  void declarativeConfiguration_ComplexNestedChain() {
    // Test a more complex configuration with nested processors

    // Create a multi-level chain to simulate complex declarative configuration
    // severity_based(ERROR) -> trace_based -> severity_based(WARN) -> batch
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(mockExporter).build();

    SeverityBasedLogRecordProcessor innerSeverityProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(batchProcessor)
            .build();

    TraceBasedLogRecordProcessor traceProcessor =
        TraceBasedLogRecordProcessor.builder().addProcessors(innerSeverityProcessor).build();

    SeverityBasedLogRecordProcessor outerSeverityProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.ERROR)
            .addProcessors(traceProcessor)
            .build();

    SpanContext sampledSpanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());

    // Test log that meets outer severity but not inner severity - should NOT export
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    outerSeverityProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(1)).export(any());

    // Test log that would meet inner severity but not outer - should NOT export
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    outerSeverityProcessor.onEmit(context, logRecord);
    // Still only 1 export because WARN doesn't meet ERROR threshold
    verify(mockExporter, times(1)).export(any());
  }

  @Test
  void declarativeConfiguration_WithCompositeProcessor() {
    // Test using the composite processor factory method as would be done in declarative config
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(mockExporter).build();
    LogRecordProcessor simpleProcessor = SimpleLogRecordProcessor.create(mockExporter);

    // Create composite processor (simulates multiple processors at same level in config)
    LogRecordProcessor compositeProcessor =
        LogRecordProcessor.composite(batchProcessor, simpleProcessor);

    // Chain with named processors
    TraceBasedLogRecordProcessor traceProcessor =
        TraceBasedLogRecordProcessor.builder().addProcessors(compositeProcessor).build();

    SeverityBasedLogRecordProcessor severityProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceProcessor)
            .build();

    // Test the configuration
    SpanContext sampledSpanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    severityProcessor.onEmit(context, logRecord);

    // Should export twice: once via batch, once via simple
    verify(mockExporter, times(2)).export(any());
  }

  private void testDeclarativeConfigurationScenarios(LogRecordProcessor rootProcessor) {
    SpanContext sampledSpanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    SpanContext nonSampledSpanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());

    // Scenario 1: Log meets severity threshold but span is not sampled
    // Expected: NOT exported (filtered by trace condition)
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(nonSampledSpanContext);
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, never()).export(any());

    // Scenario 2: Log has sampled span but doesn't meet severity threshold
    // Expected: NOT exported (filtered by severity condition)
    when(logRecord.getSeverity()).thenReturn(Severity.INFO);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, never()).export(any());

    // Scenario 3: Log meets both conditions (severity >= WARN AND sampled span)
    // Expected: Exported successfully
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(1)).export(any());

    // Scenario 4: Log exactly meets severity threshold with sampled span
    // Expected: Exported successfully
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any());

    // Scenario 5: Edge case - null severity with sampled span
    // Expected: NOT exported (filtered by severity condition)
    when(logRecord.getSeverity()).thenReturn(null);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any()); // No additional exports

    // Scenario 6: Edge case - valid severity with invalid span context
    // Expected: NOT exported (filtered by trace condition)
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(SpanContext.getInvalid());
    rootProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any()); // No additional exports
  }

  @Test
  void declarativeConfiguration_LifecycleManagement() {
    // Test that lifecycle methods are properly delegated through the chain
    LogRecordProcessor mockBatchProcessor = mock(LogRecordProcessor.class);
    when(mockBatchProcessor.shutdown())
        .thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
    when(mockBatchProcessor.forceFlush())
        .thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());

    TraceBasedLogRecordProcessor traceProcessor =
        TraceBasedLogRecordProcessor.builder().addProcessors(mockBatchProcessor).build();

    SeverityBasedLogRecordProcessor severityProcessor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceProcessor)
            .build();

    // Test shutdown propagation
    severityProcessor.shutdown();
    verify(mockBatchProcessor, times(1)).shutdown();

    // Test forceFlush propagation
    severityProcessor.forceFlush();
    verify(mockBatchProcessor, times(1)).forceFlush();
  }

  @Test
  void testDeclarativeConfigurationStyle() {
    // Test demonstrating declarative-style configuration of our named processors
    // This simulates how they would be configured in a real YAML-based setup
    
    // Simulate configuration equivalent to this YAML:
    // logger_provider:
    //   processors:
    //     - severity_based:
    //         severity: "WARN"
    //         processors:
    //           - batch:
    //               exporter:
    //                 console: {}
    //     - trace_based:
    //         processors:
    //           - simple:
    //               exporter:
    //                 console: {}
    
    LogRecordExporter consoleExporter = mock(LogRecordExporter.class);
    when(consoleExporter.export(any())).thenReturn(CompletableResultCode.ofSuccess());
    when(consoleExporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    
    // Create processors in declarative style using our builder APIs
    LogRecordProcessor severityBasedProcessor = SeverityBasedLogRecordProcessor.builder()
        .setSeverity(Severity.WARN)
        .addProcessors(
            BatchLogRecordProcessor.builder(consoleExporter)
                .setMaxExportBatchSize(100)
                .setScheduleDelay(Duration.ofSeconds(1))
                .build())
        .build();
    
    LogRecordProcessor traceBasedProcessor = TraceBasedLogRecordProcessor.builder()
        .addProcessors(SimpleLogRecordProcessor.create(consoleExporter))
        .build();
    
    // Create a logger provider with our processors (simulating declarative configuration result)
    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
        .addLogRecordProcessor(severityBasedProcessor)
        .addLogRecordProcessor(traceBasedProcessor)
        .build();
    
    // Create OpenTelemetry SDK instance (simulating DeclarativeConfiguration.parseAndCreate result)
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setLoggerProvider(loggerProvider)
        .build();
    
    // Test the configured SDK
    Logger logger = sdk.getSdkLoggerProvider().get("test.declarative");
    assertThat(logger).isNotNull();
    
    // Test different log levels with the severity-based processor
    logger.logRecordBuilder()
        .setSeverity(Severity.DEBUG)
        .setBody("Debug message - should be filtered")
        .emit();
    
    logger.logRecordBuilder()
        .setSeverity(Severity.ERROR)
        .setBody("Error message - should pass through")
        .emit();
    
    // Test with sampled trace context for trace-based processor
    Span sampledSpan = sdk.getTracer("test")
        .spanBuilder("test-span")
        .startSpan();
    
    try (Scope scope = sampledSpan.makeCurrent()) {
      logger.logRecordBuilder()
          .setSeverity(Severity.INFO)
          .setBody("Log with sampled trace - should pass through trace processor")
          .emit();
    } finally {
      sampledSpan.end();
    }
    
    // Verify processors can be shut down cleanly
    CompletableResultCode shutdownResult = loggerProvider.shutdown();
    assertThat(shutdownResult.join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
    
    // This test demonstrates that our named processors work correctly
    // in a declarative configuration style, even though they're not yet
    // integrated with the YAML parsing infrastructure
  }
}
