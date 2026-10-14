package com.kkirikkiri.global.init;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.kkirikkiri.user.entity.User;

/** Local-only provenance, independent of email naming. Missing/changed markers never authorize deletion. */
@Component
@Profile("local & !prod & !test")
public class SeedUserManifest {
    private final Path path;

    public SeedUserManifest(@Value("${app.seed.manifest:local-data/seed-users.properties}") String path) {
        this.path = Path.of(path).toAbsolutePath().normalize();
    }

    public synchronized boolean contains(User user) {
        return fingerprint(user).equals(read().getProperty(user.getUserId().toString()));
    }

    public void registerAfterCommit(User user) {
        String id = user.getUserId().toString(), fingerprint = fingerprint(user);
        afterCommit(() -> update(properties -> properties.setProperty(id, fingerprint)));
    }

    public void removeAfterCommit(Collection<Long> ids) {
        var keys = ids.stream().map(Object::toString).toList();
        afterCommit(() -> update(properties -> keys.forEach(properties::remove)));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Seed provenance requires a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private synchronized void update(java.util.function.Consumer<Properties> change) {
        Properties properties = read();
        change.accept(properties);
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), "seed-users-", ".tmp");
            try {
                try (var output = Files.newOutputStream(temporary)) {
                    properties.store(output, "Local seed provenance; do not copy between databases");
                }
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot persist local seed provenance", failure);
        }
    }

    private Properties read() {
        Properties properties = new Properties();
        if (!Files.exists(path)) return properties;
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot read local seed provenance; seed changes refused", failure);
        }
    }

    private String fingerprint(User user) {
        try {
            // Neither the plaintext password nor its BCrypt hash is written to the manifest.
            String identity = user.getUserId() + "\n" + user.getSchoolEmail() + "\n" + user.getPassword();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
