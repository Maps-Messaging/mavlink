package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class SweepConfig {

  private long systemTtlNanos;
  private long sourceTtlNanos;

  public SweepConfig() {
    this.systemTtlNanos = 10L * 60L * 1_000_000_000L;
    this.sourceTtlNanos = 60L * 1_000_000_000L;
  }
}
