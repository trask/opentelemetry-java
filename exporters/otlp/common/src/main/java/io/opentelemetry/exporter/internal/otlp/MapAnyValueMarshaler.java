/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.otlp;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.internal.marshal.Marshaler;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.proto.common.v1.internal.AnyValue;
import io.opentelemetry.proto.common.v1.internal.KeyValueList;
import java.io.IOException;

/** Marshals {@link Attributes} into OTLP {@link AnyValue#KVLIST_VALUE} messages. */
final class MapAnyValueMarshaler extends MarshalerWithSize {

  private final Marshaler value;

  private MapAnyValueMarshaler(MapValueMarshaler value) {
    super(calculateSize(value));
    this.value = value;
  }

  static MarshalerWithSize create(Attributes attributes) {
    KeyValueMarshaler[] marshalers = KeyValueMarshaler.createForAttributes(attributes);
    return new MapAnyValueMarshaler(new MapValueMarshaler(marshalers));
  }

  @Override
  public void writeTo(Serializer output) throws IOException {
    output.serializeMessage(AnyValue.KVLIST_VALUE, value);
  }

  private static int calculateSize(Marshaler value) {
    return MarshalerUtil.sizeMessage(AnyValue.KVLIST_VALUE, value);
  }

  private static class MapValueMarshaler extends MarshalerWithSize {

    private final Marshaler[] values;

    private MapValueMarshaler(KeyValueMarshaler[] values) {
      super(calculateSize(values));
      this.values = values;
    }

    @Override
    public void writeTo(Serializer output) throws IOException {
      output.serializeRepeatedMessage(KeyValueList.VALUES, values);
    }

    private static int calculateSize(Marshaler[] values) {
      return MarshalerUtil.sizeRepeatedMessage(KeyValueList.VALUES, values);
    }
  }
}
