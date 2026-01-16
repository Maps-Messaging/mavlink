package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SequenceStats {
  private long duplicates;
  private long reorders;
  private long gaps;
  private long lostPackets;
  private long suspiciousBackwards;
  private long resetsSuspected;
  private long invalidFrames;
  private long multiSourceActive;
}
