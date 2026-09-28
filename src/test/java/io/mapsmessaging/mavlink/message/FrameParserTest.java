package io.mapsmessaging.mavlink.message;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FrameParserTest {

  private final FrameParser parser = new FrameParser();

  @Test
  void parsesV1AndUnsignedV2HeadersAndPayloads() {
    Frame v1 = parser.parse(new byte[]{(byte) 0xfe, 2, 3, 4, 5, 6, 7, 8, 0x34, 0x12});
    assertEquals(Version.V1, v1.getVersion());
    assertEquals(3, v1.getSequence());
    assertEquals(4, v1.getSystemId());
    assertEquals(5, v1.getComponentId());
    assertEquals(6, v1.getMessageId());
    assertArrayEquals(new byte[]{7, 8}, v1.getPayload());
    assertEquals(0x1234, v1.getChecksum());

    Frame v2 = parser.parse(new byte[]{(byte) 0xfd, 2, 0, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0x34, 0x12});
    assertEquals(Version.V2, v2.getVersion());
    assertEquals(5, v2.getSequence());
    assertEquals(6, v2.getSystemId());
    assertEquals(7, v2.getComponentId());
    assertEquals(0x0a0908, v2.getMessageId());
    assertArrayEquals(new byte[]{11, 12}, v2.getPayload());
    assertEquals(4, v2.getCompatibilityFlags());
    assertFalse(v2.isSigned());
    assertNull(v2.getSignature());
  }

  @Test
  void parsesSignedV2FrameAndRejectsTruncation() {
    byte[] frameBytes = new byte[25];
    frameBytes[0] = (byte) 0xfd;
    frameBytes[2] = 1;
    frameBytes[11] = 50;
    Arrays.fill(frameBytes, 12, 25, (byte) 9);

    Frame signed = parser.parse(frameBytes);
    assertTrue(signed.isSigned());
    assertEquals(13, signed.getSignature().length);
    assertEquals(50, signed.getChecksum());

    assertThrows(IllegalArgumentException.class, () -> parser.parse(Arrays.copyOf(frameBytes, 24)));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(Arrays.copyOf(frameBytes, 11)));
  }

  @Test
  void rejectsInvalidAndIncompleteFrames() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{0}));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{(byte) 0xfe}));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{(byte) 0xfe, 2, 3, 4, 5, 6, 7, 8}));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{(byte) 0xfd}));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[]{(byte) 0xfd, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2}));
  }
}
