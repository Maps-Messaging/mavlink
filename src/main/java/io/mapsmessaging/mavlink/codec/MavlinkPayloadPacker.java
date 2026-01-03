package io.mapsmessaging.mavlink.codec;

import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkEnumResolver;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.AbstractMavlinkFieldCodec;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;
import lombok.Getter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavlinkPayloadPacker {

  @Getter
  private final MavlinkMessageRegistry messageRegistry;

  public MavlinkPayloadPacker(MavlinkMessageRegistry messageRegistry) {
    this.messageRegistry = messageRegistry;
  }

  public byte[] packPayload(int messageId, Map<String, Object> values) throws IOException {
    MavlinkCompiledMessage compiledMessage = getCompiledMessage(messageId);
    List<MavlinkCompiledField> compiledFields = compiledMessage.getCompiledFields();

    int lastExtensionIndex = findLastIncludedExtensionIndex(compiledFields, values);
    int payloadSize = computeTotalPotentialPayloadSize(compiledFields);

    if (payloadSize <= 0) {
      throw new IOException("Computed MAVLink payload size is 0 for message id: " + messageId +
          " (" + compiledMessage.getName() + "). Likely extension/base field classification bug.");
    }

    ByteBuffer buffer = allocate(payloadSize);
    encodePayload(compiledFields, lastExtensionIndex, values, buffer);
    int length = buffer.position();
    byte[] out = new byte[length];
    buffer.flip();
    buffer.get(out);
    return out;
  }

  private MavlinkCompiledMessage getCompiledMessage(int messageId) throws IOException {
    MavlinkCompiledMessage compiledMessage = messageRegistry.getCompiledMessagesById().get(messageId);
    if (compiledMessage == null) {
      throw new IOException("Unknown MAVLink message id: " + messageId);
    }
    return compiledMessage;
  }

  private int findLastIncludedExtensionIndex(List<MavlinkCompiledField> compiledFields, Map<String, Object> values) {
    int lastExtensionIndex = -1;
    for (int i = 0; i < compiledFields.size(); i++) {
      MavlinkFieldDefinition field = compiledFields.get(i).getFieldDefinition();
      if (!field.isExtension()) {
        continue;
      }
      Object value = values.get(field.getName());
      if (value != null) {
        lastExtensionIndex = i;
      }
    }
    return lastExtensionIndex;
  }

  private int computePayloadSize(List<MavlinkCompiledField> compiledFields, int lastExtensionIndex) {
    int size = 0;
    for (int i = 0; i < compiledFields.size(); i++) {
      MavlinkCompiledField compiledField = compiledFields.get(i);
      MavlinkFieldDefinition field = compiledField.getFieldDefinition();

      if (!field.isExtension()) {
        size += compiledField.getSizeInBytes();
        continue;
      }

      if (i <= lastExtensionIndex) {
        size += compiledField.getSizeInBytes();
        continue;
      }

      break;
    }
    return size;
  }

  private int computeTotalPotentialPayloadSize(List<MavlinkCompiledField> compiledFields) {
    int size = 0;
    for (MavlinkCompiledField compiledField : compiledFields) {
      size += compiledField.getSizeInBytes();
    }
    return size;
  }

  private ByteBuffer allocate(int payloadSize) {
    ByteBuffer buffer = ByteBuffer.allocate(payloadSize);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return buffer;
  }

  private void encodePayload(
      List<MavlinkCompiledField> compiledFields,
      int lastExtensionIndex,
      Map<String, Object> values,
      ByteBuffer buffer) throws IOException {

    for (int i = 0; i < compiledFields.size(); i++) {
      MavlinkCompiledField compiledField = compiledFields.get(i);
      MavlinkFieldDefinition field = compiledField.getFieldDefinition();

      if (field.isExtension() && i > lastExtensionIndex) {
        break;
      }

      Object value = values.get(field.getName());

      if (value == null) {
        zeroFill(compiledField, buffer);
        continue;
      }

      value = MavlinkEnumResolver.resolveEnumValue(messageRegistry, field, value);

      if (!field.isArray()) {
        encodeScalar(compiledField, buffer, value);
        continue;
      }

      encodeArray(compiledField, buffer, value);

    }
  }

  private void encodeScalar(MavlinkCompiledField compiledField, ByteBuffer buffer, Object value) throws IOException {
    try {
      compiledField.getFieldCodec().encode(buffer, value);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IOException("Failed to encode field '" + compiledField.getFieldDefinition().getName() +
          "' with value type " + value.getClass().getName(), e);
    }
  }

  private void encodeArray(MavlinkCompiledField compiledField, ByteBuffer buffer, Object value) throws IOException {
    MavlinkFieldDefinition field = compiledField.getFieldDefinition();

    if (field.getWireType() == MavlinkWireType.CHAR) {
      encodeCharArray(compiledField, buffer, value);
      return;
    }

    List<?> elements = toElements(value, field.getName());
    encodeTypedArray(compiledField, buffer, elements);
  }

  private void encodeCharArray(MavlinkCompiledField compiledField, ByteBuffer buffer, Object value) throws IOException {
    MavlinkFieldDefinition field = compiledField.getFieldDefinition();
    int len = field.getArrayLength();

    byte[] src;
    if (value instanceof String s) {
      src = s.getBytes(StandardCharsets.UTF_8);
    } else if (value instanceof byte[] b) {
      src = b;
    } else {
      throw new IOException("CHAR array field '" + field.getName() + "' expects String or byte[], got: " +
          value.getClass().getName());
    }

    int copyLen = Math.min(len, src.length);
    buffer.put(src, 0, copyLen);
    zeroBytes(buffer, len - copyLen);
  }

  private void encodeTypedArray(MavlinkCompiledField compiledField, ByteBuffer buffer, List<?> elements) throws IOException {
    MavlinkFieldDefinition field = compiledField.getFieldDefinition();
    AbstractMavlinkFieldCodec codec = compiledField.getFieldCodec();

    int len = field.getArrayLength();
    int count = Math.min(len, elements.size());

    try {
      codec.encode(buffer, elements);
    } catch (Exception e) {
      e.printStackTrace();

      throw new IOException("Failed to encode array field '" + field.getName() + "'", e);
    }

    int remaining = len - count;
    if (remaining > 0) {
      int elementSize = field.getWireType().getSizeInBytes();
      zeroBytes(buffer, remaining * elementSize);
    }
  }

  private List<?> toElements(Object value, String fieldName) throws IOException {
    if (value instanceof List<?> list) {
      return list;
    }
    if (value != null && value.getClass().isArray()) {
      int arrayLen = java.lang.reflect.Array.getLength(value);
      List<Object> tmp = new ArrayList<>(arrayLen);
      for (int i = 0; i < arrayLen; i++) {
        tmp.add(java.lang.reflect.Array.get(value, i));
      }
      return tmp;
    }
    throw new IOException("Array field '" + fieldName + "' expects List or array, got: " +
        (value == null ? "null" : value.getClass().getName()));
  }

  private void zeroFill(MavlinkCompiledField compiledField, ByteBuffer buffer) {
    zeroBytes(buffer, compiledField.getSizeInBytes());
  }

  private void zeroBytes(ByteBuffer buffer, int count) {
    for (int i = 0; i < count; i++) {
      buffer.put((byte) 0);
    }
  }
}
