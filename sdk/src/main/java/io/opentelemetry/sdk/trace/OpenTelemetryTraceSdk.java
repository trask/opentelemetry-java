/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.trace.Tracer;
import javax.annotation.concurrent.ThreadSafe;

/** This class provides static global access to SDK trace features. */
@ThreadSafe
public final class OpenTelemetryTraceSdk {

  /**
   * Returns the active {@code TraceConfig}.
   *
   * @return the active {@code TraceConfig}.
   */
  public static TraceConfig getActiveTraceConfig() {
    return getTracerProvider().getActiveTraceConfig();
  }

  /**
   * Updates the active {@link TraceConfig}.
   *
   * <p>Note: To update the {@link TraceConfig} associated with this instance you should use the
   * {@link TraceConfig#toBuilder()} method on the {@link TraceConfig} returned from {@link
   * #getActiveTraceConfig()}, make the changes desired to the {@link TraceConfig.Builder} instance,
   * then use this method with the resulting {@link TraceConfig} instance.
   *
   * @param traceConfig the new active {@code TraceConfig}.
   * @see TraceConfig
   */
  public static void updateActiveTraceConfig(TraceConfig traceConfig) {
    getTracerProvider().updateActiveTraceConfig(traceConfig);
  }

  /**
   * Adds a new {@code SpanProcessor} to this {@code Tracer}.
   *
   * <p>Any registered processor cause overhead, consider to use an async/batch processor especially
   * for span exporting, and export to multiple backends using the {@link
   * io.opentelemetry.sdk.trace.export.MultiSpanExporter}.
   *
   * @param spanProcessor the new {@code SpanProcessor} to be added.
   */
  public static void addSpanProcessor(SpanProcessor spanProcessor) {
    getTracerProvider().addSpanProcessor(spanProcessor);
  }

  /**
   * Attempts to stop all the activity for this {@link Tracer}. Calls {@link
   * SpanProcessor#shutdown()} for all registered {@link SpanProcessor}s.
   *
   * <p>This operation may block until all the Spans are processed. Must be called before turning
   * off the main application to ensure all data are processed and exported.
   *
   * <p>After this is called all the newly created {@code Span}s will be no-op.
   */
  public static void shutdown() {
    getTracerProvider().shutdown();
  }

  /**
   * Requests the active span processor to process all span events that have not yet been processed.
   *
   * @see SpanProcessor#forceFlush()
   */
  public static void forceFlush() {
    getTracerProvider().forceFlush();
  }

  private static TracerSdkProvider getTracerProvider() {
    return (TracerSdkProvider) OpenTelemetry.getTracerProvider();
  }

  private OpenTelemetryTraceSdk() {}
}
