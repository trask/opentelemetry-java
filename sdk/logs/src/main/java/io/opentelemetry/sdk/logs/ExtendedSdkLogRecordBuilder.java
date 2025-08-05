/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.ExtendedAttributesMap;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** SDK implementation of {@link ExtendedLogRecordBuilder}. */
final class ExtendedSdkLogRecordBuilder extends SdkLogRecordBuilder
    implements ExtendedLogRecordBuilder {

  @Nullable private ExtendedAttributesMap extendedAttributes;

  ExtendedSdkLogRecordBuilder(
      LoggerSharedState loggerSharedState, InstrumentationScopeInfo instrumentationScopeInfo, @Nullable SdkLogger logger) {
    super(loggerSharedState, instrumentationScopeInfo, logger);
  }

  // Backward compatible constructor
  ExtendedSdkLogRecordBuilder(
      LoggerSharedState loggerSharedState, InstrumentationScopeInfo instrumentationScopeInfo) {
    super(loggerSharedState, instrumentationScopeInfo);
  }

  @Override
  public ExtendedSdkLogRecordBuilder setEventName(String eventName) {
    super.setEventName(eventName);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setException(Throwable throwable) {
    if (throwable == null) {
      return this;
    }

    loggerSharedState
        .getExceptionAttributeResolver()
        .setExceptionAttributes(
            this::setAttribute,
            throwable,
            loggerSharedState.getLogLimits().getMaxAttributeValueLength());

    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setTimestamp(long timestamp, TimeUnit unit) {
    super.setTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setTimestamp(Instant instant) {
    super.setTimestamp(instant);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setObservedTimestamp(long timestamp, TimeUnit unit) {
    super.setObservedTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setObservedTimestamp(Instant instant) {
    super.setObservedTimestamp(instant);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setContext(Context context) {
    super.setContext(context);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setSeverity(Severity severity) {
    super.setSeverity(severity);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setSeverityText(String severityText) {
    super.setSeverityText(severityText);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setBody(String body) {
    super.setBody(body);
    return this;
  }

  @Override
  public ExtendedSdkLogRecordBuilder setBody(Value<?> value) {
    super.setBody(value);
    return this;
  }

  @Override
  public <T> ExtendedSdkLogRecordBuilder setAttribute(ExtendedAttributeKey<T> key, T value) {
    if (key == null || key.getKey().isEmpty() || value == null) {
      return this;
    }
    if (this.extendedAttributes == null) {
      this.extendedAttributes =
          ExtendedAttributesMap.create(
              logLimits.getMaxNumberOfAttributes(), logLimits.getMaxAttributeValueLength());
    }
    this.extendedAttributes.put(key, value);
    return this;
  }

  @Override
  public <T> ExtendedSdkLogRecordBuilder setAttribute(AttributeKey<T> key, @Nullable T value) {
    if (key == null || key.getKey().isEmpty() || value == null) {
      return this;
    }
    return setAttribute(ExtendedAttributeKey.fromAttributeKey(key), value);
  }

  @Override
  public void emit() {
    if (loggerSharedState.hasBeenShutdown()) {
      return;
    }
    Context context = this.context == null ? Context.current() : this.context;

    // Apply filtering rules if logger is available
    if (logger != null) {
      // 1. Check minimum severity level
      if (severity != Severity.UNDEFINED_SEVERITY_NUMBER && severity.getSeverityNumber() < logger.minimumSeverity) {
        return;
      }

      // 2. Check trace-based filtering
      if (logger.traceBased) {
        Span span = Span.fromContext(context);
        if (span.getSpanContext().isValid() && !span.getSpanContext().getTraceFlags().isSampled()) {
          return;
        }
      }
    }

    long observedTimestampEpochNanos =
        this.observedTimestampEpochNanos == 0
            ? this.loggerSharedState.getClock().now()
            : this.observedTimestampEpochNanos;
    loggerSharedState
        .getLogRecordProcessor()
        .onEmit(
            context,
            ExtendedSdkReadWriteLogRecord.create(
                loggerSharedState.getLogLimits(),
                loggerSharedState.getResource(),
                instrumentationScopeInfo,
                eventName,
                timestampEpochNanos,
                observedTimestampEpochNanos,
                Span.fromContext(context).getSpanContext(),
                severity,
                severityText,
                body,
                extendedAttributes));
  }
}
