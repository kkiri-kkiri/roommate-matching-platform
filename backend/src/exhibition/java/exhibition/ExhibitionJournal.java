package exhibition;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Local provenance and crash barrier. APPLYING is never automatically retried or adopted. */
public final class ExhibitionJournal implements AutoCloseable {
    public record Owned(String kind, long id, long owner, String desired, String snapshot, Map<String, Long> children) {
        public Owned { children = Collections.unmodifiableMap(new TreeMap<>(children)); }
    }
    public record State(String database, String anchor, String status, Map<String, Owned> owned) {
        public State { owned = Collections.unmodifiableMap(new TreeMap<>(owned)); }
    }
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    public ExhibitionJournal(Path path) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        Files.createDirectories(this.path.getParent());
        channel = FileChannel.open(this.path.resolveSibling(this.path.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = channel.tryLock(); }
        catch (RuntimeException | IOException failure) { channel.close(); throw failure; }
        if (acquired == null) { channel.close(); throw new IllegalStateException("Another exhibition operation is running"); }
        lock = acquired;
    }
    public State read(String database, String anchor) throws IOException {
        if (!Files.exists(path)) return new State(database, anchor, "COMPLETE", Map.of());
        State state = JSON.readValue(Files.readString(path), State.class);
        if (!database.equals(state.database()) || !anchor.equals(state.anchor()))
            throw new IllegalStateException("Journal database/anchor mismatch; never copy provenance to another database");
        if (!"COMPLETE".equals(state.status()))
            throw new IllegalStateException("Incomplete exhibition run. Stop and restore the verified database/journal backup; do not retry or delete the journal");
        return state;
    }
    public void write(State state) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "exhibition-", ".tmp");
        try {
            byte[] bytes = JSON.writeValueAsBytes(state);
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(value)));
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void close() throws IOException { lock.release(); channel.close(); }
}
