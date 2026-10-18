package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.signing.MapSigningKeyProvider;
import io.mapsmessaging.mavlink.signing.StaticSigningKeyProvider;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class V2FrameSigningTest {

  private static final byte[] KEY = new byte[32];

  @Test
  void signsAndVerifiesWithNonzeroBufferPosition() {
    MapSigningKeyProvider keys = new MapSigningKeyProvider();
    keys.register(2, 3, 7, KEY);

    DialectRegistry dialect = new DialectRegistry() {
      @Override
      public int crcExtra(io.mapsmessaging.mavlink.message.Version version, int messageId) {
        return 50;
      }

      @Override
      public int minimumPayloadLength(io.mapsmessaging.mavlink.message.Version version, int messageId) {
        return 2;
      }
    };

    io.mapsmessaging.mavlink.message.Frame frame = new io.mapsmessaging.mavlink.message.Frame();
    frame.setVersion(io.mapsmessaging.mavlink.message.Version.V2);
    frame.setMessageId(42);
    frame.setSequence(1);
    frame.setSystemId(2);
    frame.setComponentId(3);
    frame.setPayload(new byte[]{9, 8});
    frame.setPayloadLength(2);
    frame.setSigned(true);

    io.mapsmessaging.mavlink.signing.MavlinkSigningConfig signingConfig =
        new io.mapsmessaging.mavlink.signing.MavlinkSigningConfig(
            io.mapsmessaging.mavlink.signing.MavlinkSigningPolicy.OPTIONAL,
            7,
            new io.mapsmessaging.mavlink.signing.SigningState());

    ByteBuffer output = ByteBuffer.allocate(64);
    output.position(2);
    new FramePacker(dialect, keys, signingConfig).pack(output, frame);

    assertEquals(2 + 10 + 2 + 2 + 13, output.position());
    assertEquals(7, output.get(output.position() - 13) & 0xff);
    assertTrue(V2FrameSigning.validateSignature(output, 2, 14, 2, 3, keys));
    assertFalse(V2FrameSigning.validateSignature(output, 2, 14, 2, 4, keys));

    output.put(6, (byte) (output.get(6) ^ 1));
    assertFalse(V2FrameSigning.validateSignature(output, 2, 14, 2, 3, keys));
    keys.unregister(2, 3, 7);
    assertNull(keys.getSigningKey(2, 3, 7));
  }

  @Test
  void validatesInputsAndDefensivelyCopiesStaticKey() {
    assertThrows(IllegalArgumentException.class, () -> new StaticSigningKeyProvider(null));
    assertThrows(IllegalArgumentException.class, () -> new StaticSigningKeyProvider(new byte[31]));
    byte[] input = Arrays.copyOf(KEY, KEY.length);
    input[0] = 42;
    StaticSigningKeyProvider provider = new StaticSigningKeyProvider(input);
    input[0] = 0;
    assertEquals(42, provider.getSigningKey(1, 2, 3)[0]);
    byte[] returned = provider.getSigningKey(1, 2, 3);
    returned[0] = 0;
    assertEquals(42, provider.getSigningKey(1, 2, 3)[0]);
    assertTrue(provider.canValidate());

    ByteBuffer packet = ByteBuffer.wrap(new byte[]{(byte) 0xfd, 0, 0, 0, 1, 2, 3, 4, 0, 0, 0, 0, 0});
    assertThrows(IllegalArgumentException.class, () -> V2SignatureGenerator.buildSignature(packet, 0, 11, 7, 1L, null));
    assertThrows(IllegalArgumentException.class, () -> V2SignatureGenerator.buildSignature(packet, 0, 11, 7, 1L, new byte[31]));
    assertThrows(IllegalArgumentException.class, () -> V2SignatureGenerator.buildSignature(packet, 0, 11, -1, 1L, KEY));
    assertThrows(IllegalArgumentException.class, () -> V2SignatureGenerator.buildSignature(packet, 0, 11, 256, 1L, KEY));
  }
}
