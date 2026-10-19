package io.mapsmessaging.mavlink.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JsonCodecTest {

  @Test
  void validatesRequiredFieldsAndUnknownMessageId() throws Exception {
    PayloadPacker packer = mock(PayloadPacker.class);
    MapConverter converter = mock(MapConverter.class);
    MessageRegistry registry = mock(MessageRegistry.class);
    when(packer.getMessageRegistry()).thenReturn(registry);
    when(registry.getCompiledMessagesById()).thenReturn(Map.of());
    JsonCodec codec = new JsonCodec("test", packer, converter);

    assertThrows(IOException.class, () -> codec.fromJson(null));
    assertThrows(IOException.class, () -> codec.fromJson(new JsonObject()));
    JsonObject unknown = new JsonObject();
    unknown.addProperty("messageId", 99);
    assertThrows(IOException.class, () -> codec.fromJson(unknown));
    verify(packer, never()).packPayload(eq(99), anyMap());
  }

  @Test
  void convertsJsonToFrameWithExplicitAndDefaultHeaders() throws Exception {
    PayloadPacker packer = mock(PayloadPacker.class);
    MapConverter converter = mock(MapConverter.class);
    MessageRegistry registry = mock(MessageRegistry.class);
    CompiledMessage message = new CompiledMessage();
    message.setCompiledFields(List.of());
    MessageDefinition definition = new MessageDefinition();
    definition.setExtraCrc(50);
    message.setMessageDefinition(definition);
    when(packer.getMessageRegistry()).thenReturn(registry);
    when(registry.getCompiledMessagesById()).thenReturn(Map.of(42, message));
    when(packer.packPayload(eq(42), anyMap())).thenReturn(new byte[]{7});
    JsonCodec codec = new JsonCodec("test", packer, converter);

    JsonObject defaults = new JsonObject();
    defaults.addProperty("messageId", 42);
    byte[] defaultFrame = codec.fromJson(defaults);
    assertEquals(1, defaultFrame[5] & 0xff);
    assertEquals(1, defaultFrame[6] & 0xff);
    assertEquals(0, defaultFrame[4] & 0xff);
    assertEquals(7, defaultFrame[10] & 0xff);

    defaults.addProperty("systemId", 3);
    defaults.addProperty("componentId", 4);
    defaults.addProperty("sequence", 5);
    byte[] explicitFrame = codec.fromJson(defaults);
    assertEquals(3, explicitFrame[5] & 0xff);
    assertEquals(4, explicitFrame[6] & 0xff);
    assertEquals(5, explicitFrame[4] & 0xff);

    Frame frame = new Frame();
    when(converter.convert(frame)).thenReturn(Map.of("messageId", 42, "valid", true));
    assertEquals(42, codec.toJson(frame).get("messageId").getAsInt());
    assertTrue(codec.toJson(frame).get("valid").getAsBoolean());
  }
}
