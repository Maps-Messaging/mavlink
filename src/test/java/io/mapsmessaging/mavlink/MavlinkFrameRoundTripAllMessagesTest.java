/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import io.mapsmessaging.mavlink.signing.MapSigningKeyProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkFrameRoundTripAllMessagesTest extends BaseRoudTripTest {

  private static final byte[] TEST_SIGNING_KEY = buildTestSigningKey();

  private static byte[] buildTestSigningKey() {
    Random random = new Random();
    byte[] key = new byte[32];
    random.nextBytes(key);
    return key;
  }

  @TestFactory
  Stream<DynamicTest> allMessages_frame_roundTrip_v2_unsigned_fullPayload() throws Exception {
    return buildRoundTripTests(false);
  }

  @TestFactory
  Stream<DynamicTest> allMessages_frame_roundTrip_v2_signed_fullPayload() throws Exception {
    return buildRoundTripTests(true);
  }

  private Stream<DynamicTest> buildRoundTripTests(boolean signFrames) throws Exception {
    MavlinkCodec payloadCodec = MavlinkTestSupport.codec();


    MapSigningKeyProvider signingKeyProvider = new MapSigningKeyProvider();
    signingKeyProvider.register (1, 1, 0, TEST_SIGNING_KEY);
    MavlinkFrameCodec frameCodec = new MavlinkFrameCodec(payloadCodec, signingKeyProvider);

    MessageRegistry registry = payloadCodec.getRegistry();

    String suffix = signFrames ? "signed" : "unsigned";

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName() + " (" + suffix + ")",
            () -> frameRoundTripForMessageV2(frameCodec, payloadCodec, registry, msg, signFrames)
        ));
  }

  private static void frameRoundTripForMessageV2(
      MavlinkFrameCodec frameCodec,
      MavlinkCodec payloadCodec,
      MessageRegistry registry,
      CompiledMessage msg,
      boolean signFrame
  ) throws Exception {

    Map<String, Object> values =
        RandomValueFactory.buildValues(registry, msg, MavlinkRoundTripAllMessagesTest.ExtensionMode.SOME_PRESENT, BASE_SEED);

    byte[] payload = payloadCodec.encodePayload(msg.getMessageId(), values);
    assertNotNull(payload);

    Frame frame = new Frame();
    frame.setVersion(Version.V2);
    frame.setSequence(42);
    frame.setSystemId(1);
    frame.setComponentId(1);
    frame.setMessageId(msg.getMessageId());
    frame.setPayload(payload);
    frame.setPayloadLength(payload.length);

    frame.setSigned(signFrame);
    frame.setIncompatibilityFlags(signFrame ? (byte) 1 : (byte) 0);
    frame.setCompatibilityFlags((byte) 0);
    frame.setSignature(null);

    ByteBuffer out = ByteBuffer.allocate(payload.length + 128);
    frameCodec.packFrame(out, frame);

    out.flip();

    ByteBuffer networkOwned = ByteBuffer.allocate(out.remaining() + 16);
    networkOwned.put(out);
    networkOwned.flip();

    Optional<Frame> decodedOpt = frameCodec.tryUnpackFrame(networkOwned);
    assertTrue(decodedOpt.isPresent(), "Expected to decode a frame");

    Frame decoded = decodedOpt.get();

    if (signFrame) {
      assertTrue(decoded.isSigned(), "Expected signed frame");
      assertEquals(FrameFailureReason.OK, decoded.getValidated(),  "Expected signature validation to succeed");
      assertNotNull(decoded.getSignature(), "Expected signature bytes");
      assertEquals(13, decoded.getSignature().length, "Expected 13-byte MAVLink v2 signature block");
    } else {
      assertFalse(decoded.isSigned(), "Expected unsigned frame");
      assertNotEquals(FrameFailureReason.OK, decoded.getValidated(),  "Expected validated=false for unsigned frame");
      assertNull(decoded.getSignature(), "Expected signature to be null for unsigned frame");
    }

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

      FieldDefinition fieldDefinition = RandomValueFactory.fieldByName(msg, fieldName);
      ValueAssertions.assertFieldEquals(fieldDefinition, expected, actual, msg);
    }
  }
}
