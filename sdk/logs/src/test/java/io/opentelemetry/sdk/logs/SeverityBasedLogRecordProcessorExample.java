/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;

/**
 * Example demonstrating how to use {@link SeverityBasedLogRecordProcessor}.
 *
 * <p>This example shows how to create a severity-based processor that only forwards logs of WARN
 * level or higher to downstream processors.
 */
public final class SeverityBasedLogRecordProcessorExample {

  private SeverityBasedLogRecordProcessorExample() {}

  public static void main(String[] args) {
    // Example 1: Basic usage with a single downstream processor
    LogRecordExporter exporter = LogRecordExporter.composite(); // Replace with actual exporter

    LogRecordProcessor severityProcessor =
        SeverityBasedLogRecordProcessor.builder(Severity.WARN)
            .addProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();

    // Example 2: Chaining with batch processor
    LogRecordProcessor chainedProcessor =
        SeverityBasedLogRecordProcessor.builder(Severity.ERROR)
            .addProcessor(BatchLogRecordProcessor.builder(exporter).build())
            .build();

    // Example 3: Multiple downstream processors
    LogRecordProcessor multiProcessor =
        SeverityBasedLogRecordProcessor.builder(Severity.INFO)
            .addProcessor(SimpleLogRecordProcessor.create(exporter))
            .addProcessor(BatchLogRecordProcessor.builder(exporter).build())
            .build();

    // Example 4: Using builder convenience methods
    LogRecordProcessor varArgsProcessor =
        SeverityBasedLogRecordProcessor.builder(Severity.DEBUG)
            .addProcessors(
                SimpleLogRecordProcessor.create(exporter),
                BatchLogRecordProcessor.builder(exporter).build())
            .build();

    // Clean up resources
    severityProcessor.shutdown();
    chainedProcessor.shutdown();
    multiProcessor.shutdown();
    varArgsProcessor.shutdown();
  }
}
