package org.androshogi.storage;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.settings.AppSettings;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class GameStoreCollectionsTest {
    private Context context;
    private GameStore store;
    private String id;
    private String otherId;
    private String previousLast;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        store = new GameStore(context);
        previousLast = AppSettings.lastGameId(context);
        id = GameStore.newId() + "-collection";
        otherId = id + "-other";
    }

    @After public void tearDown() {
        store.delete(id);
        store.delete(otherId);
        if (previousLast == null) AppSettings.clearLastGameId(context);
        else AppSettings.setLastGameId(context, previousLast);
    }

    private SavedGame game(String gameId) {
        GameRecord record = new GameRecord("4k4/9/9/9/9/9/9/9/4K4 w B2P 1", "B", "W");
        record.playVariation(123);
        record.seek(0);
        record.playVariation(456);
        record.setComment(record.currentNodeId(), "分岐のコメント");
        return new SavedGame(gameId, 100, 200, record, new GameAnalysis(record));
    }

    private JSONObject document() throws Exception {
        return new JSONObject(new String(Files.readAllBytes(
                new File(context.getFilesDir(), "games/" + id + ".json").toPath()), StandardCharsets.UTF_8));
    }

    @Test public void newRecordsUseHistoryAndStaleAutosavesKeepTheOnDiskCategory() throws Exception {
        SavedGame detached = game(id);
        assertTrue(store.save(detached));
        assertFalse(document().getBoolean("saved"));
        assertTrue(new GameStore(context).setSaved(id, true));
        assertTrue(store.save(detached));
        assertTrue(document().getBoolean("saved"));
        assertTrue(new GameStore(context).setSaved(id, false));
        assertTrue(store.save(detached));
        assertFalse(document().getBoolean("saved"));
        assertEquals(detached.record.currentNodeId(), store.loadLast().record.currentNodeId());
        assertEquals(2, store.loadLast().record.node(GameRecord.ROOT_ID).childIds().size());
    }

    @Test public void movingPreservesEveryFieldAndDoesNotChangeTheGameToResume() throws Exception {
        assertTrue(store.save(game(id)));
        JSONObject before = document();
        assertTrue(store.save(game(otherId)));
        assertEquals(otherId, AppSettings.lastGameId(context));
        assertTrue(store.setSaved(id, true));
        JSONObject after = document();
        assertTrue(after.getBoolean("saved"));
        before.remove("saved");
        after.remove("saved");
        assertEquals(before.toString(), after.toString());
        assertEquals(otherId, store.loadLast().id);
        assertFalse(store.setSaved(id + "-missing", true));
        assertFalse(store.setSaved("../outside", true));
    }

    @Test public void legacyFilesStaySavedWhenTheyAreOpenedAndAutosaved() throws Exception {
        assertTrue(store.save(game(id)));
        JSONObject legacy = document();
        legacy.remove("saved");
        Files.write(new File(context.getFilesDir(), "games/" + id + ".json").toPath(),
                legacy.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(store.list().stream().filter(g -> g.id.equals(id)).findFirst().get().saved);
        assertTrue(store.save(store.load(id)));
        assertTrue(document().getBoolean("saved"));
        assertTrue(org.androshogi.game.PositionAnalysis.isWhiteToMove(store.load(id).record.startSfen()));
    }
}
