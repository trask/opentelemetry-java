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

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration tests demonstrating how the named log processors work together with existing 
 * OpenTelemetry log processors and in complex processing chains.
 */
@ExtendWith(MockitoExtension.class)
class NamedLogProcessorsIntegrationTest {

  @Mock private LogRecordExporter mockExporter;
  @Mock private ReadWriteLogRecord logRecord;
  private Context context;

  private static final String TRACE_ID = "12345678901234567890123456789012";
  private static final String SPAN_ID = "1234567890123456";

  @BeforeEach
  void setUp() {
    context = Context.current();
    when(mockExporter.export(any())).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
  }

  @Test
  void integration_withBatchLogRecordProcessor() {
    // Create a processing chain: SeverityBased -> BatchLogRecordProcessor
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(mockExporter)
        .setMaxExportBatchSize(1) // Force immediate export for testing
        .build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(batchProcessor)
            .build();

    // Test log that meets severity threshold
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    severityProcessor.onEmit(context, logRecord);
    
    // Force flush to ensure batch is processed
    severityProcessor.forceFlush();
    
    // Verify export was called
    verify(mockExporter, times(1)).export(any());
  }

  @Test
  void integration_withSimpleLogRecordProcessor() {
    // Create a processing chain: TraceBased -> SimpleLogRecordProcessor
    LogRecordProcessor simpleProcessor = SimpleLogRecordProcessor.create(mockExporter);
    
    TraceBasedLogRecordProcessor traceProcessor = 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(simpleProcessor)
            .build();

    // Test log with sampled span context
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    traceProcessor.onEmit(context, logRecord);
    
    // Verify immediate export (simple processor exports immediately)
    verify(mockExporter, times(1)).export(any());
  }

  @Test
  void integration_withMultiLogRecordProcessor() {
    // Create a processing chain using MultiLogRecordProcessor
    LogRecordProcessor simpleProcessor1 = SimpleLogRecordProcessor.create(mockExporter);
    LogRecordProcessor simpleProcessor2 = SimpleLogRecordProcessor.create(mockExporter);
    
    LogRecordProcessor multiProcessor = LogRecordProcessor.composite(simpleProcessor1, simpleProcessor2);
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.INFO)
            .addProcessors(multiProcessor)
            .build();

    // Test log that meets severity threshold
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    severityProcessor.onEmit(context, logRecord);
    
