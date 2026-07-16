package io.mapsmessaging.mavlink.tlog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TlogConfigurationTest {

  @Test
  void appliesDefaults() {
    Path file = Path.of("telemetry.tlog");

    TlogConfiguration configuration = TlogConfiguration.builder(file).build();

    assertEquals(file.toAbsolutePath().normalize(), configuration.getFilePath());
    assertEquals(64L * 1024L * 1024L, configuration.getMaximumFileSizeBytes());
    assertEquals(Duration.ofHours(1), configuration.getMaximumFileAge());
    assertEquals(7, configuration.getRetentionDays());
    assertEquals(8192, configuration.getQueueCapacity());
    assertEquals(Duration.ofSeconds(1), configuration.getFlushInterval());
    assertTrue(configuration.isCompressRotatedFiles());
    assertTrue(configuration.isRotateExistingFileOnStartup());
  }

  @Test
  void acceptsDisabledSizeAgeAndRetentionLimits() {
    TlogConfiguration configuration =
        TlogConfiguration.builder(Path.of("telemetry.tlog"))
            .maximumFileSizeBytes(0)
            .maximumFileAge(Duration.ZERO)
            .retentionDays(0)
            .compressRotatedFiles(false)
            .rotateExistingFileOnStartup(false)
            .build();

    assertEquals(0, configuration.getMaximumFileSizeBytes());
    assertEquals(Duration.ZERO, configuration.getMaximumFileAge());
    assertEquals(0, configuration.getRetentionDays());
    assertFalse(configuration.isCompressRotatedFiles());
    assertFalse(configuration.isRotateExistingFileOnStartup());
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThrows(NullPointerException.class, () -> TlogConfiguration.builder(null).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.log")).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).maximumFileSizeBytes(-1).build());
    assertThrows(NullPointerException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).maximumFileAge(null).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).maximumFileAge(Duration.ofSeconds(-1)).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).retentionDays(-1).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).queueCapacity(0).build());
    assertThrows(NullPointerException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).flushInterval(null).build());
    assertThrows(IllegalArgumentException.class, () -> TlogConfiguration.builder(Path.of("telemetry.tlog")).flushInterval(Duration.ofMillis(-1)).build());
  }
}
