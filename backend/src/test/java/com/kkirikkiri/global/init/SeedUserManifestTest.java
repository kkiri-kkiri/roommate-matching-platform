package com.kkirikkiri.global.init;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.kkirikkiri.user.entity.User;

class SeedUserManifestTest {
    @TempDir Path directory;
    SeedUserManifest manifest;
    User seed;
    @BeforeEach void setup() {
        manifest = new SeedUserManifest(directory.resolve("seed.properties").toString());
        seed = new User("unique-encoded-password", "seed_user01@hknu.ac.kr");
        ReflectionTestUtils.setField(seed,"userId",1L);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    @AfterEach void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    private void commit() { TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit()); }

    @Test void recordsOnlyCommittedNewUsersAndNeverAdoptsMatchingEmail() {
        assertFalse(manifest.contains(seed));
        manifest.registerAfterCommit(seed);
        assertFalse(manifest.contains(seed));
        commit();
        assertTrue(new SeedUserManifest(directory.resolve("seed.properties").toString()).contains(seed));
        User real = new User("different-password","seed_user01@hknu.ac.kr");
        ReflectionTestUtils.setField(real,"userId",1L);
        assertFalse(manifest.contains(real));
        seed.changePassword("changed-password");
        assertFalse(manifest.contains(seed));
    }
    @Test void rollbackLeavesNoMarker() {
        manifest.registerAfterCommit(seed);
        assertFalse(manifest.contains(seed));
        // Rollback never calls afterCommit.
        assertFalse(Files.exists(directory.resolve("seed.properties")));
    }
    @Test void deletesMarkerOnlyAfterSuccessfulDeletionCommit() {
        manifest.registerAfterCommit(seed); commit();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        manifest.removeAfterCommit(List.of(1L));
        assertTrue(manifest.contains(seed)); commit(); assertFalse(manifest.contains(seed));
    }
    @Test void corruptManifestRefusesChanges() throws Exception {
        Files.writeString(directory.resolve("seed.properties"),"bad=\\uZZZZ");
        assertThrows(IllegalStateException.class, () -> manifest.contains(seed));
    }
}
