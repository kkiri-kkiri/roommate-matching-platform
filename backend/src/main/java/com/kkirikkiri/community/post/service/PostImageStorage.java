package com.kkirikkiri.community.post.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
		try {
			Files.createDirectories(directory);
			try (var input = file.getInputStream()) {
				Files.copy(input, target);
			}
		} catch (IOException exception) {
			deleteQuietly(target);
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
		return "/api/post-images/" + target.getFileName();
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			org.slf4j.LoggerFactory.getLogger(PostImageStorage.class).warn("Failed to remove image {}", path, exception);
		}
	}
}
