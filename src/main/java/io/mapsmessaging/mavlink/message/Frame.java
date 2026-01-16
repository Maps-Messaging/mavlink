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

package io.mapsmessaging.mavlink.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(
    title = "MAVLink Frame",
    description = "Decoded MAVLink frame with header, payload, checksum, and optional signature"
)
public class Frame {

  @Schema(
      description = "MAVLink protocol version",
      example = "V2",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private Version version;

  @Schema(
      description = "Packet sequence number",
      example = "42",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int sequence;

  @Schema(
      description = "Source system ID",
      example = "1",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int systemId;

  @Schema(
      description = "Source component ID",
      example = "1",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int componentId;

  @Schema(
      description = "MAVLink message ID",
      example = "33",
      minimum = "0",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int messageId;

  @Schema(
      description = "Payload length in bytes",
      example = "12",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int payloadLength;

  @Schema(
      description = "Raw payload bytes",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private byte[] payload;

  @Schema(
      description = "CRC checksum (X25) as unsigned 16-bit value",
      example = "54321",
      minimum = "0",
      maximum = "65535",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private int checksum;

  @Schema(
      description = "True if the frame includes a MAVLink v2 signature",
      example = "true",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private boolean signed;

  @Schema(
      description = "MAVLink v2 incompatibility flags",
      example = "1",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private byte incompatibilityFlags;

  @Schema(
      description = "MAVLink v2 compatibility flags",
      example = "0",
      minimum = "0",
      maximum = "255",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  private byte compatibilityFlags;

  @Schema(
      description = "MAVLink v2 signature block (13 bytes) if present",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      nullable = true
  )
  private byte[] signature;

  @Schema(
      description = "True if CRC and (if present) signature validation succeeded",
      example = "true",
      requiredMode = Schema.RequiredMode.REQUIRED,
      defaultValue = "false"
  )
  private boolean validated = false;
}