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

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.context.*;
import io.mapsmessaging.mavlink.message.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemContextManagerTest {

  @Test
  void validatedFrameCreatesContextAndNoDetectionsOnFirstFrame() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    Frame frame = frame(1, 1, 10, 33, payloadOf(1, 2, 3));
    List<Detection> detections = manager.onValidatedFrame(frame, "udp:10.0.0.1:14550", 1_000L);

    assertNotNull(detections);
    assertTrue(detections.isEmpty());

    assertEquals(1, manager.getSystemContexts().size());
    assertNotNull(manager.getSystemContexts().get(1));
  }

  @Test
  void invalidFrameDoesNotCreateContextWhenMissing() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    List<Detection> detections = manager.onInvalidFrame(42, "udp:10.0.0.1:14550", 1_000L, FrameFailureReason.CRC_FAILED);

    assertNotNull(detections);
    assertTrue(detections.isEmpty());
    assertTrue(manager.getSystemContexts().isEmpty());
  }

  @Test
  void duplicateIsDetectedWithinDuplicateWindow() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    sequenceProcessorConfig.setDuplicateTimeWindowNanos(1_000L);

    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    String streamId = "udp:10.0.0.1:14550";

    Frame first = frame(1, 1, 10, 33, payloadOf(1, 2, 3));
    assertTrue(manager.onValidatedFrame(first, streamId, 10_000L).isEmpty());

    Frame duplicate = frame(1, 1, 10, 33, payloadOf(1, 2, 3));
    List<Detection> detections = manager.onValidatedFrame(duplicate, streamId, 10_500L);

    assertEquals(1, detections.size());
    assertEquals(DetectionType.SEQ_DUPLICATE, detections.get(0).getType());
  }

  @Test
  void sameSequenceDifferentFingerprintIsAlert() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    sequenceProcessorConfig.setDuplicateTimeWindowNanos(10_000L);

    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    String streamId = "udp:10.0.0.1:14550";

    Frame first = frame(1, 1, 10, 33, payloadOf(1, 2, 3));
    assertTrue(manager.onValidatedFrame(first, streamId, 10_000L).isEmpty());

    Frame conflicting = frame(1, 1, 10, 33, payloadOf(9, 9, 9));
    List<Detection> detections = manager.onValidatedFrame(conflicting, streamId, 12_000L);

    assertEquals(1, detections.size());
    assertEquals(DetectionType.SEQ_SAME_SEQ_DIFFERENT_FINGERPRINT, detections.get(0).getType());
    assertEquals(DetectionSeverity.ALERT, detections.get(0).getSeverity());
  }

  @Test
  void gapIsDetectedWhenSequenceJumpsForward() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    String streamId = "udp:10.0.0.1:14550";

    Frame first = frame(1, 1, 10, 33, payloadOf(1));
    assertTrue(manager.onValidatedFrame(first, streamId, 10_000L).isEmpty());

    Frame jump = frame(1, 1, 13, 33, payloadOf(2));
    List<Detection> detections = manager.onValidatedFrame(jump, streamId, 11_000L);

    assertEquals(1, detections.size());
    assertEquals(DetectionType.SEQ_GAP, detections.get(0).getType());
    assertTrue(detections.get(0).getDetails().contains("lost=2"));
  }

  @Test
  void reorderIsDetectedWhenBackwardsWithinWindow() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();
    sequenceProcessorConfig.setReorderDistanceWindow(5);
    sequenceProcessorConfig.setReorderTimeWindowNanos(10_000L);

    SweepConfig sweepConfig = new SweepConfig();

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    String streamId = "udp:10.0.0.1:14550";

    Frame head = frame(1, 1, 20, 33, payloadOf(1));
    assertTrue(manager.onValidatedFrame(head, streamId, 10_000L).isEmpty());

    Frame forward = frame(1, 1, 21, 33, payloadOf(2));
    assertTrue(manager.onValidatedFrame(forward, streamId, 11_000L).isEmpty());

    Frame reorder = frame(1, 1, 19, 33, payloadOf(3));
    List<Detection> detections = manager.onValidatedFrame(reorder, streamId, 12_000L);

    assertEquals(1, detections.size());
    assertEquals(DetectionType.SEQ_REORDER, detections.get(0).getType());
    assertTrue(detections.get(0).getDetails().contains("back="));
  }

  @Test
  void sweepRemovesExpiredSystemContexts() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();

    SweepConfig sweepConfig = new SweepConfig();
    sweepConfig.setSystemTtlNanos(1_000L);
    sweepConfig.setSourceTtlNanos(10_000L);

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    Frame frame = frame(1, 1, 10, 33, payloadOf(1, 2, 3));
    manager.onValidatedFrame(frame, "udp:10.0.0.1:14550", 1_000L);

    SweepResult result = manager.sweep(5_000L);

    assertEquals(1, result.getRemovedSystems());
    assertTrue(manager.getSystemContexts().isEmpty());
  }

  @Test
  void sweepRemovesExpiredSourcesButKeepsSystemIfActive() {
    SequenceProcessorConfig sequenceProcessorConfig = new SequenceProcessorConfig();

    SweepConfig sweepConfig = new SweepConfig();
    sweepConfig.setSystemTtlNanos(100_000L);
    sweepConfig.setSourceTtlNanos(1_000L);

    SystemContextManager manager = new SystemContextManager(sequenceProcessorConfig, sweepConfig);

    Frame frame = frame(1, 1, 10, 33, payloadOf(1));

    manager.onValidatedFrame(frame, "udp:10.0.0.1:14550", 1_000L);
    manager.onValidatedFrame(frame, "udp:10.0.0.2:14550", 1_100L);

    // Keep the system active (touch lastActivity) but age out sources.
    manager.onValidatedFrame(frame, "udp:10.0.0.1:14550", 10_000L);

    SweepResult result = manager.sweep(20_000L);

    assertEquals(0, result.getRemovedSystems());
    assertTrue(result.getRemovedSources() >= 1);
    assertEquals(1, manager.getSystemContexts().size());
  }

  private Frame frame(int systemId, int componentId, int sequence, int messageId, byte[] payload) {
    Frame frame = new Frame();
    frame.setSystemId(systemId);
    frame.setComponentId(componentId);
    frame.setSequence(sequence);
    frame.setMessageId(messageId);
    frame.setPayload(payload);
    frame.setPayloadLength(payload == null ? 0 : payload.length);
    frame.setChecksum(0);
    frame.setSigned(false);
    frame.setValidated(FrameFailureReason.OK);
    return frame;
  }

  private byte[] payloadOf(int... bytes) {
    byte[] payload = new byte[bytes.length];
    for (int index = 0; index < bytes.length; index++) {
      payload[index] = (byte) bytes[index];
    }
    return payload;
  }
}