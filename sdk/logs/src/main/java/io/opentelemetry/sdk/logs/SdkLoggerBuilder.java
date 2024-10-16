/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.sdk.internal.ComponentRegistry;
import javax.annotation.Nullable;

final class SdkLoggerBuilder implements LoggerBuilder {

  private final ComponentRegistry<SdkLogger> registry;
  private final String instrumentationScopeName;
  @Nullable private String instrumentationScopeVersion;
  @Nullable private String schemaUrl;
  @Nullable private String eventDomain;

  SdkLoggerBuilder(ComponentRegistry<SdkLogger> registry, String instrumentationScopeName) {
    this.registry = registry;
    this.instrumentationScopeName = instrumentationScopeName;
  }

  @Override
  public LoggerBuilder setEventDomain(String eventDomain) {
    this.eventDomain = eventDomain;
    return this;
  }

  @Override
  public SdkLoggerBuilder setSchemaUrl(String schemaUrl) {
    this.schemaUrl = schemaUrl;
    return this;
  }

  @Override
  public SdkLoggerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
    this.instrumentationScopeVersion = instrumentationScopeVersion;
    return this;
  }

  @Override
  public SdkLogger build() {
    SdkLogger logger =
        registry.get(
            instrumentationScopeName, instrumentationScopeVersion, schemaUrl, Attributes.empty());
    return eventDomain == null ? logger : logger.withEventDomain(eventDomain);
  }
}
