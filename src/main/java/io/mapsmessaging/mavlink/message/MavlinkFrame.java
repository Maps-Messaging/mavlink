/*
 *
 *     Copyright [ 2020 - 2026 ] [Matthew Buckton]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package io.mapsmessaging.mavlink.message;

import lombok.Data;

@Data
public class MavlinkFrame {

  private MavlinkVersion version;
  private int sequence;
  private int systemId;
  private int componentId;
  private int messageId;
  private int payloadLength;
  private byte[] payload;
  private int checksum;
  private boolean signed;
  private byte incompatibilityFlags;
  private byte compatibilityFlags;
  private byte[] signature;
}