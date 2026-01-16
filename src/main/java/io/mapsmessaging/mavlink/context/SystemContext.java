package io.mapsmessaging.mavlink.context;

import io.mapsmessaging.mavlink.message.Frame;
import lombok.Data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Data
public class SystemContext {

  private int systemId;
  private boolean initialized;
  private int lastAcceptedSequence;
  private long lastAcceptedAtNanos;
  private long lastActivityAtNanos;
  private SequenceRingBuffer256 sequenceRingBuffer;
  private Map<String, SourceStats> sourceStats;
  private SequenceStats sequenceStats;

  public List<Detection> onValidatedFrame(Frame frame, String streamId, long receivedAtNanos, SequenceProcessor sequenceProcessor) {
    lastActivityAtNanos = receivedAtNanos;

    List<Detection> detections = new ArrayList<>();

    SourceStats statsForSource = sourceStats.computeIfAbsent(streamId, key -> new SourceStats(streamId));
    statsForSource.setLastSeenAtNanos(receivedAtNanos);
    statsForSource.setPacketCount(statsForSource.getPacketCount() + 1);

    SequenceProcessingResult result = sequenceProcessor.process(this, frame, streamId, receivedAtNanos);
    detections.addAll(result.getDetections());

    return detections;
  }

  public List<Detection> onInvalidFrame(String streamId, long receivedAtNanos, FrameFailureReason reason) {
    lastActivityAtNanos = receivedAtNanos;

    List<Detection> detections = new ArrayList<>();

    SourceStats statsForSource = sourceStats.computeIfAbsent(streamId, key -> new SourceStats(streamId));
    statsForSource.setLastSeenAtNanos(receivedAtNanos);
    statsForSource.setInvalidPacketCount(statsForSource.getInvalidPacketCount() + 1);

    sequenceStats.setInvalidFrames(sequenceStats.getInvalidFrames() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemId);
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.FRAME_INVALID);
    detection.setSeverity(DetectionSeverity.WARN);
    detection.setDetails(reason.name());

    detections.add(detection);

    return detections;
  }

  public int sweep(long nowNanos, SweepConfig sweepConfig, SequenceProcessorConfig sequenceProcessorConfig) {
    int removedSources = 0;

    long sourceTtlNanos = sweepConfig.getSourceTtlNanos();
    long activeWindowNanos = sequenceProcessorConfig.getMultiSourceActiveWindowNanos();

    String currentPrimary = null;

    Iterator<Map.Entry<String, SourceStats>> iterator = sourceStats.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, SourceStats> entry = iterator.next();
      SourceStats stats = entry.getValue();

      long ageNanos = nowNanos - stats.getLastSeenAtNanos();
      if (ageNanos > sourceTtlNanos) {
        iterator.remove();
        removedSources++;
        continue;
      }

      if (stats.isPrimary()) {
        long primaryAgeNanos = nowNanos - stats.getLastSeenAtNanos();
        if (primaryAgeNanos <= activeWindowNanos) {
          currentPrimary = stats.getStreamId();
        }
      }
    }

    if (currentPrimary == null) {
      for (SourceStats stats : sourceStats.values()) {
        if (stats.isPrimary()) {
          stats.setPrimary(false);
          stats.setPrimarySinceAtNanos(0L);
        }
      }
    }

    return removedSources;
  }

  public boolean isExpired(long nowNanos, SweepConfig sweepConfig) {
    long ageNanos = nowNanos - lastActivityAtNanos;
    return ageNanos > sweepConfig.getSystemTtlNanos();
  }

  public SystemContextSnapshot snapshot() {
    SystemContextSnapshot snapshot = new SystemContextSnapshot();
    snapshot.setSystemId(systemId);
    snapshot.setInitialized(initialized);
    snapshot.setLastAcceptedSequence(lastAcceptedSequence);
    snapshot.setLastAcceptedAtNanos(lastAcceptedAtNanos);
    snapshot.setSequenceStats(sequenceStats);
    snapshot.setSourceCount(sourceStats.size());
    return snapshot;
  }
}
