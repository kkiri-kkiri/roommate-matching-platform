package com.kkirikkiri.community.post.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostImageStorageTest {
	@TempDir Path directory;

	@Test
	void storesBytesOutsideDatabaseAndRemovesThemOnRollback() throws Exception {
		var storage = new PostImageStorage(directory.toString());
		var file = new MockMultipartFile("images", "../../photo.png", "image/png", new byte[] {1, 2, 3});
		TransactionSynchronizationManager.initSynchronization();
		try {
			String url = storage.store(file);
			assertTrue(url.startsWith("/api/post-images/"));
			Path saved = directory.resolve(url.substring(url.lastIndexOf('/') + 1));
			assertArrayEquals(file.getBytes(), Files.readAllBytes(saved));
			TransactionSynchronizationManager.getSynchronizations().forEach(
					sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
			assertFalse(Files.exists(saved));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
