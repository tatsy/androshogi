package org.androshogi.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
