/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtendedSpanBuilderTest {

  private InMemorySpanExporter spanExporter;
  private SdkTracerProvider tracerProvider;
  private ExtendedTracer tracer;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    tracer = (ExtendedTracer) tracerProvider.get("test");
  }

  @AfterEach
  void tearDown() throws Exception {
    tracerProvider.close();
  }

  @Test
  void setAttribute_withExtendedAttributeKeyDelegatesToSdk() {
    ExtendedSpanBuilder spanBuilder = tracer.spanBuilder("span");

    spanBuilder.setAttribute(ExtendedAttributeKey.stringKey("extended.string"), "value");
    spanBuilder.startSpan().end();

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertThat(spans)
        .hasSize(1)
        .first()
        .satisfies(
            spanData ->
                assertThat(spanData.getAttributes())
                    .containsEntry(AttributeKey.stringKey("extended.string"), "value"));
  }

  @Test
  void setAttribute_withUnsupportedExtendedAttributeKeyIsNoop() {
    ExtendedSpanBuilder spanBuilder = tracer.spanBuilder("span");

    spanBuilder.setAttribute(
        ExtendedAttributeKey.mapKey("extended.map"), ExtendedAttributes.empty());
    spanBuilder.startSpan().end();

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertThat(spans)
        .hasSize(1)
        .first()
        .satisfies(
            spanData ->
                assertThat(spanData.getAttributes().asMap())
                    .doesNotContainKey(AttributeKey.stringKey("extended.map")));
  }
}
