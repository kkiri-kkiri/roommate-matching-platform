package com.kkirikkiri.community.post.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

@Component
public class PostImageStorage {
	public static final String URL_PREFIX = "/api/post-images/";
	private static final int MAX_BYTES = 5 * 1024 * 1024;

	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
	private final Path directory;

	public PostImageStorage(@Value("${app.community.image-directory:uploads/community}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	public String store(MultipartFile file) {
		String extension = file.getContentType() == null ? null : EXTENSIONS.get(file.getContentType());
		if (extension == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		Path target = directory.resolve(UUID.randomUUID() + extension);
		boolean created = false;
		try {
			Files.createDirectories(directory);
			// CREATE_NEW never replaces a file used by another upload/reference.
			try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
				created = true;
				try (var input = file.getInputStream()) {
					input.transferTo(output);
				}
			}
		} catch (IOException exception) {
			if (created) deleteQuietly(target);
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) deleteQuietly(target);
				}
			});
		}
		return URL_PREFIX + target.getFileName();
	}

	/** Accept historical safe basenames as well as UUID names; never accept URL/path aliases. */
	public static boolean isSafeFilename(String filename) {
		return filename != null && filename.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,199}\\.(jpg|jpeg|png|webp|gif)");
	}

	public static Optional<String> filenameFromUrl(String url) {
		if (url == null || !url.startsWith(URL_PREFIX)) return Optional.empty();
		String filename = url.substring(URL_PREFIX.length());
		return isSafeFilename(filename) ? Optional.of(filename) : Optional.empty();
	}

	private Path checkedPath(String filename) throws IOException {
		if (!isSafeFilename(filename)) throw new IOException("Invalid image identifier");
		Path root = directory.toRealPath();
		Path target = root.resolve(filename).normalize();
		if (!target.getParent().equals(root) || Files.isSymbolicLink(target)) {
			throw new IOException("Invalid image location");
		}
		return target;
	}

	public byte[] read(String filename) throws IOException {
		Path target = checkedPath(filename);
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Image unavailable");
		try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
			byte[] bytes = input.readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES) throw new IOException("Image unavailable");
			return bytes;
		}
	}

	/** Missing files succeed; missing/unavailable roots and nonregular targets must be retried. */
	public void delete(String filename) throws IOException {
		Path target = checkedPath(filename);
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
				&& !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Invalid image location");
		}
		Files.deleteIfExists(target);
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			org.slf4j.LoggerFactory.getLogger(PostImageStorage.class).warn("Image upload rollback cleanup failed");
		}
	}
}
