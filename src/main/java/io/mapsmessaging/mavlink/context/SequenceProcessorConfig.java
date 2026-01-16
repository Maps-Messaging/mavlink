package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SequenceProcessorConfig {

  private int reorderDistanceWindow;
  private long reorderTimeWindowNanos;
  private long duplicateTimeWindowNanos;
  private int suspiciousBackwardDistance;
  private long multiSourceActiveWindowNanos;

  public SequenceProcessorConfig() {
    this.reorderDistanceWindow = 20;
    this.reorderTimeWindowNanos = 500_000_000L;
    this.duplicateTimeWindowNanos = 1_000_000_000L;
    this.suspiciousBackwardDistance = 64;
    this.multiSourceActiveWindowNanos = 2_000_000_000L;
  }
}
