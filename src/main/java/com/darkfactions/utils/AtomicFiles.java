package com.darkfactions.utils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe file writing, free of any Bukkit/Paper types so the durability
 * mechanics can be unit tested without a server.
 *
 * <p>The previous persistence code called {@code config.save(file)} directly on
 * the live data file, which truncates it in place — a crash, kill, or full disk
 * mid-write would leave the file truncated or empty with no recovery. This helper
 * writes to a sibling {@code .tmp}, fsyncs it, preserves the previous good file as
 * a {@code .bak}, and swaps the new file in with an atomic rename.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Writes content to the supplied temp file; may throw on I/O failure. */
    @FunctionalInterface
    public interface FileWriter {
        void writeTo(File tmp) throws IOException;
    }

    public static File tempFile(File target) {
        return new File(target.getParentFile(), target.getName() + ".tmp");
    }

    public static File backupFile(File target) {
        return new File(target.getParentFile(), target.getName() + ".bak");
    }

    /**
     * Durably and atomically replace {@code target}. The {@code writer} writes the
     * new content to a sibling {@code .tmp} file, which is fsynced to disk; the
     * current target (if any) is preserved as {@code .bak}; then the temp file is
     * moved into place with an atomic rename (falling back to a plain replace only
     * if the filesystem does not support atomic moves).
     *
     * <p>On any failure the original target file is left untouched.
     */
    public static void writeAtomically(File target, FileWriter writer) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        File tmp = tempFile(target);
        writer.writeTo(tmp);

        // Flush the new contents to disk before we swap it in, so the rename can
        // never expose a file whose data hasn't actually been persisted.
        try (FileChannel ch = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE)) {
            ch.force(true);
        }

        // Keep the previous good copy as a backup for recovery on a corrupt write.
        if (target.exists()) {
            Files.copy(target.toPath(), backupFile(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
