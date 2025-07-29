/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of {@link LogRecordProcessor} that filters log records based on minimum severity
 * level and delegates to downstream processors.
 *
 * <p>This processor only forwards log records to downstream processors if the log record's
 * severity level is greater than or equal to the configured minimum severity level.
 *
 * @since 1.45.0
 */
public final class SeverityBasedLogRecordProcessor implements LogRecordProcessor {

  private final Severity minimumSeverity;
  private final LogRecordProcessor delegate;
  private final List<LogRecordProcessor> processors;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  SeverityBasedLogRecordProcessor(
      Severity minimumSeverity, List<LogRecordProcessor> processors) {
    this.minimumSeverity = requireNonNull(minimumSeverity, "minimumSeverity");
    this.processors = Collections.unmodifiableList(new ArrayList<>(processors));
    this.delegate = LogRecordProcessor.composite(processors);
  }

  /**
   * Returns a new {@link SeverityBasedLogRecordProcessorBuilder} to construct a {@link
   * SeverityBasedLogRecordProcessor}.
   *
   * @param minimumSeverity the minimum severity level required for processing
   * @return a new {@link SeverityBasedLogRecordProcessorBuilder}
   */
  public static SeverityBasedLogRecordProcessorBuilder builder(Severity minimumSeverity) {
    return new SeverityBasedLogRecordProcessorBuilder(minimumSeverity);
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    Severity logSeverity = logRecord.getSeverity();
    if (logSeverity.getSeverityNumber() >= minimumSeverity.getSeverityNumber()) {
      delegate.onEmit(context, logRecord);
    }
    // If severity doesn't meet minimum requirement, drop the log record (no delegation)
  }

  @Override
  public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) {
      return CompletableResultCode.ofSuccess();
    }
    return delegate.shutdown();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return delegate.forceFlush();
  }

  /**
   * Returns the minimum severity level required for processing.
   *
   * @return the minimum severity level
   */
  public Severity getMinimumSeverity() {
    return minimumSeverity;
  }

  /**
   * Returns an unmodifiable list of downstream processors.
   *
   * @return the list of downstream processors
   */
  public List<LogRecordProcessor> getProcessors() {
    return processors;
  }

  @Override
  public String toString() {
    return "SeverityBasedLogRecordProcessor{"
        + "minimumSeverity="
        + minimumSeverity
        + ", processors="
        + processors
        + '}';
  }
}
