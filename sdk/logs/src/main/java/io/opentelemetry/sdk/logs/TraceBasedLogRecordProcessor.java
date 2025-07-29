/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link LogRecordProcessor} that filters log records based on whether their associated span
 * context is sampled and delegates to a list of downstream processors only if the span context is
 * sampled.
 *
 * <p>Log records without a valid span context or with a non-sampled span context will be filtered
 * out and not passed to downstream processors.
 *
 * @since 1.42.0
 */
public final class TraceBasedLogRecordProcessor implements LogRecordProcessor {

  private final LogRecordProcessor delegate;

  private TraceBasedLogRecordProcessor(List<LogRecordProcessor> processors) {
    Objects.requireNonNull(processors, "processors");
    if (processors.isEmpty()) {
      throw new IllegalArgumentException("At least one processor must be specified");
    }
    this.delegate = MultiLogRecordProcessor.create(processors);
  }

  /**
   * Returns a new {@link TraceBasedLogRecordProcessorBuilder} for creating a {@link
   * TraceBasedLogRecordProcessor}.
   */
  public static TraceBasedLogRecordProcessorBuilder builder() {
    return new TraceBasedLogRecordProcessorBuilder();
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    // Only delegate if the log record's span context is sampled
    SpanContext spanContext = logRecord.getSpanContext();
    if (spanContext != null && spanContext.isValid() && spanContext.isSampled()) {
      delegate.onEmit(context, logRecord);
    }
  }

  @Override
  public CompletableResultCode shutdown() {
    return delegate.shutdown();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return delegate.forceFlush();
  }

  /** Builder for {@link TraceBasedLogRecordProcessor}. */
  public static final class TraceBasedLogRecordProcessorBuilder {

    private final List<LogRecordProcessor> processors = new ArrayList<>();

    private TraceBasedLogRecordProcessorBuilder() {}

    /**
     * Adds multiple processors to the list of downstream processors.
     *
     * @param processors the processors to add
     * @return this builder
     */
    public TraceBasedLogRecordProcessorBuilder addProcessors(LogRecordProcessor... processors) {
      for (LogRecordProcessor processor : processors) {
        this.processors.add(Objects.requireNonNull(processor, "processor"));
      }
      return this;
    }

    /**
     * Adds multiple processors to the list of downstream processors.
     *
     * @param processors the processors to add
     * @return this builder
     */
    public TraceBasedLogRecordProcessorBuilder addProcessors(
        Iterable<LogRecordProcessor> processors) {
      for (LogRecordProcessor processor : processors) {
        this.processors.add(Objects.requireNonNull(processor, "processor"));
      }
      return this;
    }

    /**
     * Builds the {@link TraceBasedLogRecordProcessor}.
     *
     * @return the configured processor
     * @throws IllegalArgumentException if no processors are configured
     */
    public TraceBasedLogRecordProcessor build() {
      if (processors.isEmpty()) {
        throw new IllegalArgumentException("At least one processor must be configured");
      }
      return new TraceBasedLogRecordProcessor(processors);
    }
  }
}
