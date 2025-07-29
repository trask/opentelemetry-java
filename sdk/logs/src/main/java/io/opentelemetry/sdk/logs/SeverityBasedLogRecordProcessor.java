/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A {@link LogRecordProcessor} that filters log records based on their severity level and delegates
 * to a list of downstream processors only if the log record meets the minimum severity threshold.
 */
public final class SeverityBasedLogRecordProcessor implements LogRecordProcessor {

  private final Severity minimumSeverity;
  private final LogRecordProcessor delegate;

  private SeverityBasedLogRecordProcessor(
      Severity minimumSeverity, List<LogRecordProcessor> processors) {
    this.minimumSeverity = Objects.requireNonNull(minimumSeverity, "minimumSeverity");
    Objects.requireNonNull(processors, "processors");
    if (processors.isEmpty()) {
      throw new IllegalArgumentException("At least one processor must be specified");
    }
    this.delegate = MultiLogRecordProcessor.create(processors);
  }

  /**
   * Returns a new {@link SeverityBasedLogRecordProcessorBuilder} for creating a {@link
   * SeverityBasedLogRecordProcessor}.
   */
  public static SeverityBasedLogRecordProcessorBuilder builder() {
    return new SeverityBasedLogRecordProcessorBuilder();
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    // Only delegate if the log record severity meets the minimum threshold
    Severity logSeverity = logRecord.getSeverity();
    if (logSeverity != null
        && logSeverity.getSeverityNumber() >= minimumSeverity.getSeverityNumber()) {
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

  /** Builder for {@link SeverityBasedLogRecordProcessor}. */
  public static final class SeverityBasedLogRecordProcessorBuilder {
    @Nullable private Severity minimumSeverity;
    private final List<LogRecordProcessor> processors = new ArrayList<>();

    private SeverityBasedLogRecordProcessorBuilder() {}

    /**
     * Sets the minimum severity level for log records to be processed. Only log records with
     * severity greater than or equal to this level will be delegated to downstream processors.
     *
     * @param severity the minimum severity level
     * @return this builder
     */
    public SeverityBasedLogRecordProcessorBuilder setSeverity(Severity severity) {
      this.minimumSeverity = Objects.requireNonNull(severity, "severity");
      return this;
    }

    /**
     * Adds multiple processors to the list of downstream processors.
     *
     * @param processors the processors to add
     * @return this builder
     */
    public SeverityBasedLogRecordProcessorBuilder addProcessors(LogRecordProcessor... processors) {
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
    public SeverityBasedLogRecordProcessorBuilder addProcessors(
        Iterable<LogRecordProcessor> processors) {
      for (LogRecordProcessor processor : processors) {
        this.processors.add(Objects.requireNonNull(processor, "processor"));
      }
      return this;
    }

    /**
     * Builds the {@link SeverityBasedLogRecordProcessor}.
     *
     * @return the configured processor
     * @throws IllegalArgumentException if severity is not set or no processors are configured
     */
    public SeverityBasedLogRecordProcessor build() {
      if (minimumSeverity == null) {
        throw new IllegalArgumentException("Minimum severity must be set");
      }
      if (processors.isEmpty()) {
        throw new IllegalArgumentException("At least one processor must be configured");
      }
      return new SeverityBasedLogRecordProcessor(minimumSeverity, processors);
    }
  }
}
