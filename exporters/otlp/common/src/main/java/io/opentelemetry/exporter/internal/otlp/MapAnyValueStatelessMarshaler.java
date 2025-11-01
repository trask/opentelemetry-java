/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.otlp;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.internal.marshal.MarshalerContext;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.exporter.internal.marshal.StatelessMarshaler;
import io.opentelemetry.exporter.internal.marshal.StatelessMarshalerUtil;
import io.opentelemetry.proto.common.v1.internal.KeyValueList;
import java.io.IOException;

/** Marshals {@link Attributes} into OTLP {@link KeyValueList} instances. */
final class MapAnyValueStatelessMarshaler implements StatelessMarshaler<Attributes> {

  static final MapAnyValueStatelessMarshaler INSTANCE = new MapAnyValueStatelessMarshaler();

  private MapAnyValueStatelessMarshaler() {}

  @Override
  public void writeTo(Serializer output, Attributes attributes, MarshalerContext context)
      throws IOException {
    output.serializeRepeatedMessageWithContext(
        KeyValueList.VALUES, attributes, AttributeKeyValueStatelessMarshaler.INSTANCE, context);
  }

  @Override
  public int getBinarySerializedSize(Attributes attributes, MarshalerContext context) {
    return StatelessMarshalerUtil.sizeRepeatedMessageWithContext(
        KeyValueList.VALUES, attributes, AttributeKeyValueStatelessMarshaler.INSTANCE, context);
  }
}
