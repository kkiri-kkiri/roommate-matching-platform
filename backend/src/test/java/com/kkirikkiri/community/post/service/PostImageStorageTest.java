package com.kkirikkiri.community.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

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

	@ParameterizedTest
	@CsvSource({"image/jpeg,.jpg", "image/png,.png", "image/webp,.webp", "image/gif,.gif"})
	void usesAllowedContentTypeForExtensionAndUniqueFileNames(String type, String extension) throws Exception {
		// given
		var storage = new PostImageStorage(directory.toString());
		var file = new MockMultipartFile("images", "same.exe", type, new byte[]{1});
		// when
		String first = storage.store(file);
		String second = storage.store(file);
		// then
		assertTrue(first.endsWith(extension));
		assertTrue(second.endsWith(extension));
		assertNotEquals(first, second);
		assertArrayEquals(file.getBytes(), Files.readAllBytes(storedPath(first)));
		assertArrayEquals(file.getBytes(), Files.readAllBytes(storedPath(second)));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"text/plain", "image/svg+xml"})
	void rejectsUnsupportedOrMissingContentTypeWithoutWritingFile(String type) throws Exception {
		// given
		var file = new MockMultipartFile("images", "photo.png", type, new byte[]{1});
		// when / then
		assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(CustomException.class,
				() -> new PostImageStorage(directory.toString()).store(file)).getErrorCode());
		assertDirectoryEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 5 * 1024 * 1024 + 1})
	void rejectsEmptyOrOversizedImageWithoutWritingFile(int size) throws Exception {
		// given
		var file = new MockMultipartFile("images", "photo.png", "image/png", new byte[size]);
		// when / then
		assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(CustomException.class,
				() -> new PostImageStorage(directory.toString()).store(file)).getErrorCode());
		assertDirectoryEmpty();
	}

	@Test
	void acceptsExactlyFiveMegabytes() throws Exception {
		// given
		var file = new MockMultipartFile("images", "photo.png", "image/png", new byte[5 * 1024 * 1024]);
		// when
		String url = new PostImageStorage(directory.toString()).store(file);
		// then
		assertEquals(file.getSize(), Files.size(storedPath(url)));
	}

	@Test
	void keepsStoredImageAfterTransactionCommit() {
		// given
		var storage = new PostImageStorage(directory.toString());
		var file = new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
		TransactionSynchronizationManager.initSynchronization();
		try {
			String url = storage.store(file);
			// when
			TransactionSynchronizationManager.getSynchronizations().forEach(
					sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
			// then
			assertTrue(Files.exists(storedPath(url)));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void translatesReadFailureAndRemovesPartiallyWrittenImage() throws Exception {
		// given
		MultipartFile file = mock(MultipartFile.class);
		when(file.getContentType()).thenReturn("image/png");
		when(file.getSize()).thenReturn(2L);
		InputStream input = mock(InputStream.class, CALLS_REAL_METHODS);
		when(file.getInputStream()).thenReturn(input);
		doAnswer(invocation -> {
			byte[] buffer = invocation.getArgument(0);
			buffer[(int) invocation.getArgument(1)] = 1;
			return 1;
		}).doThrow(new IOException("read failed")).when(input).read(any(byte[].class), anyInt(), anyInt());
		// when / then
		assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, assertThrows(CustomException.class,
				() -> new PostImageStorage(directory.toString()).store(file)).getErrorCode());
		assertDirectoryEmpty();
		verify(input).close();
	}

	private Path storedPath(String url) {
		return directory.resolve(url.substring(url.lastIndexOf('/') + 1));
	}

	@Test
	void filenameCollisionNeverOverwritesOrDeletesAnExistingFile() throws Exception {
		var id = java.util.UUID.randomUUID();
		Path existing = directory.resolve(id + ".png");
		byte[] original = {7, 8, 9};
		Files.write(existing, original);
		try (var uuids = mockStatic(java.util.UUID.class)) {
			uuids.when(java.util.UUID::randomUUID).thenReturn(id);
			var storage = new PostImageStorage(directory.toString());
			assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, assertThrows(CustomException.class,
					() -> storage.store(new MockMultipartFile("images", "new.png", "image/png", new byte[]{1}))).getErrorCode());
		}
		assertArrayEquals(original, Files.readAllBytes(existing));
	}

	private void assertDirectoryEmpty() throws IOException {
		try (var files = Files.list(directory)) {
			assertEquals(0, files.count());
		}
	}
}
