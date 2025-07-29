/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SeverityBasedLogRecordProcessor;
import io.opentelemetry.sdk.logs.TraceBasedLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessorBehaviorTest {

  @Test
  void severityBasedProcessorFiltersCorrectly() {
    // Create a counting exporter to verify filtering
    AtomicInteger exportCount = new AtomicInteger(0);
    LogRecordExporter countingExporter =
        new LogRecordExporter() {
          @Override
          public io.opentelemetry.sdk.common.CompletableResultCode export(
              Collection<io.opentelemetry.sdk.logs.data.LogRecordData> logs) {
            exportCount.addAndGet(logs.size());
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
          }

          @Override
          public io.opentelemetry.sdk.common.CompletableResultCode flush() {
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
          }

          @Override
          public io.opentelemetry.sdk.common.CompletableResultCode shutdown() {
            return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
          }
        };

    // Create severity-based processor that only allows WARN and above
    LogRecordProcessor processor =
        SeverityBasedLogRecordProcessor.builder()
            .setSeverity(Severity.WARN)
            .addProcessors(SimpleLogRecordProcessor.create(countingExporter))
            .build();

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder().addLogRecordProcessor(processor).build();

    Logger logger = loggerProvider.get("test");

    // Log at different severities
    logger
        .logRecordBuilder()
        .setSeverity(Severity.DEBUG)
        .setBody("debug message")
        .emit(); // Should be filtered
    logger
        .logRecordBuilder()
        .setSeverity(Severity.INFO)
        .setBody("info message")
        .emit(); // Should be filtered
    logger
        .logRecordBuilder()
        .setSeverity(Severity.WARN)
        .setBody("warn message")
        .emit(); // Should pass
    logger
        .logRecordBuilder()
        .setSeverity(Severity.ERROR)
        .setBody("error message")
        .emit(); // Should pass

    // Only WARN and ERROR should have been exported
    assertThat(exportCount.get()).isEqualTo(2);
  }

  @Test
  void traceBasedProcessorFiltersCorrectly() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();

    // Create trace-based processor
    LogRecordProcessor processor =
        TraceBasedLogRecordProcessor.builder()
            .addProcessors(SimpleLogRecordProcessor.create(exporter))
            .build();

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder().addLogRecordProcessor(processor).build();

    Logger logger = loggerProvider.get("test");

    // Create sampled and non-sampled span contexts
    SpanContext sampledContext =
        SpanContext.create(
            "12345678901234567890123456789012",
            "1234567890123456",
            TraceFlags.getSampled(),
            TraceState.getDefault());

    SpanContext nonSampledContext =
        SpanContext.create(
            "12345678901234567890123456789012",
            "1234567890123456",
            TraceFlags.getDefault(), // not sampled
            TraceState.getDefault());

    // Log with sampled context (should pass)
    Context sampledCtx = Context.root().with(Span.wrap(sampledContext));
    logger.logRecordBuilder().setContext(sampledCtx).setBody("sampled log").emit();

    // Log with non-sampled context (should be filtered)
    Context nonSampledCtx = Context.root().with(Span.wrap(nonSampledContext));
    logger.logRecordBuilder().setContext(nonSampledCtx).setBody("non-sampled log").emit();

    // Log without span context (should be filtered)
    logger.logRecordBuilder().setBody("no context log").emit();

    // Only the sampled log should have been exported
    assertThat(exporter.getFinishedLogRecordItems()).hasSize(1);
    assertThat(exporter.getFinishedLogRecordItems().get(0).getSpanContext())
        .isEqualTo(sampledContext);
  }
}
