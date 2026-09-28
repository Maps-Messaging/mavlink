package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.signing.StaticSigningKeyProvider;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class FramePackerTest {

  private final DialectRegistry dialect = new DialectRegistry() {
    @Override
    public int crcExtra(Version version, int messageId) {
      return 50;
    }

    @Override
    public int minimumPayloadLength(Version version, int messageId) {
      return 0;
    }
  };

  private Frame frame(Version version, boolean signed) {
    Frame frame = new Frame();
    frame.setVersion(version);
    frame.setMessageId(42);
    frame.setSystemId(1);
    frame.setComponentId(2);
    frame.setSequence(3);
    frame.setPayload(new byte[]{9, 8});
    frame.setPayloadLength(2);
    frame.setSigned(signed);
    return frame;
  }

  @Test
  void packsV1AndClearsV2Metadata() {
    Frame frame = frame(Version.V1, true);
    frame.setSignature(new byte[13]);
    ByteBuffer output = ByteBuffer.allocate(32);
    output.position(2);

    new FramePacker(dialect, null).pack(output, frame);

    assertEquals(13, output.position());
    assertEquals(0xfe, output.get(2) & 0xff);
    assertEquals(2, output.get(3) & 0xff);
    assertEquals(9, output.get(8) & 0xff);
    assertFalse(frame.isSigned());
    assertNull(frame.getSignature());
  }

  @Test
  void packsAndVerifiesSignedV2Frame() {
    byte[] key = new byte[32];
    StaticSigningKeyProvider provider = new StaticSigningKeyProvider(key);
    Frame frame = frame(Version.V2, true);
    ByteBuffer output = ByteBuffer.allocate(32);
    new FramePacker(dialect, provider).pack(output, frame);

    assertEquals(28, output.position());
    assertEquals(0xfd, output.get(0) & 0xff);
    assertEquals(1, output.get(2) & 0xff);
    assertEquals(13, frame.getSignature().length);
    output.flip();
    assertTrue(V2FrameSigning.validateSignature(output, 0, 13, 1, 2, provider));
  }

  @Test
  void rejectsInvalidFrameAndPreservesCapacityOnFailure() {
    FramePacker packer = new FramePacker(dialect, null);
    Frame frame = frame(Version.V2, false);
    assertThrows(IllegalArgumentException.class, () -> packer.pack(null, frame));
    assertThrows(IllegalArgumentException.class, () -> packer.pack(ByteBuffer.allocate(20), null));
    frame.setVersion(null);
    assertThrows(IllegalArgumentException.class, () -> packer.pack(ByteBuffer.allocate(20), frame));
    frame.setVersion(Version.V2);
    frame.setPayloadLength(256);
    assertThrows(IllegalArgumentException.class, () -> packer.pack(ByteBuffer.allocate(20), frame));
    frame.setPayloadLength(3);
    assertThrows(IllegalArgumentException.class, () -> packer.pack(ByteBuffer.allocate(20), frame));
    frame.setPayloadLength(2);
    ByteBuffer tooSmall = ByteBuffer.allocate(14);
    assertThrows(IllegalArgumentException.class, () -> packer.pack(tooSmall, frame));
    assertEquals(0, tooSmall.position());

    frame.setSigned(true);
    SigningKeyProvider missingKey = new SigningKeyProvider() {
      @Override
      public boolean canValidate() {
        return true;
      }

      @Override
      public byte[] getSigningKey(int systemId, int componentId, int linkId) {
        return null;
      }
    };
    assertThrows(IllegalArgumentException.class, () -> new FramePacker(dialect, missingKey).pack(ByteBuffer.allocate(28), frame));
  }
}
