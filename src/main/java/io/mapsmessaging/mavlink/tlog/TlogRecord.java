package io.mapsmessaging.mavlink.tlog;

import java.util.Arrays;

record TlogRecord(long timestampMicros, byte[] frame, boolean stop) {

  static final TlogRecord STOP = new TlogRecord(0, new byte[0], true);

  static TlogRecord data(long timestampMicros, byte[] frame) {
    return new TlogRecord(timestampMicros, frame, false);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TlogRecord record)) {
      return false;
    }
    return timestampMicros == record.timestampMicros
        && stop == record.stop
        && Arrays.equals(frame, record.frame);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(timestampMicros);
    result = 31 * result + Arrays.hashCode(frame);
    return 31 * result + Boolean.hashCode(stop);
  }

  @Override
  public String toString() {
    return "TlogRecord[timestampMicros=" + timestampMicros
        + ", frame=" + Arrays.toString(frame)
        + ", stop=" + stop + ']';
  }
}
