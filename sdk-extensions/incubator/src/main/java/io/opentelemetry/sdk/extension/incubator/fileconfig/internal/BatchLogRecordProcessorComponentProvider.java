/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessorBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Declarative configuration SPI implementation for {@link BatchLogRecordProcessor}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class BatchLogRecordProcessorComponentProvider
    implements ComponentProvider<LogRecordProcessor> {

  @Override
  public Class<LogRecordProcessor> getType() {
    return LogRecordProcessor.class;
  }

  @Override
  public String getName() {
    return "batch";
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public LogRecordProcessor create(DeclarativeConfigProperties config) {
    // Get the exporter configuration
    DeclarativeConfigProperties exporterConfig = config.getStructured("exporter");
    if (exporterConfig == null) {
      throw new IllegalArgumentException("batch log record processor exporter is required");
    }

    // Find the exporter type
    Set<String> exporterKeys = exporterConfig.getPropertyKeys();
    if (exporterKeys.size() != 1) {
      throw new IllegalArgumentException(
          "Exporter configuration must have exactly one key specifying the exporter type");
    }

    String exporterType = exporterKeys.iterator().next();
    DeclarativeConfigProperties exporterProperties = exporterConfig.getStructured(exporterType);

    // Load the exporter using ComponentLoader
    ComponentLoader componentLoader = config.getComponentLoader();
    Iterable<ComponentProvider> componentProviders =
        componentLoader.load(ComponentProvider.class);

    Optional<ComponentProvider<LogRecordExporter>> matchingExporterProvider =
        StreamSupport.stream(componentProviders.spliterator(), false)
            .filter(
                provider ->
                    LogRecordExporter.class.equals(provider.getType())
                        && exporterType.equals(provider.getName()))
            .map(provider -> (ComponentProvider<LogRecordExporter>) provider)
            .findFirst();

    if (!matchingExporterProvider.isPresent()) {
      throw new IllegalArgumentException(
          "No ComponentProvider found for LogRecordExporter with name: " + exporterType);
    }

    LogRecordExporter exporter =
        matchingExporterProvider
            .get()
            .create(
                exporterProperties != null
                    ? exporterProperties
                    : DeclarativeConfigProperties.empty());

    // Build the BatchLogRecordProcessor with configuration
    BatchLogRecordProcessorBuilder builder = BatchLogRecordProcessor.builder(exporter);

    // Configure optional properties
    Integer exportTimeout = config.getInt("export_timeout");
    if (exportTimeout != null) {
      builder.setExporterTimeout(Duration.ofMillis(exportTimeout));
    }

    Integer maxExportBatchSize = config.getInt("max_export_batch_size");
    if (maxExportBatchSize != null) {
      builder.setMaxExportBatchSize(maxExportBatchSize);
    }

    Integer maxQueueSize = config.getInt("max_queue_size");
    if (maxQueueSize != null) {
      builder.setMaxQueueSize(maxQueueSize);
    }

    Integer scheduleDelay = config.getInt("schedule_delay");
    if (scheduleDelay != null) {
      builder.setScheduleDelay(Duration.ofMillis(scheduleDelay));
    }

    return builder.build();
  }
}
