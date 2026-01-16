package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SourceStats {

  private final String streamId;
  private long lastSeenAtNanos;
  private long packetCount;
  private long invalidPacketCount;
  private boolean primary;
  private long primarySinceAtNanos;
  private Integer lastAcceptedSequenceFromSource;

  public SourceStats(String streamId) {
    this.streamId = streamId;
  }
}
