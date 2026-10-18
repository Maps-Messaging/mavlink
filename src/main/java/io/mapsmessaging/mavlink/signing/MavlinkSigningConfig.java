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

import java.util.Objects;

/**
 * Per-link configuration. Sharing the state object across codec instances preserves replay and timestamp history.
 *
 * @param policy incoming signed/unsigned acceptance policy
 * @param linkId unsigned 8-bit ID for outbound signed packets
 * @param state thread-safe signing and replay state
 */
public record MavlinkSigningConfig(MavlinkSigningPolicy policy, int linkId, SigningState state) {

  public MavlinkSigningConfig {
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(state, "state");
    if (linkId < 0 || linkId > 255) {
      throw new IllegalArgumentException("linkId must be between 0 and 255");
    }
  }

  public static MavlinkSigningConfig unsignedDefaults() {
    return new MavlinkSigningConfig(MavlinkSigningPolicy.DISABLED, 0, new SigningState());
  }
}
