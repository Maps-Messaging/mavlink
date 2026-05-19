/*
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.mapsmessaging.mavlink.context;

import io.mapsmessaging.mavlink.message.Frame;

import java.util.Map;

public record SequenceProcessor(SequenceProcessorConfig config) {

  public SequenceProcessingResult process(SystemContext systemContext, Frame frame, String streamId, long receivedAtNanos) {
    SequenceProcessingResult result = new SequenceProcessingResult();
    int sequence = frame.getSequence() & 0xFF;
    result.setSequence(sequence);

    int fingerprint = FrameFingerprint.computeFingerprint(frame);

    SequenceRingEntry previousEntryForSequence = systemContext.getSequenceRingBuffer().get(sequence);
    if (previousEntryForSequence != null) {
      long ageNanos = receivedAtNanos - previousEntryForSequence.getLastSeenAtNanos();
      boolean withinDupWindow = ageNanos >= 0 && ageNanos <= config.getDuplicateTimeWindowNanos();

      if (withinDupWindow) {
        if (previousEntryForSequence.getFingerprint() == fingerprint) {
          incrementDuplicate(systemContext, frame, streamId, receivedAtNanos, result);
          updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
          detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
          return result;
        }

        Detection detection = new Detection();
        detection.setSystemId(systemContext.getSystemId());
        detection.setStreamId(streamId);
        detection.setOccurredAtNanos(receivedAtNanos);
        detection.setType(DetectionType.SEQ_SAME_SEQ_DIFFERENT_FINGERPRINT);
        detection.setSeverity(DetectionSeverity.ALERT);
        detection.setDetails("seq=" + sequence + " previousStream=" + previousEntryForSequence.getStreamId());

        result.getDetections().add(detection);

        updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
        detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
        return result;
      }
    }

    if (!systemContext.isInitialized()) {
      systemContext.setInitialized(true);
      systemContext.setLastAcceptedSequence(sequence);
      systemContext.setLastAcceptedAtNanos(receivedAtNanos);
      setLastAcceptedSequenceForSource(systemContext, streamId, sequence);
      updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
      detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
      result.setAcceptedAsHead(true);
      return result;
    }

    int lastAcceptedSequence = systemContext.getLastAcceptedSequence() & 0xFF;
    int delta = (sequence - lastAcceptedSequence) & 0xFF;

    if (delta == 0) {
      incrementDuplicate(systemContext, frame, streamId, receivedAtNanos, result);
      updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
      detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
      return result;
    }

    if (delta <= 127) {
      if (delta > 1) {
        int lostPackets = delta - 1;
        incrementGap(systemContext, streamId, receivedAtNanos, result, lostPackets);
      }

      systemContext.setLastAcceptedSequence(sequence);
      systemContext.setLastAcceptedAtNanos(receivedAtNanos);
      setLastAcceptedSequenceForSource(systemContext, streamId, sequence);
      updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
      detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
      result.setAcceptedAsHead(true);
      return result;
    }

    int backwardDistance = 256 - delta;

    boolean withinReorderDistance = backwardDistance <= config.getReorderDistanceWindow();
    long ageSinceHeadNanos = receivedAtNanos - systemContext.getLastAcceptedAtNanos();
    boolean withinReorderTime = ageSinceHeadNanos >= 0 && ageSinceHeadNanos <= config.getReorderTimeWindowNanos();

    if (withinReorderDistance && withinReorderTime) {
      incrementReorder(systemContext, streamId, receivedAtNanos, result, backwardDistance);
      updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
      detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);
      return result;
    }

    incrementSuspiciousBackward(systemContext, streamId, receivedAtNanos, result, backwardDistance);

    if (looksLikeReset(systemContext, sequence, receivedAtNanos)) {
      incrementResetSuspected(systemContext, streamId, receivedAtNanos, result, lastAcceptedSequence, sequence);
    }

    updateRing(systemContext, sequence, fingerprint, streamId, receivedAtNanos);
    detectMultiSourceActive(systemContext, streamId, receivedAtNanos, result);

    return result;
  }

  private void updateRing(SystemContext systemContext, int sequence, int fingerprint, String streamId, long receivedAtNanos) {
    SequenceRingEntry entry = new SequenceRingEntry();
    entry.setSequence(sequence);
    entry.setFingerprint(fingerprint);
    entry.setStreamId(streamId);
    entry.setLastSeenAtNanos(receivedAtNanos);
    systemContext.getSequenceRingBuffer().put(sequence, entry);
  }

  private void setLastAcceptedSequenceForSource(SystemContext systemContext, String streamId, int sequence) {
    SourceStats stats = systemContext.getSourceStats().get(streamId);
    if (stats == null) {
      return;
    }
    stats.setLastAcceptedSequenceFromSource(sequence);
  }

  private void incrementDuplicate(SystemContext systemContext, Frame frame, String streamId, long receivedAtNanos, SequenceProcessingResult result) {
    systemContext.getSequenceStats().setDuplicates(systemContext.getSequenceStats().getDuplicates() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SEQ_DUPLICATE);
    detection.setSeverity(DetectionSeverity.INFO);
    detection.setDetails("seq=" + (frame.getSequence() & 0xFF));

    result.getDetections().add(detection);
  }

  private void incrementGap(SystemContext systemContext, String streamId, long receivedAtNanos, SequenceProcessingResult result, int lostPackets) {
    systemContext.getSequenceStats().setGaps(systemContext.getSequenceStats().getGaps() + 1);
    systemContext.getSequenceStats().setLostPackets(systemContext.getSequenceStats().getLostPackets() + lostPackets);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SEQ_GAP);
    detection.setSeverity(DetectionSeverity.WARN);
    detection.setDetails("lost=" + lostPackets);

    result.getDetections().add(detection);
  }

  private void incrementReorder(SystemContext systemContext, String streamId, long receivedAtNanos, SequenceProcessingResult result, int backwardDistance) {
    systemContext.getSequenceStats().setReorders(systemContext.getSequenceStats().getReorders() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SEQ_REORDER);
    detection.setSeverity(DetectionSeverity.INFO);
    detection.setDetails("back=" + backwardDistance);

    result.getDetections().add(detection);
  }

  private void incrementSuspiciousBackward(SystemContext systemContext, String streamId, long receivedAtNanos, SequenceProcessingResult result, int backwardDistance) {
    systemContext.getSequenceStats().setSuspiciousBackwards(systemContext.getSequenceStats().getSuspiciousBackwards() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SEQ_SUSPICIOUS_BACKWARDS);

    DetectionSeverity severity = backwardDistance >= config.getSuspiciousBackwardDistance()
        ? DetectionSeverity.ALERT
        : DetectionSeverity.WARN;

    detection.setSeverity(severity);
    detection.setDetails("back=" + backwardDistance);

    result.getDetections().add(detection);
  }

  private void incrementResetSuspected(SystemContext systemContext, String streamId, long receivedAtNanos, SequenceProcessingResult result, int lastAcceptedSequence, int sequence) {
    systemContext.getSequenceStats().setResetsSuspected(systemContext.getSequenceStats().getResetsSuspected() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SEQ_RESET_SUSPECTED);
    detection.setSeverity(DetectionSeverity.WARN);
    detection.setDetails("head=" + lastAcceptedSequence + " seq=" + sequence);

    result.getDetections().add(detection);
  }

  private boolean looksLikeReset(SystemContext systemContext, int sequence, long receivedAtNanos) {
    int lastAcceptedSequence = systemContext.getLastAcceptedSequence() & 0xFF;

    boolean headWasMidRange = lastAcceptedSequence >= 50 && lastAcceptedSequence <= 200;
    boolean newIsLow = sequence <= 10;

    if (!headWasMidRange || !newIsLow) {
      return false;
    }

    long silenceNanos = receivedAtNanos - systemContext.getLastAcceptedAtNanos();
    return silenceNanos > config.getMultiSourceActiveWindowNanos();
  }

  private void detectMultiSourceActive(SystemContext systemContext, String streamId, long receivedAtNanos, SequenceProcessingResult result) {
    String primaryStreamId = findPrimaryStreamId(systemContext, receivedAtNanos);
    if (primaryStreamId == null) {
      markPrimary(systemContext, streamId, receivedAtNanos);
      return;
    }

    if (primaryStreamId.equals(streamId)) {
      return;
    }

    systemContext.getSequenceStats().setMultiSourceActive(systemContext.getSequenceStats().getMultiSourceActive() + 1);

    Detection detection = new Detection();
    detection.setSystemId(systemContext.getSystemId());
    detection.setStreamId(streamId);
    detection.setOccurredAtNanos(receivedAtNanos);
    detection.setType(DetectionType.SYSTEM_MULTI_SOURCE_ACTIVE);
    detection.setSeverity(DetectionSeverity.WARN);
    detection.setDetails("primary=" + primaryStreamId);

    result.getDetections().add(detection);
  }

  private String findPrimaryStreamId(SystemContext systemContext, long receivedAtNanos) {
    long activeWindowNanos = config.getMultiSourceActiveWindowNanos();

    for (Map.Entry<String, SourceStats> entry : systemContext.getSourceStats().entrySet()) {
      SourceStats stats = entry.getValue();
      if (!stats.isPrimary()) {
        continue;
      }

      long ageNanos = receivedAtNanos - stats.getLastSeenAtNanos();
      if (ageNanos >= 0 && ageNanos <= activeWindowNanos) {
        return stats.getStreamId();
      }
    }

    return null;
  }

  private void markPrimary(SystemContext systemContext, String streamId, long receivedAtNanos) {
    SourceStats stats = systemContext.getSourceStats().get(streamId);
    if (stats == null) {
      return;
    }

    stats.setPrimary(true);
    stats.setPrimarySinceAtNanos(receivedAtNanos);
  }
}
