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
 *
 *
 */
package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.MavlinkMessageFormatLoader;
import io.mapsmessaging.mavlink.codec.MavlinkFrameCodec;
import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.signing.NoSigningKeyProvider;
import io.mapsmessaging.mavlink.signing.StaticSigningKeyProvider;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavlinkWireConformanceTest {

  private static final DialectRegistry DIALECT = new DialectRegistry() {
    @Override
    public int crcExtra(Version version, int messageId) {
      return 50;
    }

    @Override
    public int minimumPayloadLength(Version version, int messageId) {
      return 2;
    }
  };

  private static Frame frame(Version version, byte[] payload, boolean signed) {
    Frame frame = new Frame();
    frame.setVersion(version);
    frame.setMessageId(42);
    frame.setSystemId(1);
    frame.setComponentId(2);
    frame.setSequence(3);
    frame.setPayload(payload);
    frame.setPayloadLength(payload.length);
    frame.setSigned(signed);
    return frame;
  }

  private static byte[] pack(FramePacker packer, Frame frame) {
    ByteBuffer output = ByteBuffer.allocate(300);
    packer.pack(output, frame);
    return Arrays.copyOf(output.array(), output.position());
  }

  @Test
  void mavlink1MatchesIndependentCrcVectorAndRemainsUnsigned() {
    // X25 CRC over 02 03 01 02 2A 09 08 with CRC_EXTRA 0x32 = 0xDE9C.
    Frame packet = frame(Version.V1, new byte[]{9, 8}, false);
    byte[] wire = pack(new FramePacker(DIALECT, null), packet);
    assertArrayEquals(new byte[]{(byte) 0xFE, 2, 3, 1, 2, 42, 9, 8, (byte) 0x9C, (byte) 0xDE}, wire);
    Frame decoded = new V1FrameHandler(DIALECT).tryDecode(ByteBuffer.wrap(wire)).orElseThrow();
    assertEquals(FrameFailureReason.OK, decoded.getValidated());
    assertFalse(decoded.isSigned());
  }

  @Test
  void mavlink2TrimsTrailingZeroBytesButRetainsTheFirstByte() {
    Frame packet = frame(Version.V2, new byte[]{9, 0, 0}, false);
    byte[] wire = pack(new FramePacker(DIALECT, null), packet);
    assertEquals(1, wire[1] & 0xFF);
    assertEquals(13, wire.length);
    Frame decoded = new V2FrameHandler(DIALECT, new NoSigningKeyProvider()).tryDecode(ByteBuffer.wrap(wire)).orElseThrow();
    assertEquals(FrameFailureReason.UNSIGNED, decoded.getValidated());
    assertArrayEquals(new byte[]{9}, decoded.getPayload());

    Frame empty = frame(Version.V2, new byte[]{0, 0}, false);
    byte[] emptyWire = pack(new FramePacker(DIALECT, null), empty);
    assertEquals(1, emptyWire[1] & 0xFF);
    assertEquals(0, emptyWire[10] & 0xFF);
  }

  @Test
  void decoderAcceptsOtherwiseValidUntrimmedUnsignedV2Payloads() {
    // CRC of len=2, seq=3, sys=1, comp=2, msg=42, payload=09 00, extra=0x32 is 0xDF78.
    byte[] untrimmed = {(byte) 0xFD, 2, 0, 0, 3, 1, 2, 42, 0, 0, 9, 0, 0x78, (byte) 0xDF};
    Frame decoded = new V2FrameHandler(DIALECT, new NoSigningKeyProvider()).tryDecode(ByteBuffer.wrap(untrimmed)).orElseThrow();
    assertEquals(FrameFailureReason.UNSIGNED, decoded.getValidated());
    assertArrayEquals(new byte[]{9, 0}, decoded.getPayload());
  }

  @Test
  void signatureMatchesIndependentSha256VectorAndAuthenticatesMetadata() {
    byte[] key = new byte[32];
    for (int index = 0; index < key.length; index++) {
      key[index] = (byte) index;
    }
    // Reference: SHA-256(key + FD0201000301022A000009082944 + 0540E201000000), first 6 bytes.
    byte[] packet = {(byte) 0xFD, 2, 1, 0, 3, 1, 2, 42, 0, 0, 9, 8, 0x29, 0x44};
    byte[] expected = {5, 0x40, (byte) 0xE2, 1, 0, 0, 0, 0x0A, (byte) 0x88, (byte) 0xAF, 0x28, 0x39, (byte) 0xA8};
    assertArrayEquals(expected, V2SignatureGenerator.buildSignature(ByteBuffer.wrap(packet), 0, packet.length - 2, 5, 123456, key));
    assertFalse(Arrays.equals(expected, V2SignatureGenerator.buildSignature(ByteBuffer.wrap(packet), 0, packet.length - 2, 6, 123456, key)));
    assertFalse(Arrays.equals(expected, V2SignatureGenerator.buildSignature(ByteBuffer.wrap(packet), 0, packet.length - 2, 5, 123457, key)));
  }

  @Test
  void authenticatedPacketsRejectReplayButLeaveUnsignedTrafficWorking() {
    StaticSigningKeyProvider signingKeys = new StaticSigningKeyProvider(new byte[32]);
    FramePacker packer = new FramePacker(DIALECT, signingKeys);
    byte[] signed = pack(packer, frame(Version.V2, new byte[]{9, 8}, true));
    byte[] next = pack(packer, frame(Version.V2, new byte[]{9, 8}, true));
    V2FrameHandler handler = new V2FrameHandler(DIALECT, signingKeys);

    assertEquals(FrameFailureReason.OK, handler.tryDecode(ByteBuffer.wrap(signed)).orElseThrow().getValidated());
    assertEquals(FrameFailureReason.SIGNATURE_FAILED, handler.tryDecode(ByteBuffer.wrap(signed)).orElseThrow().getValidated());
    assertEquals(FrameFailureReason.OK, handler.tryDecode(ByteBuffer.wrap(next)).orElseThrow().getValidated());

    byte[] tamperedTimestamp = next.clone();
    tamperedTimestamp[tamperedTimestamp.length - 12] ^= 1;
    assertEquals(FrameFailureReason.SIGNATURE_FAILED,
        new V2FrameHandler(DIALECT, signingKeys).tryDecode(ByteBuffer.wrap(tamperedTimestamp)).orElseThrow().getValidated());

    byte[] unsigned = pack(new FramePacker(DIALECT, null), frame(Version.V2, new byte[]{9, 8}, false));
    assertEquals(FrameFailureReason.UNSIGNED, handler.tryDecode(ByteBuffer.wrap(unsigned)).orElseThrow().getValidated());
    assertEquals(FrameFailureReason.UNKNOWN,
        new V2FrameHandler(DIALECT, new NoSigningKeyProvider()).tryDecode(ByteBuffer.wrap(signed)).orElseThrow().getValidated());
  }

  @Test
  void trimmedCommandAckRetainsItsLowByte() throws Exception {
    MavlinkFrameCodec codec = new MavlinkFrameCodec(MavlinkMessageFormatLoader.getInstance().getDialectOrThrow("common"));
    Frame commandAck = frame(Version.V2, new byte[]{1, 0, 0}, false);
    commandAck.setMessageId(77);
    ByteBuffer wire = ByteBuffer.allocate(300);
    codec.packFrame(wire, commandAck);
    wire.flip();

    Frame decoded = codec.tryUnpackFrame(wire).orElseThrow();
    assertEquals(FrameFailureReason.UNSIGNED, decoded.getValidated());
    assertEquals(1, decoded.getPayloadLength());
    assertEquals(1, ((Number) codec.parsePayload(decoded).get("command")).intValue());
    assertTrue(codec.parsePayload(decoded).containsKey("result"));
  }
  @Test
  void mavlink1BasePayloadWithExtensionCapableMessageStillDecodes() throws Exception {
    MavlinkFrameCodec codec = new MavlinkFrameCodec(MavlinkMessageFormatLoader.getInstance().getDialectOrThrow("common"));
    Frame commandAck = frame(Version.V1, new byte[]{1, 0, 0}, false);
    commandAck.setMessageId(77);
    ByteBuffer wire = ByteBuffer.allocate(64);
    codec.packFrame(wire, commandAck);
    wire.flip();

    Frame decoded = codec.tryUnpackFrame(wire).orElseThrow();
    assertEquals(FrameFailureReason.OK, decoded.getValidated());
    assertEquals(1, ((Number) codec.parsePayload(decoded).get("command")).intValue());
  }

  @Test
  void unknownIncompatibilityBitsAndZeroSenderIdsAreRejected() {
    Frame signed = frame(Version.V2, new byte[]{9, 8}, false);
    signed.setIncompatibilityFlags((byte) 0x02);
    byte[] unknownFlag = pack(new FramePacker(DIALECT, null), signed);
    V2FrameHandler handler = new V2FrameHandler(DIALECT, new NoSigningKeyProvider());
    assertTrue(handler.tryDecode(ByteBuffer.wrap(unknownFlag)).isEmpty());

    signed.setIncompatibilityFlags((byte) 0);
    signed.setCompatibilityFlags((byte) 0x80);
    byte[] knownSafe = pack(new FramePacker(DIALECT, null), signed);
    assertEquals(FrameFailureReason.UNSIGNED, handler.tryDecode(ByteBuffer.wrap(knownSafe)).orElseThrow().getValidated());

    signed.setSystemId(0);
    byte[] invalidSource = pack(new FramePacker(DIALECT, null), signed);
    assertTrue(handler.tryDecode(ByteBuffer.wrap(invalidSource)).isEmpty());
  }

  @Test
  void concurrentCopiesOfOneSignedPacketAreOnlyAuthenticatedOnce() throws Exception {
    StaticSigningKeyProvider keys = new StaticSigningKeyProvider(new byte[32]);
    byte[] packet = pack(new FramePacker(DIALECT, keys), frame(Version.V2, new byte[]{9, 8}, true));
    V2FrameHandler handler = new V2FrameHandler(DIALECT, keys);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<FrameFailureReason>> results = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        results.add(executor.submit(() -> {
          start.await();
          return handler.tryDecode(ByteBuffer.wrap(packet)).orElseThrow().getValidated();
        }));
      }
      start.countDown();
      int authenticated = 0;
      for (Future<FrameFailureReason> result : results) {
        if (result.get() == FrameFailureReason.OK) {
          authenticated++;
        } else {
          assertEquals(FrameFailureReason.SIGNATURE_FAILED, result.get());
        }
      }
      assertEquals(1, authenticated);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

}
