/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.internal.EventuallyVisibleBoolean;
import io.opentelemetry.sdk.logs.internal.LoggerConfig;

/** SDK implementation of {@link Logger}. */
class SdkLogger implements Logger {

  private static final Logger NOOP_LOGGER = LoggerProvider.noop().get("noop");
  private static final boolean INCUBATOR_AVAILABLE;

  static {
    boolean incubatorAvailable = false;
    try {
      Class.forName("io.opentelemetry.api.incubator.logs.ExtendedDefaultLoggerProvider");
      incubatorAvailable = true;
    } catch (ClassNotFoundException e) {
      // Not available
    }
    INCUBATOR_AVAILABLE = incubatorAvailable;
  }

  private final LoggerSharedState loggerSharedState;
  private final InstrumentationScopeInfo instrumentationScopeInfo;

  protected final EventuallyVisibleBoolean loggerEnabled;

  SdkLogger(
      LoggerSharedState loggerSharedState,
      InstrumentationScopeInfo instrumentationScopeInfo,
      LoggerConfig loggerConfig) {
    this.loggerSharedState = loggerSharedState;
    this.instrumentationScopeInfo = instrumentationScopeInfo;
    this.loggerEnabled = new EventuallyVisibleBoolean(loggerConfig.isEnabled());
  }

  static SdkLogger create(
      LoggerSharedState sharedState,
      InstrumentationScopeInfo instrumentationScopeInfo,
      LoggerConfig loggerConfig) {
    return INCUBATOR_AVAILABLE
        ? IncubatingUtil.createExtendedLogger(sharedState, instrumentationScopeInfo, loggerConfig)
        : new SdkLogger(sharedState, instrumentationScopeInfo, loggerConfig);
  }

  @Override
  public LogRecordBuilder logRecordBuilder() {
    if (isEnabledInternal()) {
      return INCUBATOR_AVAILABLE
          ? IncubatingUtil.createExtendedLogRecordBuilder(
              loggerSharedState, instrumentationScopeInfo)
          : new SdkLogRecordBuilder(loggerSharedState, instrumentationScopeInfo);
    }
    return NOOP_LOGGER.logRecordBuilder();
  }

  // VisibleForTesting
  InstrumentationScopeInfo getInstrumentationScopeInfo() {
    return instrumentationScopeInfo;
  }

  // Visible for testing
  public boolean isEnabled(Severity severity, Context context) {
    return loggerEnabled.get();
  }

  void updateLoggerConfig(LoggerConfig loggerConfig) {
    loggerEnabled.set(loggerConfig.isEnabled());
  }
}
