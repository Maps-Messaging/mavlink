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

/**
 * MAVLink CRC-16/X25 implementation.
 *
 * Parameters:
 * Poly   : 0x1021 (reflected as 0x8408)
 * Init   : 0xFFFF
 * RefIn  : true
 * RefOut : true
 * XorOut : 0x0000  (IMPORTANT: MAVLink does NOT apply final XOR)
 */
public final class X25Crc {

  private static final int INITIAL_CRC = 0xFFFF;
  private static final int POLYNOMIAL = 0x8408;

  private int currentCrc;

  public X25Crc() {
    reset();
  }

  public static int calculate(byte[] buffer, int offset, int length) {
    X25Crc crc = new X25Crc();
    crc.update(buffer, offset, length);
    return crc.getCrc();
  }

  public static int calculate(byte[] buffer) {
    if (buffer == null) {
      return INITIAL_CRC;
    }
    return calculate(buffer, 0, buffer.length);
  }

  public void reset() {
    currentCrc = INITIAL_CRC;
  }

  public void update(byte value) {
    update(value & 0xFF);
  }

  public void update(int value) {
    int data = value & 0xFF;
    currentCrc ^= data;

    for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
      if ((currentCrc & 0x0001) != 0) {
        currentCrc = (currentCrc >>> 1) ^ POLYNOMIAL;
      } else {
        currentCrc = currentCrc >>> 1;
      }
    }

    currentCrc = currentCrc & 0xFFFF;
  }

  public void update(byte[] buffer) {
    if (buffer == null) {
      return;
    }
    update(buffer, 0, buffer.length);
  }

  public void update(byte[] buffer, int offset, int length) {
    if (buffer == null) {
      return;
    }

    int endIndex = offset + length;
    for (int index = offset; index < endIndex; index++) {
      update(buffer[index] & 0xFF);
    }
  }

  /**
   * MAVLink final CRC (no xor-out).
   */
  public int getCrc() {
    return currentCrc & 0xFFFF;
  }

  public short getCrcAsShort() {
    return (short) (currentCrc & 0xFFFF);
  }

  /**
   * Same as getCrc(); kept for compatibility if callers used it.
   */
  public int getRawCrc() {
    return currentCrc & 0xFFFF;
  }
}
