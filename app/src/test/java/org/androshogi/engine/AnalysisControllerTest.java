package org.androshogi.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.androshogi.game.GameRecord;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalysisControllerTest {
    private final GameRecord record = new GameRecord("position-0", Arrays.asList(1, 2),
            null, null, "Black", "White");
    private final FakeSearcher engine = new FakeSearcher();
    private final RecordingListener listener = new RecordingListener();

    private AnalysisController controller() {
        record.seekStart();
        return new AnalysisController(record, 100, engine, new AnalysisController.Position() {
            @Override public String sfen() { return "position-" + record.currentPly(); }
            @Override public void forward() { record.forward(); }
        }, listener);
    }

    @Test public void analyzesEveryPositionIncludingTheLast() {
        AnalysisController run = controller();
        run.start();
        assertEquals("position-0", engine.sfen);
        engine.complete(true);
        assertEquals("position-1", engine.sfen);
        engine.complete(true);
        assertEquals("position-2", engine.sfen);
        engine.complete(true);
        assertEquals(Arrays.asList(0, 1, 2), listener.saved);
        assertEquals(Arrays.asList("0/3", "1/3", "2/3", "3/3"), listener.progress);
        assertEquals(AnalysisController.Status.COMPLETED, listener.status);
        assertEquals(2, record.currentPly());
    }

    @Test public void cancellationDiscardsTheIncompletePositionAndLateCallbacks() {
        AnalysisController run = controller();
        run.start();
        engine.complete(true);
        run.cancel();
        assertTrue(engine.stopped);
        engine.complete(false);
        assertEquals(Collections.singletonList(0), listener.saved);
        assertEquals(AnalysisController.Status.CANCELLED, listener.status);
        engine.complete(true);
        assertEquals(Collections.singletonList(0), listener.saved);
    }

    @Test public void failedStartTerminatesWithoutStoring() {
        engine.startSucceeds = false;
        AnalysisController run = controller();
        run.start();
        assertEquals(AnalysisController.Status.ERROR, listener.status);
        assertTrue(listener.saved.isEmpty());
    }

    @Test public void engineFailureCannotStoreAQueuedResult() {
        AnalysisController run = controller();
        run.start();
        engine.available = false;
        engine.complete(true);
        assertEquals(AnalysisController.Status.ERROR, listener.status);
        assertTrue(listener.saved.isEmpty());
    }

    @Test public void abandonIgnoresTheOutstandingCallback() {
        AnalysisController run = controller();
        run.start();
        run.abandon();
        engine.complete(true);
        assertNull(listener.status);
        assertTrue(listener.saved.isEmpty());
    }

    private static final class FakeSearcher implements AnalysisController.Searcher {
        String sfen;
        EngineSession.SearchCallback callback;
        boolean searching;
        boolean stopped;
        boolean startSucceeds = true;
        boolean available = true;

        @Override public boolean start(String sfen, int timeMs, EngineSession.SearchCallback callback) {
            if (!startSucceeds) return false;
            this.sfen = sfen;
            this.callback = callback;
            searching = true;
            return true;
        }
        @Override public boolean isSearching() { return searching; }
        @Override public boolean isAvailable() { return available; }
        @Override public void stop() { stopped = true; }

        void complete(boolean normal) {
            searching = false;
            EngineSession.SearchCallback done = callback;
            done.onSearchFinished(new EngineSession.SearchResult(sfen, null, 0, null,
                    Collections.emptyList(), !normal));
        }
    }

    private static final class RecordingListener implements AnalysisController.Listener {
        final List<Integer> saved = new ArrayList<>();
        final List<String> progress = new ArrayList<>();
        AnalysisController.Status status;
        @Override public void onProgress(int analyzed, int total) {
            progress.add(analyzed + "/" + total);
        }
        @Override public void onResult(int ply, EngineSession.SearchResult result) { saved.add(ply); }
        @Override public void onPositionAdvanced() { }
        @Override public void onFinished(AnalysisController.Status status) { this.status = status; }
    }
}
