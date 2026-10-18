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

import java.util.Map;
import java.util.Objects;

/** Immutable persisted view of signing timestamps, without signing keys. */
public record SigningStateSnapshot(Map<SigningStreamId, Long> outbound,
                                   Map<SigningStreamId, Long> inbound,
                                   long clockTicks) {

  public SigningStateSnapshot {
    outbound = Map.copyOf(Objects.requireNonNull(outbound, "outbound"));
    inbound = Map.copyOf(Objects.requireNonNull(inbound, "inbound"));
    if (clockTicks < 0 || clockTicks > SigningState.MAX_TIMESTAMP) {
      throw new IllegalArgumentException("clockTicks must fit the MAVLink 48-bit timestamp");
    }
    outbound.values().forEach(SigningStateSnapshot::requireTimestamp);
    inbound.values().forEach(SigningStateSnapshot::requireTimestamp);
  }

  private static void requireTimestamp(Long value) {
    if (value == null || value < 0 || value > SigningState.MAX_TIMESTAMP) {
      throw new IllegalArgumentException("Invalid stored MAVLink timestamp");
    }
  }

  public static SigningStateSnapshot empty() {
    return new SigningStateSnapshot(Map.of(), Map.of(), 0L);
  }
}
