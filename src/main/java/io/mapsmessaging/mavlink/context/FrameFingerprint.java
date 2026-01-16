package io.mapsmessaging.mavlink.context;

import io.mapsmessaging.mavlink.message.Frame;

public class FrameFingerprint {

  private FrameFingerprint() {
  }

  public static int computeFingerprint(Frame frame) {
    int hash = 0x811C9DC5;

    hash = fnv1a(hash, frame.getVersion() == null ? 0 : frame.getVersion().ordinal());
    hash = fnv1a(hash, frame.getSystemId());
    hash = fnv1a(hash, frame.getComponentId());
    hash = fnv1a(hash, frame.getMessageId());
    hash = fnv1a(hash, frame.getPayloadLength());

    byte[] payload = frame.getPayload();
    if (payload != null) {
      int length = Math.min(payload.length, frame.getPayloadLength());
      for (int index = 0; index < length; index++) {
        hash = fnv1a(hash, payload[index]);
      }
    }

    return hash;
  }

  private static int fnv1a(int hash, int value) {
    int next = hash ^ value;
    return next * 16777619;
  }
}
