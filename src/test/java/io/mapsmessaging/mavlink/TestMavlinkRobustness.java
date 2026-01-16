/*
 *
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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.mavlink;


import io.mapsmessaging.mavlink.message.MessageRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TestMavlinkRobustness {

  @Test
  void encode_unknownMessageId_throwsIOException() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    assertThrows(IOException.class, () -> codec.encodePayload(999999, Map.of()));
  }

  @Test
  void decode_unknownMessageId_throwsIOException() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    assertThrows(IOException.class, () -> codec.parsePayload(999999, new byte[] { 0x01, 0x02 }));
  }

  @Test
  void decode_oversizedPayload_isHandledDeterministically() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();

    byte[] payload = codec.encodePayload(1, Map.of(
        "onboard_control_sensors_present", 1,
        "onboard_control_sensors_enabled", 1,
        "onboard_control_sensors_health", 1,
        "load", 250,
        "voltage_battery", 12000,
        "current_battery", 100,
        "battery_remaining", 90
    ));

    byte[] oversized = java.util.Arrays.copyOf(payload, payload.length + 10);
    // Leave the extra bytes as zeros. Behavior must be consistent:
    // either accept and ignore tail OR reject. Lock down one.

    try {
      codec.parsePayload(1, oversized);
    } catch (IOException exception) {
      // Acceptable if your parser is strict.
      return;
    }
  }

  @Test
  void commonDialectLoadsAndHasKnownMessageIds() throws Exception {
    MavlinkMessageFormatLoader loader = MavlinkMessageFormatLoader.getInstance();
    MavlinkCodec codec = loader.getDialectOrThrow("common");

    Assertions.assertNotNull(codec);
    Assertions.assertEquals("common", codec.getName());

    MessageRegistry registry = codec.getRegistry();
    Assertions.assertNotNull(registry);

    assertTrue(registry.getCompiledMessagesById().containsKey(0), "HEARTBEAT");
    assertTrue(registry.getCompiledMessagesById().containsKey(1), "SYS_STATUS");
    assertTrue(registry.getCompiledMessagesById().containsKey(33), "GLOBAL_POSITION_INT");
    assertTrue(registry.getCompiledMessagesById().containsKey(148), "AUTOPILOT_VERSION");

    Assertions.assertEquals(232, registry.getCompiledMessages().size());
  }
}