    // Should export twice (once per simple processor)
    verify(mockExporter, times(2)).export(any());
  }

  @Test
  void integration_complexChaining_severityToTraceToMulti() {
    // Create complex chain: Severity -> Trace -> Multi(Simple1, Simple2)
    LogRecordProcessor simpleProcessor1 = SimpleLogRecordProcessor.create(mockExporter);
    LogRecordProcessor simpleProcessor2 = SimpleLogRecordProcessor.create(mockExporter);
    LogRecordProcessor multiProcessor = LogRecordProcessor.composite(simpleProcessor1, simpleProcessor2);
    
    TraceBasedLogRecordProcessor traceProcessor = 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(multiProcessor)
            .build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.ERROR)
            .addProcessors(traceProcessor)
            .build();

    // Test various scenarios
    
    // Scenario 1: Meets severity, has sampled span - should export
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any()); // 2 exports from multi processor
    
    // Scenario 2: Meets severity, no sampled span - should NOT export
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    SpanContext nonSampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(nonSampledSpanContext);
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any()); // Still only 2 exports
    
    // Scenario 3: Doesn't meet severity, has sampled span - should NOT export
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, times(2)).export(any()); // Still only 2 exports
  }

  @Test
  void integration_performanceImpact_minimalOverhead() throws InterruptedException {
    // Test that processors don't add significant overhead when conditions aren't met
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(mockExporter).build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.ERROR)
            .addProcessors(batchProcessor)
            .build();

    // Simulate many log records that don't meet the threshold
    when(logRecord.getSeverity()).thenReturn(Severity.DEBUG);
    
    long startTime = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
      severityProcessor.onEmit(context, logRecord);
    }
    long endTime = System.nanoTime();
    
    // Should complete quickly since no processing is done
    long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
    assertThat(durationMs).isLessThan(1000); // Should take less than 1 second
    
    // No exports should have occurred
    verify(mockExporter, never()).export(any());
  }

  @Test
  void integration_lifecycleManagement_propagatesCorrectly() {
    // Create a complex chain to test lifecycle propagation
    LogRecordProcessor mockLeafProcessor1 = mock(LogRecordProcessor.class);
    LogRecordProcessor mockLeafProcessor2 = mock(LogRecordProcessor.class);
    
    when(mockLeafProcessor1.shutdown()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
    when(mockLeafProcessor1.forceFlush()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
    when(mockLeafProcessor2.shutdown()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
    when(mockLeafProcessor2.forceFlush()).thenReturn(io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess());
    
    TraceBasedLogRecordProcessor traceProcessor = 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockLeafProcessor1)
            .addProcessors(mockLeafProcessor2)
            .build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceProcessor)
            .build();

    // Test shutdown propagation
    severityProcessor.shutdown();
    
    verify(mockLeafProcessor1, times(1)).shutdown();
    verify(mockLeafProcessor2, times(1)).shutdown();
    
    // Test forceFlush propagation
    severityProcessor.forceFlush();
    
    verify(mockLeafProcessor1, times(1)).forceFlush();
    verify(mockLeafProcessor2, times(1)).forceFlush();
  }

  @Test
  void integration_withAsyncExporter_handlesBackpressure() throws InterruptedException {
    // Simulate a slow exporter
    CountDownLatch exportLatch = new CountDownLatch(1);
    LogRecordExporter slowExporter = mock(LogRecordExporter.class);
    when(slowExporter.export(any())).thenAnswer(invocation -> {
      try {
        exportLatch.await(100, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
    });
    
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(slowExporter)
        .setMaxExportBatchSize(1)
        .build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(batchProcessor)
            .build();

    // Emit a log that meets criteria
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    
    severityProcessor.onEmit(context, logRecord);
    
    // Release the export
    exportLatch.countDown();
    
    // Force flush to ensure completion
    severityProcessor.forceFlush();
    
    verify(slowExporter, times(1)).export(any());
  }

  @Test
  void integration_edgeCases_handlesGracefully() {
    LogRecordProcessor simpleProcessor = SimpleLogRecordProcessor.create(mockExporter);
    
    // Test with both processors in a chain
    TraceBasedLogRecordProcessor traceProcessor = 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(simpleProcessor)
            .build();
    
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceProcessor)
            .build();

    // Edge case 1: null severity and null span context
    when(logRecord.getSeverity()).thenReturn(null);
    when(logRecord.getSpanContext()).thenReturn(null);
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, never()).export(any());
    
    // Edge case 2: meets severity but invalid span context
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(SpanContext.getInvalid());
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, never()).export(any());
    
    // Edge case 3: doesn't meet severity but valid sampled span
    when(logRecord.getSeverity()).thenReturn(Severity.INFO);
    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    
    severityProcessor.onEmit(context, logRecord);
    verify(mockExporter, never()).export(any());
  }

  @Test
  void integration_realWorldScenario_webApplicationLogging() {
    // Simulate a real-world web application logging scenario
    
    // Create a realistic processing chain for production use
    LogRecordProcessor batchProcessor = BatchLogRecordProcessor.builder(mockExporter)
        .setMaxExportBatchSize(100)
        .build();
    
    // Only process logs for sampled traces (reduces noise)
    TraceBasedLogRecordProcessor traceProcessor = 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(batchProcessor)
            .build();
    
    // Only process WARN and above to reduce volume
    SeverityBasedLogRecordProcessor severityProcessor = 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(traceProcessor)
            .build();

    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    SpanContext nonSampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());

    // Simulate various application logs
    
    // 1. DEBUG log in sampled trace - should be filtered out by severity
    when(logRecord.getSeverity()).thenReturn(Severity.DEBUG);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    severityProcessor.onEmit(context, logRecord);
    
    // 2. ERROR log in non-sampled trace - should be filtered out by trace
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(nonSampledSpanContext);
    severityProcessor.onEmit(context, logRecord);
    
    // 3. WARN log in sampled trace - should pass through
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    severityProcessor.onEmit(context, logRecord);
    
    // 4. ERROR log in sampled trace - should pass through
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);
    severityProcessor.onEmit(context, logRecord);

    // Force flush to process batch
    severityProcessor.forceFlush();
    
    // Should have exported logs from scenarios 3 and 4 only
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<LogRecordData>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(mockExporter, times(1)).export(captor.capture());
    
    // Verify batch contains 2 log records
    assertThat(captor.getValue()).hasSize(2);
  }
}
