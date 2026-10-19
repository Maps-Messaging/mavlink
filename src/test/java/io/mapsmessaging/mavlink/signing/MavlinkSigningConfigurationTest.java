/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mapsmessaging.mavlink.signing;

import io.mapsmessaging.mavlink.MavlinkMessageFormatLoader;
import io.mapsmessaging.mavlink.codec.MavlinkCodec;
import io.mapsmessaging.mavlink.codec.MavlinkFrameCodec;
import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavlinkSigningConfigurationTest {

  private static Frame frame(Version version, boolean signed) {
    Frame frame = new Frame();
    frame.setVersion(version);
    frame.setMessageId(77);
    frame.setSequence(3);
    frame.setSystemId(1);
    frame.setComponentId(2);
    frame.setPayload(new byte[]{1, 0, 0});
    frame.setPayloadLength(3);
    frame.setSigned(signed);
    return frame;
  }

  private static MavlinkCodec common() throws Exception {
    return MavlinkMessageFormatLoader.getInstance().getDialectOrThrow("common");
  }

  private static byte[] pack(MavlinkFrameCodec codec, Frame frame) {
    ByteBuffer packet = ByteBuffer.allocate(280);
    codec.packFrame(packet, frame);
    return Arrays.copyOf(packet.array(), packet.position());
  }

  @Test
  void legacyConstructorsContinueToAcceptUnsignedV1AndV2() throws Exception {
    MavlinkFrameCodec codec = new MavlinkFrameCodec(common());
    for (Version version : new Version[]{Version.V1, Version.V2}) {
      byte[] packet = pack(codec, frame(version, false));
      Frame received = codec.tryUnpackFrame(ByteBuffer.wrap(packet)).orElseThrow();
      assertFalse(received.isSigned());
      assertTrue(received.getValidated() == FrameFailureReason.OK ||
          received.getValidated() == FrameFailureReason.UNSIGNED);
      assertEquals(1, ((Number) codec.parsePayload(received).get("command")).intValue());
    }
  }

  @Test
  void requiredSigningRejectsUnsignedV1AndV2WithoutBreakingDefault() throws Exception {
    MavlinkSigningConfig required = new MavlinkSigningConfig(MavlinkSigningPolicy.REQUIRED, 8, new SigningState());
    MavlinkFrameCodec receiver = new MavlinkFrameCodec(common(), new StaticSigningKeyProvider(new byte[32]), required);
    MavlinkFrameCodec legacy = new MavlinkFrameCodec(common());
    for (Version version : new Version[]{Version.V1, Version.V2}) {
      byte[] packet = pack(legacy, frame(version, false));
      assertEquals(FrameFailureReason.UNSIGNED_REJECTED,
          receiver.tryUnpackFrame(ByteBuffer.wrap(packet)).orElseThrow().getValidated());
      assertTrue(receiver.tryUnpackHeaderAndPayload(ByteBuffer.wrap(packet)).isEmpty());
      assertTrue(legacy.tryUnpackHeaderAndPayload(ByteBuffer.wrap(packet)).isPresent());
    }
  }

  @Test
  void configuredLinkIdIsSignedAndSharedReplayHistorySurvivesCodecReload() throws Exception {
    StaticSigningKeyProvider keys = new StaticSigningKeyProvider(new byte[32]);
    SigningState sender = new SigningState();
    SigningState receiverState = new SigningState();
    MavlinkSigningConfig senderConfig = new MavlinkSigningConfig(MavlinkSigningPolicy.OPTIONAL, 17, sender);
    MavlinkSigningConfig receiverConfig = new MavlinkSigningConfig(MavlinkSigningPolicy.REQUIRED, 17, receiverState);
    MavlinkFrameCodec sending = new MavlinkFrameCodec(common(), keys, senderConfig);
    MavlinkFrameCodec receiving = new MavlinkFrameCodec(common(), keys, receiverConfig);

    byte[] first = pack(sending, frame(Version.V2, true));
    assertEquals(17, first[first.length - 13] & 0xFF);
    assertEquals(FrameFailureReason.OK, receiving.tryUnpackFrame(ByteBuffer.wrap(first)).orElseThrow().getValidated());
    assertEquals(FrameFailureReason.SIGNATURE_REPLAY,
        new MavlinkFrameCodec(common(), keys, receiverConfig).tryUnpackFrame(ByteBuffer.wrap(first)).orElseThrow().getValidated());

    byte[] second = pack(new MavlinkFrameCodec(common(), keys, senderConfig), frame(Version.V2, true));
    assertEquals(FrameFailureReason.OK, receiving.tryUnpackFrame(ByteBuffer.wrap(second)).orElseThrow().getValidated());

    byte[] unsigned = pack(new MavlinkFrameCodec(common()), frame(Version.V2, false));
    assertEquals(FrameFailureReason.UNSIGNED,
        new MavlinkFrameCodec(common(), keys, new MavlinkSigningConfig(MavlinkSigningPolicy.OPTIONAL, 17, receiverState))
            .tryUnpackFrame(ByteBuffer.wrap(unsigned)).orElseThrow().getValidated());
  }

  @Test
  void missingKeyNeverAuthenticatesSignedPackets() throws Exception {
    StaticSigningKeyProvider keys = new StaticSigningKeyProvider(new byte[32]);
    MavlinkFrameCodec sender = new MavlinkFrameCodec(common(), keys);
    byte[] packet = pack(sender, frame(Version.V2, true));
    Frame unverified = new MavlinkFrameCodec(common()).tryUnpackFrame(ByteBuffer.wrap(packet)).orElseThrow();
    assertEquals(FrameFailureReason.SIGNATURE_UNVERIFIED, unverified.getValidated());
    assertTrue(new MavlinkFrameCodec(common()).tryUnpackHeaderAndPayload(ByteBuffer.wrap(packet)).isEmpty());
  }

  @Test
  void checkpointRestoresMonotonicAndReplayStateAndRejectsCorruption() throws Exception {
    Path directory = Path.of("target", "data", "mavlink-signing-state-test");
    Files.createDirectories(directory);
    Path path = directory.resolve("signing-" + java.util.UUID.randomUUID() + ".bin");
    FileSigningStateStore store = new FileSigningStateStore(path);
    try {
      SigningState state = new SigningState(store);
      long outgoing = state.nextOutgoing(1, 2, 5);
      long nowTicks = (System.currentTimeMillis() - 1_420_070_400_000L) * 100L;
      assertEquals(FrameFailureReason.OK, state.acceptIncoming(1, 2, 7, nowTicks));
      state.close();

      SigningState restored = new SigningState(store);
      assertTrue(restored.nextOutgoing(1, 2, 5) > outgoing);
      assertEquals(FrameFailureReason.SIGNATURE_REPLAY, restored.acceptIncoming(1, 2, 7, nowTicks));
      assertEquals(FrameFailureReason.SIGNATURE_STALE, restored.acceptIncoming(1, 2, 8, nowTicks - 6_000_100));
      restored.close();

      byte[] bytes = Files.readAllBytes(path);
      bytes[bytes.length - 1] ^= 1;
      Files.write(path, bytes);
      assertThrows(IOException.class, () -> new SigningState(store));
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void validatesSigningConfigurationAndSnapshotCopies() {
    assertThrows(IllegalArgumentException.class,
        () -> new MavlinkSigningConfig(MavlinkSigningPolicy.REQUIRED, -1, new SigningState()));
    assertThrows(IllegalArgumentException.class,
        () -> new MavlinkSigningConfig(MavlinkSigningPolicy.REQUIRED, 256, new SigningState()));
    Map<SigningStreamId, Long> mutable = new java.util.HashMap<>();
    mutable.put(new SigningStreamId(1, 2, 3), 9L);
    SigningStateSnapshot snapshot = new SigningStateSnapshot(mutable, Map.of(), 9L);
    mutable.clear();
    assertEquals(1, snapshot.outbound().size());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.outbound().clear());
  }
}
