package org.androshogi.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class EvaluationFileStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void architecturesKeepIndependentModelsAndPreserveLegacyLocation() throws IOException {
        File filesDir = folder.newFolder("files");
        File legacy = new File(new File(filesDir, "eval"), "nn.bin");
        Files.createDirectories(legacy.getParentFile().toPath());
        byte[] original = new byte[] {1, 2};
        Files.write(legacy.toPath(), original);
        assertEquals(legacy, EvaluationFileStore.file(filesDir, EngineKind.DEFAULT));

        for (EngineKind kind : EngineKind.values()) {
            if (kind != EngineKind.DEFAULT) {
                File destination = EvaluationFileStore.file(filesDir, kind);
                assertFalse(destination.exists());
                byte[] model = new byte[] {(byte) kind.ordinal(), 9};
                EvaluationFileStore.copy(new ByteArrayInputStream(model), destination);
                assertArrayEquals(model, Files.readAllBytes(destination.toPath()));
            }
        }
        for (EngineKind kind : EngineKind.values()) {
            if (kind != EngineKind.DEFAULT) {
                assertArrayEquals(new byte[] {(byte) kind.ordinal(), 9},
                        Files.readAllBytes(EvaluationFileStore.file(filesDir, kind).toPath()));
            }
        }
        assertArrayEquals(original, Files.readAllBytes(legacy.toPath()));
    }

    @Test
    public void successfulImportReplacesEvaluationFile() throws IOException {
        File destination = new File(folder.newFolder("eval"), "nn.bin");
        Files.write(destination.toPath(), new byte[] {1, 2});
        byte[] replacement = new byte[] {3, 4, 5};

        assertEquals(replacement.length, EvaluationFileStore.copy(
                new ByteArrayInputStream(replacement), destination));
        assertArrayEquals(replacement, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void failedImportKeepsPreviousEvaluationFile() throws IOException {
        File directory = folder.newFolder("eval");
        File destination = new File(directory, "nn.bin");
        byte[] original = new byte[] {1, 2};
        Files.write(destination.toPath(), original);
        InputStream interrupted = new InputStream() {
            private int readCount;
            @Override public int read() throws IOException {
                if (++readCount > 2) {
                    throw new IOException("read interrupted");
                }
                return 9;
            }
        };

        try {
            EvaluationFileStore.copy(interrupted, destination);
            fail("A failed read should not replace the installed file");
        } catch (IOException expected) {
            assertArrayEquals(original, Files.readAllBytes(destination.toPath()));
            assertEquals(1, directory.listFiles().length);
        }
    }
}
