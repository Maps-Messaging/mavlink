package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SequenceRingEntry {
  private int sequence;
  private int fingerprint;
  private String streamId;
  private long lastSeenAtNanos;
}
