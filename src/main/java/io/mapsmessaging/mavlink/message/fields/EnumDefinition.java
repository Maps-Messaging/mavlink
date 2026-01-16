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

package io.mapsmessaging.mavlink.message.fields;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
public class EnumDefinition {
  @Getter
  @Setter
  private String name;
  @Getter
  @Setter
  private boolean bitmask;
  @Getter
  @Setter
  private String description;
  private List<EnumEntry> entries = new ArrayList<>();

  public List<EnumEntry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  public void setEntries(List<EnumEntry> list) {
    entries = Collections.unmodifiableList(list);
  }

  public EnumEntry getByName(String name) {
    if (name == null) {
      return null;
    }
    for (EnumEntry entry : entries) {
      if (name.equals(entry.getName())) {
        return entry;
      }
    }
    return null;
  }

  public boolean hasEntry(String name) {
    return getByName(name) != null;
  }

  public EnumEntry getByValue(long value) {
    for (EnumEntry entry : entries) {
      if (entry.getValue() == value) {
        return entry;
      }
    }
    return null;
  }

  public List<EnumEntry> getByBitmask(long mask) {
    if (!bitmask || mask == 0) {
      return List.of();
    }

    List<EnumEntry> result = new ArrayList<>();

    for (EnumEntry entry : entries) {
      long value = entry.getValue();
      if ((mask & value) == value) {
        result.add(entry);
      }
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(name)
        .append(", bitmask=").append(bitmask)
        .append(", description=").append(description).append("\n");
    for (EnumEntry entry : entries) {
      builder.append(entry.toString()).append("\n");
    }
    return builder.toString();
  }

}