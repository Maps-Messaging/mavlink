package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SystemContextSnapshot {
  private int systemId;
  private boolean initialized;
  private int lastAcceptedSequence;
  private long lastAcceptedAtNanos;
  private SequenceStats sequenceStats;
  private int sourceCount;
}
