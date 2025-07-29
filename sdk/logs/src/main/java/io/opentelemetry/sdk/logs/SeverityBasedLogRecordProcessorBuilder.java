/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.logs.Severity;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for {@link SeverityBasedLogRecordProcessor}.
 *
 * @since 1.45.0
 */
public final class SeverityBasedLogRecordProcessorBuilder {

  private final Severity minimumSeverity;
  private final List<LogRecordProcessor> processors = new ArrayList<>();

  SeverityBasedLogRecordProcessorBuilder(Severity minimumSeverity) {
    this.minimumSeverity = requireNonNull(minimumSeverity, "minimumSeverity");
  }

  /**
   * Adds a {@link LogRecordProcessor} to the list of downstream processors.
   *
   * @param processor the processor to add
   * @return this builder
   */
  public SeverityBasedLogRecordProcessorBuilder addProcessor(LogRecordProcessor processor) {
    requireNonNull(processor, "processor");
    processors.add(processor);
    return this;
  }

  /**
   * Adds multiple {@link LogRecordProcessor}s to the list of downstream processors.
   *
   * @param processors the processors to add
   * @return this builder
   */
  public SeverityBasedLogRecordProcessorBuilder addProcessors(LogRecordProcessor... processors) {
    requireNonNull(processors, "processors");
    for (LogRecordProcessor processor : processors) {
      addProcessor(processor);
    }
    return this;
  }

  /**
   * Adds multiple {@link LogRecordProcessor}s to the list of downstream processors.
   *
   * @param processors the processors to add
   * @return this builder
   */
  public SeverityBasedLogRecordProcessorBuilder addProcessors(
      Iterable<LogRecordProcessor> processors) {
    requireNonNull(processors, "processors");
    for (LogRecordProcessor processor : processors) {
      addProcessor(processor);
    }
    return this;
  }

  /**
   * Returns a new {@link SeverityBasedLogRecordProcessor} with the configuration of this builder.
   *
   * @return a new {@link SeverityBasedLogRecordProcessor}
   * @throws IllegalArgumentException if no processors have been added
   */
  public SeverityBasedLogRecordProcessor build() {
    if (processors.isEmpty()) {
      throw new IllegalArgumentException("At least one processor must be added");
    }
    return new SeverityBasedLogRecordProcessor(minimumSeverity, processors);
  }
}
