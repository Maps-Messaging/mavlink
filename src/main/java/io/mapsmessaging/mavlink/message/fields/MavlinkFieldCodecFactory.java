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


public final class MavlinkFieldCodecFactory {

  private MavlinkFieldCodecFactory() {
  }

  public static AbstractMavlinkFieldCodec createCodec(MavlinkFieldDefinition fieldDefinition) {
    String typeString = fieldDefinition.getType();
    String baseType = typeString;
    int arrayLength = 0;

    int arrayStart = typeString.indexOf('[');
    if (arrayStart > 0 && typeString.endsWith("]")) {
      baseType = typeString.substring(0, arrayStart);
      String lengthText = typeString.substring(arrayStart + 1, typeString.length() - 1);
      arrayLength = Integer.parseInt(lengthText);
    }

    AbstractMavlinkFieldCodec scalarCodec = createScalarCodec(baseType);

    if (arrayLength > 0) {
      boolean treatAsString = scalarCodec.getWireType() == MavlinkWireType.CHAR;
      return new ArrayFieldCodec(scalarCodec, arrayLength, treatAsString);
    }

    return scalarCodec;
  }

  private static AbstractMavlinkFieldCodec createScalarCodec(String baseType) {
    AbstractMavlinkFieldCodec codec = switch (baseType) {
      case "int8_t" -> new Int8FieldCodec();
      case "uint8_t" -> new UInt8FieldCodec();
      case "int16_t" -> new Int16FieldCodec();
      case "uint16_t" -> new UInt16FieldCodec();
      case "int32_t" -> new Int32FieldCodec();
      case "uint32_t" -> new UInt32FieldCodec();
      case "int64_t" -> new Int64FieldCodec();
      case "uint64_t" -> new UInt64FieldCodec();
      case "float" -> new FloatFieldCodec();
      case "double" -> new DoubleFieldCodec();
      case "char" -> new CharFieldCodec();
      default -> null;
    };

    if (codec != null) {
      return codec;
    }

    int location = baseType.indexOf("_t");
    if (location != -1 && baseType.length() > location + 2) {
      return createScalarCodec(baseType.substring(0, location + 2));
    }

    throw new IllegalArgumentException("Unsupported MAVLink base type: " + baseType);
  }
}
