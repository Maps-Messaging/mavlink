package io.mapsmessaging.mavlink.codec;

import io.mapsmessaging.mavlink.MavlinkFrameEnvelope;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MavlinkFrameCodecTest {

  @Test
  void packsAndDecodesHeaderThenDelegatesPayloadOperations() throws Exception {
    MessageRegistry registry = mock(MessageRegistry.class);
    CompiledMessage compiled = new CompiledMessage();
    MessageDefinition definition = new MessageDefinition();
    definition.setExtraCrc(50);
    compiled.setMessageDefinition(definition);
    compiled.setPayloadSizeBytes(1);
    compiled.setMinimumPayloadSizeBytes(1);
    when(registry.getCompiledMessagesById()).thenReturn(Map.of(42, compiled));
    PayloadPacker payloadPacker = mock(PayloadPacker.class);
    PayloadParser payloadParser = mock(PayloadParser.class);
    when(payloadPacker.packPayload(eq(42), any())).thenReturn(new byte[]{7});
    when(payloadParser.parsePayload(eq(42), any())).thenReturn(Map.of("value", 7));
    MavlinkCodec payloadCodec = new MavlinkCodec("test", registry, payloadPacker, payloadParser);
    MavlinkFrameCodec codec = new MavlinkFrameCodec(payloadCodec);

    assertEquals("test", codec.getDialectName());
    assertSame(registry, codec.getRegistry());
    Frame frame = new Frame();
    frame.setVersion(Version.V2);
    frame.setSystemId(1);
    frame.setComponentId(2);
    frame.setSequence(3);
    codec.encodePayloadIntoFrame(frame, 42, Map.of("value", 7));
    assertArrayEquals(new byte[]{7}, frame.getPayload());
    assertEquals(42, frame.getMessageId());
    assertEquals(1, frame.getPayloadLength());
    assertArrayEquals(new byte[]{7}, codec.encodePayload(42, Map.of("value", 7)));
    assertEquals(Map.of("value", 7), codec.parsePayload(frame));

    ByteBuffer output = ByteBuffer.allocate(64);
    codec.packFrame(output, frame);
    byte[] wire = new byte[output.position()];
    output.flip();
    output.get(wire);
    ByteBuffer networkBuffer = ByteBuffer.allocate(64);
    networkBuffer.put(wire);
    networkBuffer.flip();
    MavlinkFrameEnvelope envelope = codec.tryUnpackHeaderAndPayload(networkBuffer).orElseThrow();
    assertEquals(42, envelope.getMessageId());
    assertArrayEquals(new byte[]{7}, envelope.getPayload());
    assertTrue(codec.tryUnpackFrame(networkBuffer).isEmpty());
  }

  @Test
  void rejectsNullAndUnknownPayloads() throws IOException {
    assertThrows(NullPointerException.class, () -> new MavlinkFrameCodec(null));
    MessageRegistry registry = mock(MessageRegistry.class);
    when(registry.getCompiledMessagesById()).thenReturn(Map.of());
    MavlinkCodec payloadCodec = new MavlinkCodec("test", registry, mock(PayloadPacker.class), mock(PayloadParser.class));
    MavlinkFrameCodec codec = new MavlinkFrameCodec(payloadCodec);
    assertThrows(NullPointerException.class, () -> codec.parsePayload(null));
    assertThrows(NullPointerException.class, () -> codec.parsePayload(new Frame()));
    assertThrows(NullPointerException.class, () -> codec.encodePayloadIntoFrame(null, 42, Map.of()));
    assertThrows(NullPointerException.class, () -> codec.encodePayloadIntoFrame(new Frame(), 42, null));
    assertTrue(codec.tryUnpackHeaderAndPayload(ByteBuffer.allocate(32)).isEmpty());
    Frame unknown = new Frame();
    unknown.setVersion(Version.V2);
    unknown.setMessageId(99);
    unknown.setPayload(new byte[]{1});
    unknown.setPayloadLength(1);
    assertThrows(IllegalArgumentException.class, () -> codec.packFrame(ByteBuffer.allocate(32), unknown));
  }
}
