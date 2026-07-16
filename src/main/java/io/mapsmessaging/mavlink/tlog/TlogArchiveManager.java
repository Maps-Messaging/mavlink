package io.mapsmessaging.mavlink.tlog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

final class TlogArchiveManager implements AutoCloseable {

  private static final System.Logger LOGGER = System.getLogger(TlogArchiveManager.class.getName());
  private static final DateTimeFormatter ARCHIVE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
  private static final long CLEANUP_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1);

  private final Path activeFile;
  private final Path directory;
  private final String archivePrefix;
  private final Pattern archivePattern;
  private final boolean compressRotatedFiles;
  private final int retentionDays;
  private final ExecutorService executor;
  private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
  private final AtomicLong lastCleanupMillis = new AtomicLong();

  TlogArchiveManager(TlogConfiguration configuration) {
    this.activeFile = configuration.getFilePath();
    this.directory = activeFile.getParent();
    String fileName = activeFile.getFileName().toString();
    String stem = fileName.substring(0, fileName.length() - ".tlog".length());
    this.archivePrefix = stem + "-";
    this.archivePattern = Pattern.compile(Pattern.quote(archivePrefix) + "(\\d{8}T\\d{6}\\.\\d{3}Z)-\\d{4}\\.tlog(?:\\.gz)?");
    this.compressRotatedFiles = configuration.isCompressRotatedFiles();
    this.retentionDays = configuration.getRetentionDays();
    this.executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "mavlink-tlog-archive"));
  }

  void recoverAndClean() throws IOException {
    Files.createDirectories(directory);

    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
      for (Path file : files) {
        String fileName = file.getFileName().toString();
        if (fileName.startsWith(archivePrefix) && fileName.endsWith(".tlog.gz.tmp")) {
          Files.deleteIfExists(file);
        }
      }
    }

    cleanExpiredArchives(true);

    if (compressRotatedFiles) {
      try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
        for (Path file : files) {
          if (isUncompressedArchive(file)) {
            submit(file);
          }
        }
      }
    }
  }

  void submit(Path rotatedFile) {
    executor.execute(() -> {
      try {
        if (compressRotatedFiles) {
          compress(rotatedFile);
        }
        cleanExpiredArchives(false);
      } catch (Throwable throwable) {
        recordFailure("Failed to archive MAVLink tlog file " + rotatedFile, throwable);
      }
    });
  }

  Throwable getLastFailure() {
    return lastFailure.get();
  }

  private void compress(Path source) throws IOException {
    if (!Files.exists(source)) {
      return;
    }

    Path compressed = source.resolveSibling(source.getFileName() + ".gz");
    Path temporary = source.resolveSibling(source.getFileName() + ".gz.tmp");

    if (Files.exists(compressed)) {
      Files.deleteIfExists(source);
      return;
    }

    Files.deleteIfExists(temporary);
    try {
      try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
          OutputStream fileOutput = new BufferedOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
          GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
        input.transferTo(gzipOutput);
      }

      moveAtomicallyWhenPossible(temporary, compressed);
      Files.delete(source);
    } catch (IOException exception) {
      Files.deleteIfExists(temporary);
      throw exception;
    }
  }

  private void cleanExpiredArchives(boolean force) throws IOException {
    if (retentionDays == 0) {
      return;
    }

    long now = System.currentTimeMillis();
    long previous = lastCleanupMillis.get();
    if (!force && now - previous < CLEANUP_INTERVAL_MILLIS) {
      return;
    }
    if (!lastCleanupMillis.compareAndSet(previous, now) && !force) {
      return;
    }
    if (force) {
      lastCleanupMillis.set(now);
    }

    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
      for (Path file : files) {
        if (!isArchive(file)) {
          continue;
        }
        Instant archiveTime = archiveTime(file);
        if (archiveTime.isBefore(cutoff)) {
          Files.deleteIfExists(file);
        }
      }
    }
  }

  private Instant archiveTime(Path file) throws IOException {
    Matcher matcher = archivePattern.matcher(file.getFileName().toString());
    if (matcher.matches()) {
      try {
        return Instant.from(ARCHIVE_TIME_FORMAT.parse(matcher.group(1)));
      } catch (DateTimeParseException ignored) {
        // Fall back to the filesystem timestamp for legacy or malformed archive names.
      }
    }
    return Files.getLastModifiedTime(file).toInstant();
  }

  private boolean isArchive(Path file) {
    return !file.equals(activeFile) && archivePattern.matcher(file.getFileName().toString()).matches();
  }

  private boolean isUncompressedArchive(Path file) {
    return isArchive(file) && file.getFileName().toString().endsWith(".tlog");
  }

  private void recordFailure(String message, Throwable throwable) {
    lastFailure.set(throwable);
    LOGGER.log(System.Logger.Level.ERROR, message, throwable);
  }

  private static void moveAtomicallyWhenPossible(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target);
    }
  }

  @Override
  public void close() {
    executor.shutdown();
    boolean interrupted = false;
    while (true) {
      try {
        if (executor.awaitTermination(1, TimeUnit.DAYS)) {
          break;
        }
      } catch (InterruptedException exception) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
