package org.androshogi.engine;

import org.androshogi.game.GameRecord;

/** Runs a sequence of searches on the UI thread without depending on Android views. */
public final class AnalysisController {
    public enum Status { COMPLETED, CANCELLED, ERROR }

    public interface Searcher {
        boolean start(String sfen, int timeMs, EngineSession.SearchCallback callback);
        boolean isSearching();
        boolean isAvailable();
        void stop();
    }

    public interface Position {
        String sfen();
        void forward();
    }

    public interface Listener {
        void onProgress(int analyzed, int total);
        void onResult(int ply, EngineSession.SearchResult result);
        void onPositionAdvanced();
        void onFinished(Status status);
    }

    private final GameRecord record;
    private final Searcher searcher;
    private final Position position;
    private final Listener listener;
    private final int timeMs;
    private final int total;
    private int analyzed;
    private int searchPly;
    private String searchSfen;
    private boolean cancelled;
    private boolean closed;
    private boolean started;

    public AnalysisController(GameRecord record, int timeMs, Searcher searcher,
                              Position position, Listener listener) {
        this.record = record;
        this.timeMs = timeMs;
        this.searcher = searcher;
        this.position = position;
        this.listener = listener;
        this.total = record.remaining() + 1;
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("Analysis already started");
        }
        started = true;
        listener.onProgress(0, total);
        searchNext();
    }

    public void cancel() {
        if (!started || closed || cancelled) {
            return;
        }
        cancelled = true;
        if (searcher.isSearching()) {
            searcher.stop();
        } else {
            finish(Status.CANCELLED);
        }
    }

    /** Silently discards any late engine callback when the Activity is destroyed. */
    public void abandon() {
        closed = true;
    }

    /** Called when the engine process fails, independently of its search callback. */
    public void engineFailed() {
        if (started && !closed) {
            finish(cancelled ? Status.CANCELLED : Status.ERROR);
        }
    }

    private void searchNext() {
        searchPly = record.currentPly();
        searchSfen = position.sfen();
        if (!searcher.start(searchSfen, timeMs, this::onSearchFinished)) {
            finish(cancelled ? Status.CANCELLED : Status.ERROR);
        }
    }

    private void onSearchFinished(EngineSession.SearchResult result) {
        if (closed) {
            return;
        }
        if (cancelled) {
            finish(Status.CANCELLED);
            return;
        }
        if (!searcher.isAvailable() || result == null || !result.completedNormally()
                || record.currentPly() != searchPly
                || !searchSfen.equals(result.sfen)
                || !searchSfen.equals(position.sfen())) {
            finish(Status.ERROR);
            return;
        }

        // Only a completed search for the same position can change stored analysis.
        listener.onResult(searchPly, result);
        analyzed++;
        listener.onProgress(analyzed, total);
        if (record.isAtEnd()) {
            finish(Status.COMPLETED);
            return;
        }

        position.forward();
        if (record.currentPly() != searchPly + 1) {
            finish(Status.ERROR);
            return;
        }
        listener.onPositionAdvanced();
        searchNext();
    }

    private void finish(Status status) {
        if (closed) {
            return;
        }
        closed = true;
        listener.onFinished(status);
    }
}
