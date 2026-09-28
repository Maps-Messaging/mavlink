package io.mapsmessaging.mavlink.tlog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TlogRecordTest {

  @Test
  void valueMethodsCompareFrameContents() {
    TlogRecord first = TlogRecord.data(123L, new byte[]{1, 2});
    TlogRecord equivalent = TlogRecord.data(123L, new byte[]{1, 2});
    assertEquals(first, equivalent);
    assertEquals(first.hashCode(), equivalent.hashCode());
    assertEquals(first, first);
    assertNotEquals(first, TlogRecord.data(124L, new byte[]{1, 2}));
    assertNotEquals(first, TlogRecord.data(123L, new byte[]{1, 3}));
    assertNotEquals(first, new TlogRecord(123L, new byte[]{1, 2}, true));
    assertNotEquals(first, TlogRecord.STOP);
    assertNotEquals(first, "not a record");
    assertTrue(first.toString().contains("frame=[1, 2]"));
  }
}
