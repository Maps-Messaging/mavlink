package io.mapsmessaging.mavlink.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SequenceProcessingResult {
  private boolean acceptedAsHead;
  private int sequence;
  private List<Detection> detections;

  public SequenceProcessingResult() {
    this.detections = new ArrayList<>();
  }
}
