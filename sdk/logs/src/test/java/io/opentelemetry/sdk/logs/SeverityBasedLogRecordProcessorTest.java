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

import io.opentelemetry.api.logs.Severity;
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
class SeverityBasedLogRecordProcessorTest {

  @Mock private LogRecordProcessor mockProcessor1;
  @Mock private LogRecordProcessor mockProcessor2;
  @Mock private ReadWriteLogRecord logRecord;
  private Context context;

  @BeforeEach
  void setUp() {
    context = Context.current();
    when(mockProcessor1.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor1.forceFlush()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor2.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
    when(mockProcessor2.forceFlush()).thenReturn(CompletableResultCode.ofSuccess());
  }

  @Test
  void create_withSeverityAndSingleProcessor() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1)
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_withSeverityAndMultipleProcessors() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_withSeverityAndProcessorList() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(Arrays.asList(mockProcessor1, mockProcessor2))
            .build();

    assertThat(processor).isNotNull();
  }

  @Test
  void create_nullSeverity_throwsException() {
    assertThatThrownBy(() -> 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(null)
            .addProcessors(mockProcessor1)
            .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("severity");
  }

  @Test
  void create_nullProcessors_throwsException() {
    assertThatThrownBy(() -> 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }

  @Test
  void create_emptyProcessorsList_throwsException() {
    assertThatThrownBy(() -> 
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(Arrays.asList())
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }

  @Test
  void onEmit_severityMeetsThreshold_delegatesToAllProcessors() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void onEmit_severityExactlyMeetsThreshold_delegatesToAllProcessors() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.WARN);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void onEmit_severityBelowThreshold_doesNotDelegate() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.INFO);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
    verify(mockProcessor2, never()).onEmit(any(), any());
  }

  @Test
  void onEmit_nullSeverity_doesNotDelegate() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSeverity()).thenReturn(null);

    processor.onEmit(context, logRecord);

    verify(mockProcessor1, never()).onEmit(any(), any());
    verify(mockProcessor2, never()).onEmit(any(), any());
  }

  @Test
  void onEmit_allSeverityLevels_correctFiltering() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.INFO)
            .addProcessors(mockProcessor1)
            .build();

    // Test severity levels below threshold
    when(logRecord.getSeverity()).thenReturn(Severity.TRACE);
    processor.onEmit(context, logRecord);
    when(logRecord.getSeverity()).thenReturn(Severity.DEBUG);
    processor.onEmit(context, logRecord);
    
    // Should not delegate for below threshold levels
    verify(mockProcessor1, never()).onEmit(any(), any());

    // Test severity levels at and above threshold
    when(logRecord.getSeverity()).thenReturn(Severity.INFO);
    processor.onEmit(context, logRecord);
    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    processor.onEmit(context, logRecord);
    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    processor.onEmit(context, logRecord);
    when(logRecord.getSeverity()).thenReturn(Severity.FATAL);
    processor.onEmit(context, logRecord);
    
    // Should delegate for at-or-above threshold levels (4 times)
    verify(mockProcessor1, times(4)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void shutdown_delegatesToAllProcessors() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    CompletableResultCode result = processor.shutdown();

    verify(mockProcessor1, times(1)).shutdown();
    verify(mockProcessor2, times(1)).shutdown();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void shutdown_multipleCallsOnlyShutdownOnce() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1)
            .build();

    processor.shutdown();
    processor.shutdown();

    verify(mockProcessor1, times(1)).shutdown();
  }

  @Test
  void forceFlush_delegatesToAllProcessors() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    CompletableResultCode result = processor.forceFlush();

    verify(mockProcessor1, times(1)).forceFlush();
    verify(mockProcessor2, times(1)).forceFlush();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void builder_setSeverity() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.ERROR)
            .addProcessors(mockProcessor1)
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.WARN);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, never()).onEmit(any(), any());

    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    processor.onEmit(context, logRecord);
    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void builder_addProcessorsArray() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(mockProcessor1, mockProcessor2)
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void builder_addProcessorsIterable() {
    SeverityBasedLogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(Arrays.asList(mockProcessor1, mockProcessor2))
            .build();

    when(logRecord.getSeverity()).thenReturn(Severity.ERROR);
    processor.onEmit(context, logRecord);

    verify(mockProcessor1, times(1)).onEmit(eq(context), eq(logRecord));
    verify(mockProcessor2, times(1)).onEmit(eq(context), eq(logRecord));
  }

  @Test
  void builder_nullSeverity_throwsException() {
    assertThatThrownBy(() ->
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("severity");
  }

  @Test
  void builder_buildWithoutSeverity_throwsException() {
    assertThatThrownBy(() ->
        SeverityBasedLogRecordProcessor.builder()
            .addProcessors(mockProcessor1)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Minimum severity must be set");
  }

  @Test
  void builder_buildWithoutProcessors_throwsException() {
    assertThatThrownBy(() ->
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one processor must be configured");
  }
}
