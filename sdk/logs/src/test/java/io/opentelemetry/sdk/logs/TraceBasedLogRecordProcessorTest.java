/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceBasedLogRecordProcessorTest {

  @Mock private LogRecordProcessor mockProcessor1;
  @Mock private LogRecordProcessor mockProcessor2;
  @Mock private ReadWriteLogRecord logRecord;
  private Context context;

  private static final String TRACE_ID = "12345678901234567890123456789012";
  private static final String SPAN_ID = "1234567890123456";

  @BeforeEach
  void setUp() {
    context = Context.current();
    when(mockProcessor1.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor1.forceFlush()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor2.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor2.forceFlush()).thenReturn(CompletableResultCode.ofSuccess());
  }

  @Test
  void create_withSingleProcessor() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_withMultipleProcessors() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_withProcessorList() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(Arrays.asList(mockProcessor1, mockProcessor2))
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_nullProcessors_throwsException() {
    assertThatThrownBy(() -> 
        TraceBasedLogRecordProcessor.builder()
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }

  @Test
  void create_emptyProcessorsList_throwsException() {
    assertThatThrownBy(() -> 
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(Arrays.asList())
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }

  @Test
  void onEmit_sampledSpanContext_delegatesToAllProcessors() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void onEmit_nonSampledSpanContext_doesNotDelegate() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    SpanContext nonSampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(nonSampledSpanContext);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
    verify(mockProcessor2, never()).onEmit(any(), any());
  }

  @Test
  void onEmit_invalidSpanContext_doesNotDelegate() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSpanContext()).thenReturn(SpanContext.getInvalid());

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
    verify(mockProcessor2, never()).onEmit(any(), any());
  }

  @Test
  void onEmit_nullSpanContext_doesNotDelegate() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSpanContext()).thenReturn(null);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
    verify(mockProcessor2, never()).onEmit(any(), any());
  }

  @Test
  void onEmit_validButNonSampledSpanContext_doesNotDelegate() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build();

    // Create a valid span context but not sampled
    SpanContext validNonSampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(validNonSampledSpanContext);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
  }

  @Test
  void shutdown_delegatesToAllProcessors() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    CompletableResultCode result = processor.shutdown();

    verify(mockProcessor1, times(1)).shutdown();
    verify(mockProcessor2, times(1)).shutdown();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void shutdown_multipleCallsOnlyShutdownOnce() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build();

    processor.shutdown();
    processor.shutdown();

    verify(mockProcessor1, times(1)).shutdown();
  }

  @Test
  void forceFlush_delegatesToAllProcessors() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    CompletableResultCode result = processor.forceFlush();

    verify(mockProcessor1, times(1)).forceFlush();
    verify(mockProcessor2, times(1)).forceFlush();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void builder_addProcessorsArray() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void builder_addProcessorsIterable() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(Arrays.asList(mockProcessor1, mockProcessor2))
            .build();

    SpanContext sampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledSpanContext);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void builder_buildWithoutProcessors_throwsException() {
    assertThatThrownBy(() ->
        TraceBasedLogRecordProcessor.builder()
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }

  @Test
  void onEmit_edgeCases_handlesCorrectly() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build();

    // Test edge case: span context with invalid trace ID (all zeros) but valid span ID
    SpanContext invalidTraceIdSpanContext = 
        SpanContext.create("00000000000000000000000000000000", SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(invalidTraceIdSpanContext);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, never()).onEmit(any(), any());

    // Test edge case: span context with valid trace ID but invalid span ID (all zeros)
    SpanContext invalidSpanIdSpanContext = 
        SpanContext.create(TRACE_ID, "0000000000000000", TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(invalidSpanIdSpanContext);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, never()).onEmit(any(), any());

    // Test edge case: completely valid and sampled span context
    SpanContext validSampledSpanContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(validSampledSpanContext);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void onEmit_traceFlagsVariations_behavesCorrectly() {
    TraceBasedLogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build();

    // Test with explicitly non-sampled trace flags
    SpanContext nonSampledContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(nonSampledContext);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, never()).onEmit(any(), any());

    // Test with explicitly sampled trace flags
    SpanContext sampledContext = 
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    when(logRecord.getSpanContext()).thenReturn(sampledContext);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
  }
}
