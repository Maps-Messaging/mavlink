package io.mapsmessaging.mavlink.context;

import lombok.Data;

@Data
public class Detection {
  private int systemId;
  private String streamId;
  private long occurredAtNanos;
  private DetectionType type;
  private DetectionSeverity severity;
  private String details;
}
