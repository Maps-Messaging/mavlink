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
    byte[] frame = { (byte) 0xfd, 0, 0, 0, 1, 2, 3, 4, 0, 0, 0, 0x34, 0x12 };
    ByteBuffer source = ByteBuffer.wrap(new byte[frame.length + 4]);
    source.position(2);
    source.put(frame);
    source.flip();
    source.position(2);

    ByteBuffer signed = V2FrameSigning.appendSignature(source, 7, 0x123456789abL, KEY);
    assertEquals(2, source.position());
    assertEquals(frame.length + 13, signed.remaining());
    assertEquals(7, signed.get(frame.length) & 0xff);

    MapSigningKeyProvider keys = new MapSigningKeyProvider();
    keys.register(2, 3, 7, KEY);
    assertTrue(V2FrameSigning.validateSignature(signed, 0, frame.length - 2, 2, 3, keys));
    assertFalse(V2FrameSigning.validateSignature(signed, 0, frame.length - 2, 2, 4, keys));
    signed.put(4, (byte) (signed.get(4) ^ 1));
    assertFalse(V2FrameSigning.validateSignature(signed, 0, frame.length - 2, 2, 3, keys));
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

    assertThrows(IllegalArgumentException.class, () -> V2FrameSigning.appendSignature(null, 0, 0, KEY));
    assertThrows(IllegalArgumentException.class, () -> V2FrameSigning.appendSignature(ByteBuffer.allocate(12), 0, 0, null));
    assertThrows(IllegalArgumentException.class, () -> V2FrameSigning.appendSignature(ByteBuffer.allocate(12), 0, 0, new byte[31]));
    assertThrows(IllegalArgumentException.class, () -> V2FrameSigning.appendSignature(ByteBuffer.allocate(12), 0, 0, KEY));
  }
}
