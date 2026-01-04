package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkFrame;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.MavlinkVersion;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkFrameRoundTripAllMessagesTest extends BaseRoudTripTest {

  @TestFactory
  Stream<DynamicTest> allMessages_frame_roundTrip_v2_fullPayload() throws Exception {
    MavlinkCodec payloadCodec = MavlinkTestSupport.codec();
    MavlinkFrameCodec frameCodec = new MavlinkFrameCodec(payloadCodec);
    MavlinkMessageRegistry registry = payloadCodec.getRegistry();

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> frameRoundTripForMessageV2(frameCodec, payloadCodec, registry, msg)
        ));
  }

  private static void frameRoundTripForMessageV2(
      MavlinkFrameCodec frameCodec,
      MavlinkCodec payloadCodec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg
  ) throws Exception {

    Map<String, Object> values =
        RandomValueFactory.buildValues(registry, msg, MavlinkRoundTripAllMessagesTest.ExtensionMode.SOME_PRESENT, BASE_SEED);

    byte[] payload = payloadCodec.encodePayload(msg.getMessageId(), values);
    assertNotNull(payload);

    MavlinkFrame frame = new MavlinkFrame();
    frame.setVersion(MavlinkVersion.V2);
    frame.setSequence(42);
    frame.setSystemId(1);
    frame.setComponentId(1);
    frame.setMessageId(msg.getMessageId());
    frame.setPayload(payload);
    frame.setPayloadLength(payload.length);

    frame.setSigned(false);
    frame.setIncompatibilityFlags((byte) 0);
    frame.setCompatibilityFlags((byte) 0);
    frame.setSignature(null);

    ByteBuffer out = ByteBuffer.allocate(payload.length + 128);
    frameCodec.packFrame(out, frame);

    out.flip();

    ByteBuffer networkOwned = ByteBuffer.allocate(out.remaining() + 16);
    networkOwned.put(out);
    networkOwned.flip();
    Optional<MavlinkFrame> decodedOpt = frameCodec.tryUnpackFrame(networkOwned);
    assertTrue(decodedOpt.isPresent(), "Expected to decode a frame");

    MavlinkFrame decoded = decodedOpt.get();

    assertEquals(frame.getVersion(), decoded.getVersion());
    assertEquals(frame.getSequence(), decoded.getSequence());
    assertEquals(frame.getSystemId(), decoded.getSystemId());
    assertEquals(frame.getComponentId(), decoded.getComponentId());
    assertEquals(frame.getMessageId(), decoded.getMessageId());

    assertNotNull(decoded.getPayload());
    assertArrayEquals(frame.getPayload(), decoded.getPayload(), "Payload bytes mismatch");

    Map<String, Object> output = payloadCodec.parsePayload(decoded.getMessageId(), decoded.getPayload());

    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String fieldName = entry.getKey();
      Object expected = entry.getValue();
      Object actual = output.get(fieldName);
      assertNotNull(actual, "Missing field after decode: " + fieldName);

      MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, fieldName);
      ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
    }
  }
}
