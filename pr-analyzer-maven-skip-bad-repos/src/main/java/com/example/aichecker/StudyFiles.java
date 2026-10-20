package com.example.aichecker;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Atomic, forced checkpoints; readers never consume a partially written record. */
final class StudyFiles {
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String hash(Path path) throws IOException { return hash(Files.readAllBytes(path)); }
    static void atomic(Path path, byte[] bytes) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (!Files.isDirectory(parent)) Files.createDirectories(parent);
        parent = parent.toRealPath();
        Path destination = parent.resolve(path.getFileName());
        Path temp = Files.createTempFile(parent, ".checkpoint-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
                directory.force(true);
            }
        } finally { Files.deleteIfExists(temp); }
    }
    static void text(Path path, String text) throws IOException { atomic(path, text.getBytes(StandardCharsets.UTF_8)); }
    static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        return p;
    }
    static void properties(Path path, Properties p) throws IOException {
        // Sorted and timestamp-free so equivalent configurations have identical hashes.
        StringBuilder text = new StringBuilder();
        for (String key : new TreeSet<>(p.stringPropertyNames())) {
            text.append(escape(key)).append('=').append(escape(p.getProperty(key))).append('\n');
        }
        text(path, text.toString());
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                .replace("=", "\\=").replace(":", "\\:").replace("#", "\\#").replace("!", "\\!").replace(" ", "\\ ");
    }
    static Properties values(String... pairs) {
        Properties p = new Properties();
        for (int i = 0; i < pairs.length; i += 2) p.setProperty(pairs[i], pairs[i + 1]);
        return p;
    }
    static void record(Path path, Record record) throws IOException {
        Properties p = new Properties();
        try {
            for (var c : record.getClass().getRecordComponents()) {
                Object value = c.getAccessor().invoke(record);
                p.setProperty(c.getName(), value == null ? "" : value.toString());
            }
        } catch (ReflectiveOperationException e) { throw new IOException(e); }
        properties(path, p);
    }
    static <T> T record(Path path, Class<T> type) throws IOException {
        Properties p = read(path);
        try {
            var components = type.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                String value = Objects.requireNonNull(p.getProperty(components[i].getName()), "Missing checkpoint field");
                values[i] = types[i] == int.class ? Integer.parseInt(value) : types[i] == boolean.class ? Boolean.valueOf(value) : value;
            }
            return type.getDeclaredConstructor(types).newInstance(values);
        } catch (Exception e) { throw new IOException("Invalid checkpoint: " + path, e); }
    }
    static List<Path> files(Path dir, String suffix) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (var paths = Files.list(dir)) { return paths.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList(); }
    }
    static String id(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
}
