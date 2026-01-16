package io.mapsmessaging.mavlink.context;

import java.util.concurrent.atomic.AtomicReferenceArray;

public class SequenceRingBuffer256 {

  private final AtomicReferenceArray<SequenceRingEntry> entries;

  public SequenceRingBuffer256() {
    this.entries = new AtomicReferenceArray<>(256);
  }

  public SequenceRingEntry get(int sequence) {
    int index = sequence & 0xFF;
    return entries.get(index);
  }

  public void put(int sequence, SequenceRingEntry entry) {
    int index = sequence & 0xFF;
    entries.set(index, entry);
  }
}
