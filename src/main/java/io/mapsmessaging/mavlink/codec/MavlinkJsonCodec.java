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

package io.mapsmessaging.mavlink.codec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkFrame;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class MavlinkJsonCodec {

  private static final Gson GSON = new Gson();

  private final String dialectName;
  private final MavlinkPayloadPacker packer;
  private final MavlinkMapConverter mapConverter;
  private final MavlinkJsonValuesExtractor valuesExtractor;
  private final MavlinkFrameEncoder frameEncoder;

  public MavlinkJsonCodec(String dialectName, MavlinkPayloadPacker packer, MavlinkMapConverter mapConverter) {
    this.dialectName = Objects.requireNonNull(dialectName, "dialectName");
    this.packer = Objects.requireNonNull(packer, "packer");
    this.mapConverter = Objects.requireNonNull(mapConverter, "mapConverter");

    this.valuesExtractor = new MavlinkJsonValuesExtractor();
    this.frameEncoder = new MavlinkFrameEncoder();
  }



  public JsonObject toJson(MavlinkFrame frame) throws IOException {
    Map<String, Object> map = mapConverter.convert(frame);
    String json = GSON.toJson(map);
    return JsonParser.parseString(json).getAsJsonObject();
  }

  public byte[] fromJson(JsonObject jsonObject) throws IOException {
    if (jsonObject == null) {
      throw new IOException("JSON object is null");
    }
    if (!jsonObject.has("messageId")) {
      throw new IOException("Missing required field 'messageId' in MAVLink JSON");
    }

    int messageId = jsonObject.get("messageId").getAsInt();
    int systemId = jsonObject.has("systemId") ? jsonObject.get("systemId").getAsInt() : 1;
    int componentId = jsonObject.has("componentId") ? jsonObject.get("componentId").getAsInt() : 1;
    int sequence = jsonObject.has("sequence") ? jsonObject.get("sequence").getAsInt() : 0;

    MavlinkMessageRegistry registry = packer.getMessageRegistry();
    MavlinkCompiledMessage compiledMessage = registry.getCompiledMessagesById().get(messageId);
    if (compiledMessage == null) {
      throw new IOException("Unknown MAVLink message id: " + messageId + " for dialect " + dialectName);
    }

    Map<String, Object> values = valuesExtractor.extractValues(jsonObject, compiledMessage);
    byte[] payload = packer.packPayload(messageId, values);

    return frameEncoder.encodeV2Frame(sequence, systemId, componentId, messageId, payload, compiledMessage);
  }
}
