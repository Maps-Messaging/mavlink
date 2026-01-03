package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MavlinkTestSupport {

  private MavlinkTestSupport() {
  }

  public static MavlinkCodec codec() throws Exception {
    MavlinkMessageFormatLoader loader = MavlinkMessageFormatLoader.getInstance();
    return loader.getDialectOrThrow("common");
  }

  public static MavlinkMessageRegistry registry(MavlinkCodec codec) {
    return codec.getRegistry();
  }

  public static MavlinkFieldDefinition fieldDefinition(MavlinkCompiledField compiledField) {
    try {
      Field field = MavlinkCompiledField.class.getDeclaredField("fieldDefinition");
      field.setAccessible(true);
      return (MavlinkFieldDefinition) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.fieldDefinition", exception);
    }
  }

  public static int offset(MavlinkCompiledField compiledField) {
    try {
      Field field = MavlinkCompiledField.class.getDeclaredField("offsetInPayload");
      field.setAccessible(true);
      return (int) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.offsetInPayload", exception);
    }
  }

  public static int size(MavlinkCompiledField compiledField) {
    try {
      Field field = MavlinkCompiledField.class.getDeclaredField("sizeInBytes");
      field.setAccessible(true);
      return (int) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.sizeInBytes", exception);
    }
  }

  public static Optional<MavlinkCompiledMessage> firstMessageWithExtensions(MavlinkMessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> fieldDefinition(field).isExtension()))
        .findFirst();
  }

  public static Optional<MavlinkCompiledMessage> firstMessageWithArray(MavlinkMessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> fieldDefinition(field).isArray()))
        .findFirst();
  }

  public static Optional<MavlinkCompiledMessage> firstMessageWithEnum(MavlinkMessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> {
          MavlinkFieldDefinition definition = fieldDefinition(field);
          String enumName = definition.getEnumName();
          return enumName != null && !enumName.isEmpty();
        }))
        .findFirst();
  }

  public static Map<String, Object> payloadWithScalar(MavlinkFieldDefinition field, Number value) {
    return Map.of(field.getName(), value);
  }

  public static Map<String, Object> payloadWithArrayFilled(MavlinkFieldDefinition field, Number value) {
    int arrayLength = field.getArrayLength();
    List<Number> list = java.util.stream.IntStream.range(0, arrayLength)
        .mapToObj(index -> value)
        .toList();
    return Map.of(field.getName(), list);
  }

  public static List<MavlinkCompiledField> fieldsSortedByOffset(MavlinkCompiledMessage message) {
    return message.getCompiledFields().stream()
        .sorted(Comparator.comparingInt(MavlinkTestSupport::offset))
        .toList();
  }

  public static String requireNonEmpty(String value, String message) {
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException(message);
    }
    return value;
  }
}
