package org.androshogi.ui.games;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.androshogi.R;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.settings.AppSettings;
import org.androshogi.shogi.Shogi;
import org.androshogi.storage.GameStore;
import org.androshogi.storage.SavedGame;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class GameListActivityTest {
    private Context context;
    private GameStore store;
    private String first;
    private String second;
    private String previousLast;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        store = new GameStore(context);
        previousLast = AppSettings.lastGameId(context);
        first = GameStore.newId() + "-bulk-a";
        second = GameStore.newId() + "-bulk-b";
        save(first, "一括削除A");
        save(second, "一括削除B");
    }

    private void save(String id, String black) {
        GameRecord record = new GameRecord(Shogi.STARTING_SFEN, black, "後手");
        assertTrue(store.save(new SavedGame(id, System.currentTimeMillis(),
                System.currentTimeMillis() + 10000, record, new GameAnalysis(record))));
    }

    @After public void tearDown() {
        File directory = new File(context.getFilesDir(), "games/" + second + ".json");
        if (directory.isDirectory()) {
            assertTrue(new File(directory, "keep").delete());
            assertTrue(directory.delete());
        }
        store.delete(first);
        store.delete(second);
        if (previousLast == null) AppSettings.clearLastGameId(context);
        else AppSettings.setLastGameId(context, previousLast);
    }

    private Intent intent() {
        return new Intent(context, GameListActivity.class)
                .putExtra(GameListActivity.EXTRA_CURRENT_ID, first);
    }

    /** Waits for real asynchronous disk work without blocking the UI thread. */
    private void awaitReady(ActivityScenario<GameListActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> ready.set(!new ViewModelProvider(activity)
                    .get(GameListViewModel.class).state().getValue().busy));
            if (ready.get()) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Saved game operation did not finish");
    }

    private void menu(ActivityScenario<GameListActivity> scenario, int id) {
        scenario.onActivity(activity -> {
            Toolbar toolbar = activity.findViewById(R.id.toolbar);
            assertTrue(activity.onOptionsItemSelected(toolbar.getMenu().findItem(id)));
        });
    }

    private void selectBoth(ActivityScenario<GameListActivity> scenario) {
        menu(scenario, R.id.game_select);
        onView(withContentDescription("一括削除A 対 後手 の棋譜を選択")).perform(click());
        // A row tap also toggles selection, without opening the game.
        onView(withText("一括削除B 対 後手")).perform(click());
        onView(withText("2件選択中")).check(matches(withText("2件選択中")));
    }

    @Test public void selectAllClearRecreateAndCancelPreserveFiles() {
        try (ActivityScenario<GameListActivity> scenario = ActivityScenario.launch(intent())) {
            awaitReady(scenario);
            menu(scenario, R.id.game_select);
            menu(scenario, R.id.game_select_all);
            onView(withContentDescription("一括削除A 対 後手 の棋譜を選択")).check(matches(isChecked()));
            menu(scenario, R.id.game_select_all);
            onView(withText("0件選択中")).check(matches(withText("0件選択中")));
            scenario.onActivity(activity -> activity.onSupportNavigateUp());
            selectBoth(scenario);
            scenario.recreate();
            awaitReady(scenario);
            onView(withContentDescription("一括削除A 対 後手 の棋譜を選択")).check(matches(isChecked()));
            onView(withContentDescription("一括削除B 対 後手 の棋譜を選択")).check(matches(isChecked()));
            menu(scenario, R.id.game_delete_selected);
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
            assertNotNull(store.load(first));
            assertNotNull(store.load(second));
        }
    }

    @Test public void batchDeleteReturnsCurrentDeletionAfterRecreation() {
        try (ActivityScenario<GameListActivity> scenario = ActivityScenario.launchActivityForResult(intent())) {
            awaitReady(scenario);
            selectBoth(scenario);
            menu(scenario, R.id.game_delete_selected);
            onView(withText(R.string.game_list_delete_action)).inRoot(isDialog()).perform(click());
            scenario.recreate();
            awaitReady(scenario);
            assertNull(store.load(first));
            assertNull(store.load(second));
            scenario.recreate();
            awaitReady(scenario);
            scenario.onActivity(activity -> activity.onSupportNavigateUp());
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            assertTrue(scenario.getResult().getResultData()
                    .getBooleanExtra(GameListActivity.EXTRA_CURRENT_DELETED, false));
            assertNull(scenario.getResult().getResultData().getStringExtra(GameListActivity.EXTRA_OPEN_ID));
        }
    }

    @Test public void failedDeletionRemainsVisibleAndSelected() throws Exception {
        try (ActivityScenario<GameListActivity> scenario = ActivityScenario.launch(intent())) {
            awaitReady(scenario);
            selectBoth(scenario);
            // A nonempty directory cannot be deleted as a file: exercise a real partial failure.
            File failed = new File(context.getFilesDir(), "games/" + second + ".json");
            assertTrue(failed.delete());
            assertTrue(failed.mkdir());
            try (FileOutputStream out = new FileOutputStream(new File(failed, "keep"))) {
                out.write(1);
            }
            menu(scenario, R.id.game_delete_selected);
            onView(withText(R.string.game_list_delete_action)).inRoot(isDialog()).perform(click());
            awaitReady(scenario);
            assertNull(store.load(first));
            assertTrue(failed.isDirectory());
            onView(withText("1件選択中")).check(matches(withText("1件選択中")));
            onView(withContentDescription("一括削除B 対 後手 の棋譜を選択")).check(matches(isChecked()));
            scenario.recreate();
            awaitReady(scenario);
            onView(withContentDescription("一括削除B 対 後手 の棋譜を選択")).check(matches(isChecked()));
        }
    }
}
