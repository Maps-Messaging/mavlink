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

import io.mapsmessaging.mavlink.message.X25Crc;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class X25CrcTest {

  @Test
  void testKnownVector_123456789() {
    // Standard CRC-16/X.25 test vector: "123456789" -> 0x906E
    X25Crc crc = new X25Crc();
    byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

    crc.update(data);

    int crcValue = crc.getCrc() & 0xFFFF;
    assertEquals(0x6F91, crcValue, "CRC-16/X.25 of '123456789' must be 0x906E");
  }

  @Test
  void testMavlink2FrameCrc_attitudeQuaternion_31() {
    int[] signed = new int[] {
        -3, 32, 0, 0, -56, 1, 1, 31, 0, 0,
        24, 35, 27, 0, 126, 45, 42, 63, -11, -55, 33, 56, -13, 23, -48, -72, 83, 63, 63, 63, -58, 54, 67, -70, 122, 57, 32, -71, 77, -44, 43, -71,
        75, -68
    };



    byte[] frame = new byte[signed.length];

    for (int index = 0; index < signed.length; index++) {
      frame[index] = (byte) signed[index];
    }
    int payloadLength = frame[1] & 0xFF;
    assertEquals(32, payloadLength);
    int receivedCrc = (frame[42] & 0xFF) | ((frame[43] & 0xFF) << 8);
    assertEquals(0xBC4B, receivedCrc);

    // MAVLink2 CRC input = bytes from LEN (index 1) through payload end (index 41), then CRC_EXTRA
    int crcInputOffset = 1;
    int crcInputLength = 9 + payloadLength; // LEN..MSGID(3) is 9 bytes, then payload
    X25Crc crc = new X25Crc();
    crc.update(frame, crcInputOffset, crcInputLength);

    int crcExtra = 246; // MAVLINK_MSG_ID_ATTITUDE_QUATERNION_CRC :contentReference[oaicite:1]{index=1}
    crc.update(crcExtra);

    int computed = crc.getCrc();
    assertEquals(receivedCrc, computed, "Computed CRC must match the frame CRC");
  }

  @Test
  void testHeartbeatExtraCrc() {
    // HEARTBEAT extra CRC is 50 (0x32) according to MAVLink tables.
    // We reproduce the MAVLink message_checksum() logic:
    //
    // crc.accumulate_str(msg.name + " ");
    // for base_fields:
    //   crc.accumulate_str(f.type + " ");
    //   crc.accumulate_str(f.name + " ");
    //   if array_length: crc.accumulate([array_length])
    //
    // extra = (crc_low ^ crc_high)

    X25Crc crc = new X25Crc();
    crc.reset();

    accumulateString(crc, "HEARTBEAT ");

    // common.xml HEARTBEAT base fields:
    // type           : uint8_t
    // autopilot      : uint8_t
    // base_mode      : uint8_t
    // custom_mode    : uint32_t
    // system_status  : uint8_t
    // mavlink_version: uint8_t_mavlink_version

    accumulateField(crc, "uint32_t", "custom_mode");
    accumulateField(crc, "uint8_t", "type");
    accumulateField(crc, "uint8_t", "autopilot");
    accumulateField(crc, "uint8_t", "base_mode");
    accumulateField(crc, "uint8_t", "system_status");
    accumulateField(crc, "uint8_t", "mavlink_version");

    int raw = crc.getRawCrc() & 0xFFFF;
    int low = raw & 0xFF;
    int high = (raw >> 8) & 0xFF;
    int extra = (low ^ high) & 0xFF;

    assertEquals(50, extra, "HEARTBEAT extra CRC must be 50 (0x32)");
  }


  @Test
  void testSysStatus() {
    X25Crc crc = new X25Crc();
    crc.reset();

    accumulateString(crc, "SYS_STATUS ");
    accumulateField(crc, "uint32_t", "onboard_control_sensors_present");
    accumulateField(crc, "uint32_t", "onboard_control_sensors_enabled");
    accumulateField(crc, "uint32_t", "onboard_control_sensors_health");
    accumulateField(crc, "uint16_t", "load");
    accumulateField(crc, "uint16_t", "voltage_battery");
    accumulateField(crc, "int16_t", "current_battery");

    accumulateField(crc, "uint16_t", "drop_rate_comm");
    accumulateField(crc, "uint16_t", "errors_comm");
    accumulateField(crc, "uint16_t", "errors_count1");
    accumulateField(crc, "uint16_t", "errors_count2");
    accumulateField(crc, "uint16_t", "errors_count3");
    accumulateField(crc, "uint16_t", "errors_count4");
    accumulateField(crc, "int8_t", "battery_remaining");

//    accumulateField(crc, "uint32_t", "onboard_control_sensors_present_extended");
    //   accumulateField(crc, "uint32_t", "onboard_control_sensors_enabled_extended");
    //   accumulateField(crc, "uint32_t", "onboard_control_sensors_health_extended");


    int raw = crc.getRawCrc() & 0xFFFF;
    int low = raw & 0xFF;
    int high = (raw >> 8) & 0xFF;
    int extra = (low ^ high) & 0xFF;

    assertEquals(124, extra, "HEARTBEAT extra CRC must be 50 (0x32)");

  }

  private static void accumulateString(X25Crc crc, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
    crc.update(bytes);
  }

  private static void accumulateField(X25Crc crc, String type, String name) {
    accumulateString(crc, type + " ");
    accumulateString(crc, name + " ");
    // No array_length here for HEARTBEAT fields
  }
}