package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.MavlinkFrame;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface MavlinkFrameDecoder {
  Optional<MavlinkFrame> decode(ByteBuffer candidateFrame);
}
