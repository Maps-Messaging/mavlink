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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

class HeartbeatTest {

  @Test
  void validateV1Heartbeat() throws Exception {
    int[] load = new int[]{0xfe,0x09,0x81,0xff,0xbe,0x00,0x00,0x00,0x00,0x00,0x06,0x08,0xc0,0x04,0x03,0xa4,0xe2};
    byte[] buffer = new byte[load.length];
    for(int x=0;x<load.length;x++){
      buffer[x] = (byte)load[x];
    }
    ByteBuffer input = ByteBuffer.wrap(buffer);
    MavlinkCodec payloadCodec = MavlinkTestSupport.codec();
    MavlinkFrameCodec frameCodec = new MavlinkFrameCodec(payloadCodec);
    Assertions.assertNotNull(frameCodec.tryUnpackFrame(input).get());
  }
}
