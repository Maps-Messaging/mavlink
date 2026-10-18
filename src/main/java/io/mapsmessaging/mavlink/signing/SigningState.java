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
 */
package io.mapsmessaging.mavlink.signing;

import io.mapsmessaging.mavlink.context.FrameFailureReason;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe timestamps for independent outgoing and incoming MAVLink signing streams.
 * A shared instance survives codec reconstruction; a store additionally survives process restarts.
 */
public final class SigningState implements AutoCloseable {

  public static final long MAX_TIMESTAMP = 0xFFFFFFFFFFFFL;
  private static final long MAVLINK_EPOCH_MILLIS = 1_420_070_400_000L;
  private static final long MAX_AGE_TICKS = 6_000_000L;
  private static final long CHECKPOINT_INTERVAL_MILLIS = 60_000L;

  private final ConcurrentMap<SigningStreamId, Long> outbound = new ConcurrentHashMap<>();
  private final ConcurrentMap<SigningStreamId, Long> inbound = new ConcurrentHashMap<>();
  private final AtomicLong clockTicks = new AtomicLong();
  private final AtomicLong lastCheckpointMillis = new AtomicLong(System.currentTimeMillis());
  private final SigningStateStore store;

  public SigningState() {
    store = null;
  }

  public SigningState(SigningStateStore store) throws IOException {
    if (store == null) {
      throw new IllegalArgumentException("store must not be null");
    }
    this.store = store;
    SigningStateSnapshot snapshot = store.load();
    outbound.putAll(snapshot.outbound());
    inbound.putAll(snapshot.inbound());
    clockTicks.set(snapshot.clockTicks());
  }

  public long nextOutgoing(int systemId, int componentId, int linkId) {
    SigningStreamId id = new SigningStreamId(systemId, componentId, linkId);
    long now = nowTicks();
    long result = outbound.compute(id, (ignored, previous) -> {
      long floor = Math.max(now, clockTicks.get());
      long candidate = previous == null ? floor : Math.max(floor, previous + 1);
      if (candidate > MAX_TIMESTAMP) {
        throw new IllegalStateException("MAVLink signing timestamp exhausted");
      }
      return candidate;
    });
    clockTicks.accumulateAndGet(result, Math::max);
    checkpointIfDue();
    return result;
  }

  /**
   * Called only after CRC and cryptographic signature checks succeeded.
   * Returns a distinct reason for stale or replayed packets.
   */
  public FrameFailureReason acceptIncoming(int systemId, int componentId, int linkId, long timestamp) {
    if (timestamp < 0 || timestamp > MAX_TIMESTAMP) {
      return FrameFailureReason.SIGNATURE_FAILED;
    }
    SigningStreamId id = new SigningStreamId(systemId, componentId, linkId);
    long floor = Math.max(nowTicks(), clockTicks.get()) - MAX_AGE_TICKS;
    if (timestamp < floor) {
      return FrameFailureReason.SIGNATURE_STALE;
    }

    AtomicReference<FrameFailureReason> outcome = new AtomicReference<>(FrameFailureReason.OK);
    inbound.compute(id, (ignored, previous) -> {
      if (previous != null && timestamp <= previous) {
        outcome.set(FrameFailureReason.SIGNATURE_REPLAY);
        return previous;
      }
      return timestamp;
    });

    if (outcome.get() == FrameFailureReason.OK) {
      clockTicks.accumulateAndGet(timestamp, Math::max);
      checkpointIfDue();
    }
    return outcome.get();
  }

  public SigningStateSnapshot snapshot() {
    return new SigningStateSnapshot(Map.copyOf(outbound), Map.copyOf(inbound), clockTicks.get());
  }

  public synchronized void checkpoint() throws IOException {
    if (store != null) {
      store.save(snapshot());
      lastCheckpointMillis.set(System.currentTimeMillis());
    }
  }

  @Override
  public void close() throws IOException {
    checkpoint();
  }

  private void checkpointIfDue() {
    if (store == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long previous = lastCheckpointMillis.get();
    if (now - previous >= CHECKPOINT_INTERVAL_MILLIS &&
        lastCheckpointMillis.compareAndSet(previous, now)) {
      try {
        checkpoint();
      } catch (IOException e) {
        lastCheckpointMillis.compareAndSet(now, previous);
        throw new UncheckedIOException("Unable to persist MAVLink signing state", e);
      }
    }
  }

  private static long nowTicks() {
    return Math.max(0L, (System.currentTimeMillis() - MAVLINK_EPOCH_MILLIS) * 100L);
  }
}
