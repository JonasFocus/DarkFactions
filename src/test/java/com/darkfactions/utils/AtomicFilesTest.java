package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    @TempDir
    File dir;

    private void write(File target, String content) throws Exception {
        AtomicFiles.writeAtomically(target, tmp ->
                Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void writesContentAndLeavesNoTempFile() throws Exception {
        File target = new File(dir, "data.yml");
        write(target, "first");
        assertTrue(target.exists());
        assertEquals("first", Files.readString(target.toPath()));
        assertFalse(AtomicFiles.tempFile(target).exists(), "temp file must be cleaned up after the swap");
    }

    @Test
    void secondWritePreservesPreviousAsBackup() throws Exception {
        File target = new File(dir, "data.yml");
        write(target, "v1");
        write(target, "v2");
        assertEquals("v2", Files.readString(target.toPath()));
        assertEquals("v1", Files.readString(AtomicFiles.backupFile(target).toPath()),
                ".bak must hold the previous good content for recovery");
    }

    @Test
    void firstWriteCreatesNoBackup() throws Exception {
        File target = new File(dir, "data.yml");
        write(target, "only");
        assertFalse(AtomicFiles.backupFile(target).exists());
    }

    @Test
    void createsParentDirectories() throws Exception {
        File target = new File(dir, "nested/sub/data.yml");
        write(target, "x");
        assertTrue(target.exists());
        assertEquals("x", Files.readString(target.toPath()));
    }
}
