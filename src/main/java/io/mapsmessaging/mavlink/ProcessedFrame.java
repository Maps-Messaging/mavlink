package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.context.Detection;
import io.mapsmessaging.mavlink.message.Frame;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class ProcessedFrame {
  Frame frame;
  Map<String, Object> fields;
  boolean valid;
  List<Detection> detections;
}
