package net.ornithemc.ploceus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class CacheFiles {

	public static final Duration ONE_HOUR = Duration.ofHours(1L);
	public static final Duration ONE_DAY = Duration.ofDays(1L);

	public static boolean isStale(Path file, Duration maxAge) throws IOException {
		Instant modifiedTime = Files.getLastModifiedTime(file).toInstant();
		Instant minModifiedTime = Instant.now().minus(maxAge);

		return modifiedTime.isBefore(minModifiedTime);
	}
}
