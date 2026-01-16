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

package io.mapsmessaging.mavlink.signing;

import io.mapsmessaging.mavlink.framing.SigningKeyProvider;
import lombok.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapSigningKeyProvider implements SigningKeyProvider {

  private final Map<SigningKey, byte[]> signingKeys;

  public MapSigningKeyProvider() {
    signingKeys = new ConcurrentHashMap<>();
  }

  public void register(int systemId, int componentId, int linkId, byte[] signature) {
    SigningKey signingKey = new SigningKey(systemId, componentId, linkId);
    signingKeys.put(signingKey, signature);
  }

  public void unregister(int systemId, int componentId, int linkId) {
    signingKeys.remove(new SigningKey(systemId, componentId, linkId));

  }

  @Override
  public boolean canValidate() {
    return true;
  }

  @Override
  public byte[] getSigningKey(int systemId, int componentId, int linkId) {
    SigningKey signingKey = new SigningKey(systemId, componentId, linkId);
    return signingKeys.get(signingKey);
  }

  @Value
  private static class SigningKey{
    int systemId;
    int componentId;
    int linkId;
  }
}