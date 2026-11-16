package org.androshogi.storage;

import org.androshogi.settings.AppSettings;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Saved games as files under {@code filesDir/games/<id>.json}, plus the id of
 * the game to reopen on the next start (kept in the preferences).
 *
 * <p>Saving never throws: a failure is logged and reported by the return
 * value, since losing a save must not take the app down with it.
 */
public final class GameStore {
    private static final String TAG = "GameStore";
    private static final String DIRECTORY = "games";
    /** MainActivity and the list have independent I/O executors and GameStore instances. */
    private static final Object WRITE_LOCK = new Object();
    /** Ids come from {@link #newId()}, but the one read back from the preferences is checked before it names a file. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final Context context;
    private final File dir;

    public GameStore(Context context) {
        this.context = context.getApplicationContext();
        this.dir = new File(this.context.getFilesDir(), DIRECTORY);
    }

    /** A new id: the creation time plus a few random digits against two games in the same second. */
    public static String newId() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        int random = new Random().nextInt(0x10000);
        return String.format(Locale.US, "%s-%04x", stamp, random);
    }

    /** Writes the game and remembers it as the one to reopen. */
    public boolean save(SavedGame game) {
        synchronized (WRITE_LOCK) {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("cannot create " + dir);
                }
                File target = fileFor(game.id);
                // A detached gameplay snapshot may predate a move between collections.
                // The on-disk flag remains authoritative; new records start in history.
                boolean saved = target.exists() && GameJson.isSaved(readText(target));
                writeAtomically(target, GameJson.write(game, saved));
                AppSettings.setLastGameId(context, game.id);
                return true;
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Failed to save game " + game.id, e);
                return false;
            }
        }
    }

    /** Moves an existing record without making it the current game or changing its update time. */
    public boolean setSaved(String id, boolean saved) {
        if (!SAFE_ID.matcher(id).matches()) return false;
        synchronized (WRITE_LOCK) {
            File target = fileFor(id);
            if (!target.isFile()) return false;
            try {
                writeAtomically(target, GameJson.withSaved(readText(target), saved));
                return true;
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Failed to move game " + id, e);
                return false;
            }
        }
    }

    private void writeAtomically(File target, String json) throws IOException {
        File temp = File.createTempFile("game-", ".tmp", dir);
        try {
            try (FileOutputStream output = new FileOutputStream(temp);
                 Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(json);
                writer.flush();
                output.getFD().sync();
            }
            // Replacing in the same directory is atomic; a failed replacement keeps the old file.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    /** The game shown when the app was last stopped, or null when there is none or it cannot be read. */
    @Nullable
    public SavedGame loadLast() {
        String id = AppSettings.lastGameId(context);
        return id == null ? null : load(id);
    }

    @Nullable
    public SavedGame load(String id) {
        if (!SAFE_ID.matcher(id).matches()) {
            Log.w(TAG, "Refusing to load game with id " + id);
            return null;
        }
        File file = fileFor(id);
        if (!file.isFile()) {
            return null;
        }
        try {
            return GameJson.read(readText(file));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Failed to load game " + id, e);
            return null;
        }
    }

    /**
     * Every readable saved game, most recently updated first. A file that
     * cannot be read is logged and left out rather than hiding the rest.
     * Reads every file, so call it off the main thread.
     */
    public List<GameSummary> list() {
        List<GameSummary> games = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return games;
        }
        for (File file : files) {
            try {
                games.add(GameJson.readSummary(readText(file)));
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Skipping unreadable game file " + file.getName(), e);
            }
        }
        Collections.sort(games, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return games;
    }

    /** Removes the game's file; if it was the one to reopen, nothing is reopened next time. */
    public boolean delete(String id) {
        if (!SAFE_ID.matcher(id).matches()) {
            Log.w(TAG, "Refusing to delete game with id " + id);
            return false;
        }
        synchronized (WRITE_LOCK) {
            File file = fileFor(id);
            boolean deleted = !file.exists() || file.delete();
            if (deleted && id.equals(AppSettings.lastGameId(context))) {
                AppSettings.clearLastGameId(context);
            }
            if (!deleted) {
                Log.w(TAG, "Failed to delete game " + id);
            }
            return deleted;
        }
    }

    private static String readText(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, n);
            }
            return sb.toString();
        }
    }

    private File fileFor(String id) {
        return new File(dir, id + ".json");
    }
}
