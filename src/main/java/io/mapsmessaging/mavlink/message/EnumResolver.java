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


import io.mapsmessaging.mavlink.message.fields.EnumDefinition;
import io.mapsmessaging.mavlink.message.fields.EnumEntry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EnumResolver {

  private EnumResolver() {
  }

  public static Object resolveEnumValue(
      MessageRegistry registry,
      FieldDefinition field,
      Object value
  ) throws IOException {

    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(field, "field");

    String enumName = field.getEnumName();
    if (enumName == null || enumName.isEmpty()) {
      return value; // ← THIS is the key line
    }

    if (value == null) {
      throw new IOException("Enum field '" + field.getName() + "' cannot be null");
    }

    EnumDefinition enumDef = registry.getEnumsByName().get(enumName);
    if (enumDef == null) {
      throw new IOException("Enum '" + enumName + "' not registered");
    }

    if (value instanceof Number n) {
      return n.intValue();
    }

    if (value instanceof String s) {
      EnumEntry entry = enumDef.getByName(s);
      if (entry == null) {
        throw new IOException(
            "Unknown enum value '" + s + "' for enum '" + enumName + "'");
      }
      return (int) entry.getValue();
    }

    if (value instanceof Object[] arr) {
      if (!enumDef.isBitmask()) {
        List<Integer> res = new ArrayList<>();
        for(Object item:arr){
          if(item instanceof Number num){
            res.add(num.intValue());
          }
        }
        return res;
      }

      int mask = 0;
      List<Integer> res = new ArrayList<>();
      for (Object element : arr) {
        EnumEntry entry;
        if(element instanceof Number index){
          List<EnumEntry> entries = enumDef.getByBitmask(index.longValue());
          mask = 0;
          for(EnumEntry mavlinkEnumEntry:entries){
            mask |= mavlinkEnumEntry.getValue();
          }
          res.add(mask);
        }
        else if(element instanceof String name) {
          entry = enumDef.getByName(name);
          if (entry == null) {
            throw new IOException("Unknown enum value '" + enumName + "' for enum '" + enumName + "'");
          }
          mask = (int)enumDef.getByName(name).getValue();
          res.add(mask);
        }
        else{
          throw new IOException("Bitmask enum '" + enumName + "' expects string values");
        }
      }
      return res;
    }
    throw new IOException("Unsupported enum value type for field '" + field.getName() + "': " + value.getClass().getName());
  }
}
