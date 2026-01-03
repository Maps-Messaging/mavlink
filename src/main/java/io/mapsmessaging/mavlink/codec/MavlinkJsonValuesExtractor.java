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

package io.mapsmessaging.mavlink.codec;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavlinkJsonValuesExtractor {

  public Map<String, Object> extractValues(JsonObject jsonObject, MavlinkCompiledMessage compiledMessage) {
    Map<String, Object> values = new HashMap<>();

    for (MavlinkCompiledField compiledField : compiledMessage.getCompiledFields()) {
      String fieldName = compiledField.getFieldDefinition().getName();
      if (!jsonObject.has(fieldName)) {
        continue;
      }

      if (jsonObject.get(fieldName).isJsonArray()) {
        List<Object> list = parseArray(jsonObject.getAsJsonArray(fieldName));
        values.put(fieldName, list);
        continue;
      }

      if (jsonObject.get(fieldName).isJsonPrimitive()) {
        Object value = parsePrimitive(jsonObject.getAsJsonPrimitive(fieldName));
        if (value != null) {
          values.put(fieldName, value);
        }
      }
    }

    return values;
  }

  private List<Object> parseArray(JsonArray array) {
    List<Object> list = new ArrayList<>(array.size());
    for (int index = 0; index < array.size(); index++) {
      if (!array.get(index).isJsonPrimitive()) {
        continue;
      }

      Object value = parsePrimitive(array.get(index).getAsJsonPrimitive());
      if (value != null) {
        list.add(value);
      }
    }
    return list;
  }

  private Object parsePrimitive(JsonPrimitive primitive) {
    if (primitive.isNumber()) {
      return primitive.getAsNumber();
    }
    if (primitive.isString()) {
      return primitive.getAsString();
    }
    if (primitive.isBoolean()) {
      return primitive.getAsBoolean() ? 1 : 0;
    }
    return null;
  }
}
