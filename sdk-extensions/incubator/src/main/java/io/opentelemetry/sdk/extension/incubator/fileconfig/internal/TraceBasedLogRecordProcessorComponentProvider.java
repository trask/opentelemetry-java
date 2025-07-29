/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.TraceBasedLogRecordProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Declarative configuration SPI implementation for {@link TraceBasedLogRecordProcessor}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class TraceBasedLogRecordProcessorComponentProvider
    implements ComponentProvider<LogRecordProcessor> {

  @Override
  public Class<LogRecordProcessor> getType() {
    return LogRecordProcessor.class;
  }

  @Override
  public String getName() {
    return "trace_based";
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public LogRecordProcessor create(DeclarativeConfigProperties config) {
    TraceBasedLogRecordProcessor.TraceBasedLogRecordProcessorBuilder builder =
        TraceBasedLogRecordProcessor.builder();

    // TraceBasedLogRecordProcessor doesn't have configuration options - it always filters
    // for sampled traces. Any configuration properties would be ignored.

    // Load nested processors using the processors configuration
    List<DeclarativeConfigProperties> processorConfigs = config.getStructuredList("processors");
    if (processorConfigs != null && !processorConfigs.isEmpty()) {
      List<LogRecordProcessor> downstreamProcessors = new ArrayList<>();

      for (DeclarativeConfigProperties processorConfig : processorConfigs) {
        // Each processor config should have exactly one key which is the processor type
        Set<String> keys = processorConfig.getPropertyKeys();
        if (keys.size() != 1) {
          throw new IllegalArgumentException(
              "Each processor configuration must have exactly one key specifying the processor type");
        }

        String processorType = keys.iterator().next();
        DeclarativeConfigProperties processorProperties =
            processorConfig.getStructured(processorType);

        // Use the ComponentLoader to load the processor
        ComponentLoader componentLoader = config.getComponentLoader();
        Iterable<ComponentProvider> componentProviders =
            componentLoader.load(ComponentProvider.class);

        Optional<ComponentProvider<LogRecordProcessor>> matchingProvider =
            StreamSupport.stream(componentProviders.spliterator(), false)
                .filter(
                    provider ->
                        LogRecordProcessor.class.equals(provider.getType())
                            && processorType.equals(provider.getName()))
                .map(provider -> (ComponentProvider<LogRecordProcessor>) provider)
                .findFirst();
        if (matchingProvider.isPresent()) {
          LogRecordProcessor processor =
              matchingProvider
                  .get()
                  .create(
                      processorProperties != null
                          ? processorProperties
                          : DeclarativeConfigProperties.empty());
          downstreamProcessors.add(processor);
        } else {
          throw new IllegalArgumentException(
              "No ComponentProvider found for LogRecordProcessor with name: " + processorType);
        }
      }

      builder.addProcessors(downstreamProcessors.toArray(new LogRecordProcessor[0]));
    } else {
      // If no processors are configured, use a no-op processor as before
      LogRecordProcessor noOpProcessor = LogRecordProcessor.composite();
      builder.addProcessors(noOpProcessor);
    }

    return builder.build();
  }
}
