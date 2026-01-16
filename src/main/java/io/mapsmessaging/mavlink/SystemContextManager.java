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
