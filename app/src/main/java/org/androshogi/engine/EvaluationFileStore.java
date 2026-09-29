package org.androshogi.engine;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** A private copy of the user-selected NNUE model, installed without exposing partial files. */
public final class EvaluationFileStore {
    private EvaluationFileStore() {}

    public static File file(Context context) {
        return new File(new File(context.getFilesDir(), "eval"), "nn.bin");
    }

    public static long importFile(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new FileNotFoundException("Cannot open evaluation file");
            }
            return copy(input, file(context));
        }
    }

    /** Copies on a worker thread; an error leaves the previously installed nn.bin intact. */
    static long copy(InputStream input, File destination) throws IOException {
        File directory = destination.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create evaluation directory");
        }
        File temporary = File.createTempFile("nn-", ".tmp", directory);
        try {
            long bytes = 0;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    bytes += count;
                }
                if (bytes == 0) {
                    throw new IOException("Evaluation file is empty");
                }
                output.getFD().sync();
            }
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return bytes;
        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }
}
