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

package io.mapsmessaging.mavlink.message.fields;

import lombok.Getter;

public enum MavlinkWireType {

  INT8(1, "int8_t"),
  UINT8(1, "uint8_t"),
  INT16(2, "int16_t"),
  UINT16(2, "uint16_t"),
  INT32(4, "int32_t"),
  UINT32(4, "uint32_t"),
  INT64(8, "int64_t"),
  UINT64(8, "uint64_t"),
  FLOAT(4, "float"),
  DOUBLE(8, "double"),
  CHAR(1, "char"),
  ;

  @Getter
  private final int sizeInBytes;
  @Getter
  private final String wireName;

  MavlinkWireType(int sizeInBytes, String wireName) {
    this.sizeInBytes = sizeInBytes;
    this.wireName = wireName;
  }

  public static MavlinkWireType fromXmlType(String xmlType) {
    if (xmlType == null) {
      throw new IllegalArgumentException("MAVLink XML type is null");
    }

    String type = xmlType.trim();

    // Horrible special-case: uint8_t_mavlink_version behaves as uint8_t
    if ("uint8_t_mavlink_version".equals(type)) {
      type = "uint8_t";
    }
    if (type.contains("[") && type.contains("]")) {
      type = type.substring(0, type.indexOf("["));
    }

    return switch (type) {
      case "int8_t" -> INT8;
      case "uint8_t" -> UINT8;
      case "int16_t" -> INT16;
      case "uint16_t" -> UINT16;
      case "int32_t" -> INT32;
      case "uint32_t" -> UINT32;
      case "int64_t" -> INT64;
      case "uint64_t" -> UINT64;
      case "float" -> FLOAT;
      case "double" -> DOUBLE;
      case "char" -> CHAR;
      default -> throw new IllegalArgumentException("Unsupported MAVLink XML type: " + xmlType);
    };
  }

}