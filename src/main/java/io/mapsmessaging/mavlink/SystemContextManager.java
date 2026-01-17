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
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SystemContextManager {

  private final Map<Integer, SystemContext> systemContexts;
  private final SequenceProcessor sequenceProcessor;
  private final SequenceProcessorConfig sequenceProcessorConfig;
  private final SweepConfig sweepConfig;

  public SystemContextManager() {
    this(new SequenceProcessorConfig(), new SweepConfig());
  }

  public SystemContextManager(SequenceProcessorConfig sequenceProcessorConfig, SweepConfig sweepConfig) {
    this.systemContexts = new ConcurrentHashMap<>();
    this.sequenceProcessorConfig = sequenceProcessorConfig;
    this.sequenceProcessor = new SequenceProcessor(sequenceProcessorConfig);
    this.sweepConfig = sweepConfig;
  }

  public List<Detection> onValidatedFrame(Frame frame, String streamId, long receivedAtNanos) {
    int systemId = frame.getSystemId();
    SystemContext systemContext = systemContexts.computeIfAbsent(systemId, this::createContext);
    return systemContext.onValidatedFrame(frame, streamId, receivedAtNanos, sequenceProcessor);
  }

  public List<Detection> onInvalidFrame(int systemId, String streamId, long receivedAtNanos, FrameFailureReason reason) {
    SystemContext systemContext = systemContexts.get(systemId);
    if (systemContext == null) {
      return List.of();
    }
    return systemContext.onInvalidFrame(streamId, receivedAtNanos, reason);
  }

  public SweepResult sweep(long nowNanos) {
    SweepResult result = new SweepResult();

    int removedSources = 0;
    int removedSystems = 0;

    for (Map.Entry<Integer, SystemContext> entry : systemContexts.entrySet()) {
      SystemContext systemContext = entry.getValue();

      removedSources += systemContext.sweep(nowNanos, sweepConfig, sequenceProcessorConfig);

      if (systemContext.isExpired(nowNanos, sweepConfig)) {
        systemContexts.remove(entry.getKey());
        removedSystems++;
      }
    }
    result.setRemovedSystems(removedSystems);
    result.setRemovedSources(removedSources);
    return result;
  }

  private SystemContext createContext(int systemId) {
    SystemContext systemContext = new SystemContext();
    systemContext.setSystemId(systemId);
    systemContext.setSequenceRingBuffer(new SequenceRingBuffer256());
    systemContext.setSourceStats(new ConcurrentHashMap<>());
    systemContext.setSequenceStats(new SequenceStats());
    systemContext.setLastActivityAtNanos(0L);
    return systemContext;
  }

  public List<SystemContextSnapshot> snapshotAll() {
    List<SystemContextSnapshot> snapshots = new ArrayList<>();
    for (SystemContext systemContext : systemContexts.values()) {
      snapshots.add(systemContext.snapshot());
    }
    return snapshots;
  }
}
