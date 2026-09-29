package org.androshogi.engine;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A long lived USI engine process.
 *
 * <p>The engine is started once ({@code usi} / {@code setoption} / {@code isready} /
 * {@code usinewgame}) and then kept alive, so that repeated searches — in
 * particular the game analysis loop — do not pay the cost of reloading the
 * evaluation function for every position.
 *
 * <p>Two worker threads are used: a writer thread that drains the command queue
 * (and performs the start-up handshake before it does so), and a reader thread
 * that parses the engine output. Every callback is delivered on the main thread,
 * so views may be updated from them directly.
 *
 * <p>Threading contract: {@link #start()}, {@link #startSearch}, {@link #stopSearch()}
 * and {@link #shutdown()} are meant to be called from the main thread and never block.
 */
public class EngineSession {
    private static final String TAG = "EngineSession";

    /** Search time used by the "検討" button. */
    public static final int DEFAULT_TIME_MS = 10000;
    /** Number of candidate moves requested from the engine. */
    public static final int DEFAULT_MULTI_PV = 5;

    private static final long USIOK_TIMEOUT_MS = 10000;
    /** Loading a large NNUE evaluation function can take a while on a phone. */
    private static final long READYOK_TIMEOUT_MS = 60000;
    private static final long QUIT_TIMEOUT_MS = 1000;
    /** Minimum interval between info callbacks while searching. */
    private static final long NOTIFY_INTERVAL_MS = 100;

    private static final String QUIT_COMMAND = "quit";

    public enum State {
        /** Created but not started yet. */
        NEW,
        /** The process is running but the handshake has not finished. */
        STARTING,
        /** Ready to accept a search. */
        READY,
        /** A search is running. */
        SEARCHING,
        /** A search is running and {@code stop} has already been sent. */
        STOPPING,
        /** The process has been shut down, or failed to start. */
        CLOSED,
    }

    /** Result of a single search. */
    public static final class SearchResult {
        /** Position the search was started from. */
        public final String sfen;
        /** Best move in USI notation, or null if the engine reported none (resign/win). */
        @Nullable
        public final String bestMoveUSI;
        /** Best move as a move code, or {@link Shogi#MOVE_NONE}. */
        public final int bestMoveCode;
        @Nullable
        public final String ponderUSI;
        /** Last info line of each multi-PV index, ordered by index. */
        public final List<EngineInfo> infos;
        /** True if the search was cut short by {@link #stopSearch()}. */
        public final boolean stopped;

        SearchResult(String sfen, @Nullable String bestMoveUSI, int bestMoveCode,
                     @Nullable String ponderUSI, List<EngineInfo> infos, boolean stopped) {
            this.sfen = sfen;
            this.bestMoveUSI = bestMoveUSI;
            this.bestMoveCode = bestMoveCode;
            this.ponderUSI = ponderUSI;
            this.infos = Collections.unmodifiableList(infos);
            this.stopped = stopped;
        }

        /** True when the engine finished on its own rather than via {@link EngineSession#stopSearch()}. */
        public boolean completedNormally() {
            return !stopped;
        }

        /** Info line of multi-PV index 1, or null if the engine reported none. */
        @Nullable
        public EngineInfo bestInfo() {
            for (EngineInfo info : infos) {
                if (info.multipv() == 1) {
                    return info;
                }
            }
            return null;
        }
    }

    /** Notified once when the engine becomes usable, or when it fails. */
    public interface StateListener {
        void onEngineReady(String engineName);
        void onEngineFailed(String message);
    }

    /** Notified when a single search finishes. */
    public interface SearchCallback {
        void onSearchFinished(SearchResult result);
    }

    private final File execFile;
    private final File workingDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<EngineUpdateListener> listeners = new ArrayList<>();
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    /** Option values sent during the handshake. Guards the two maps below as well. */
    private final Map<String, String> options = new LinkedHashMap<>();
    /** Option values the engine currently holds, as far as we have told it. */
    private final Map<String, String> appliedOptions = new LinkedHashMap<>();
    /** Options changed after the handshake; sent, with isready, before the next search. */
    private final Map<String, String> pendingOptions = new LinkedHashMap<>();
    /** Lower-cased option name to the spelling the engine advertised, filled by the reader. */
    private final Map<String, String> knownOptions = new ConcurrentHashMap<>();
    /** Guards against reporting the same breakdown from both worker threads. */
    private final AtomicBoolean failureReported = new AtomicBoolean(false);

    private final CountDownLatch usiOkLatch = new CountDownLatch(1);
    private final CountDownLatch readyOkLatch = new CountDownLatch(1);

    @Nullable
    private StateListener stateListener;

    private Process process;
    private BufferedWriter writer;
    private Thread readerThread;
    private Thread writerThread;
    private String engineName = "";

    private volatile State state = State.NEW;
    /** Monotonic start time, read by the engine output thread after it starts. */
    private long startNanos;

    // --- Search state. ---
    /** Latest info line per multi-PV index; guarded by itself. */
    private final TreeMap<Integer, EngineInfo> latestInfos = new TreeMap<>();
    private volatile Board searchBoard;
    private volatile String searchSFEN;
    private volatile SearchCallback searchCallback;
    private volatile boolean stopRequested;
    private volatile long lastNotifiedAt;

    public EngineSession(String name, String workingDir) {
        this.workingDir = new File(workingDir);
        this.execFile = new File(this.workingDir, "lib" + name + ".so");
        synchronized (options) {
            options.put("MultiPV", String.valueOf(DEFAULT_MULTI_PV));
        }
    }

    public void setStateListener(@Nullable StateListener listener) {
        this.stateListener = listener;
    }

    public void addEngineUpdateListener(EngineUpdateListener listener) {
        this.listeners.add(listener);
    }

    public State getState() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isSearching() {
        return state == State.SEARCHING || state == State.STOPPING;
    }

    /**
     * Records an option value for the engine.
     *
     * <p>Before the handshake the value goes out with the other options. After
     * it, the change is held until the next search starts and is then sent
     * together with {@code isready}: the engine only accepts setoption while
     * idle, and YaneuraOu applies Threads and USI_Hash when it handles
     * isready. Values equal to what the engine already has are dropped, so
     * callers may re-apply the whole settings screen freely.
     */
    public void setOption(String name, String value) {
        synchronized (options) {
            State current = state;
            if (current == State.NEW || current == State.STARTING) {
                options.put(name, value);
                // The handshake may already have taken its snapshot; the
                // pending map catches that case and is pruned afterwards.
                pendingOptions.put(name, value);
                return;
            }
            if (value.equals(appliedOptions.get(name))) {
                pendingOptions.remove(name);
            } else {
                pendingOptions.put(name, value);
            }
        }
    }

    /**
     * Starts the engine process and performs the handshake in the background.
     * The {@link StateListener} is notified when the engine is ready or has failed.
     */
    public synchronized void start() {
        if (state != State.NEW) {
            Log.w(TAG, "start() ignored in state " + state);
            return;
        }
        state = State.STARTING;
        startNanos = System.nanoTime();
        // ProcessBuilder.start() may touch the filesystem and linker. Keep it off the UI thread.
        writerThread = new Thread(this::launchAndWriterLoop, "usi-writer");
        writerThread.start();
    }

    /** Launches the process, publishes it atomically, then performs the normal writer loop. */
    private void launchAndWriterLoop() {
        if (!execFile.exists()) {
            fail("エンジンが見つかりません: " + execFile.getName());
            return;
        }

        final Process launched;
        final BufferedWriter launchedWriter;
        try {
            ProcessBuilder pb = new ProcessBuilder(execFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.directory(workingDir);
            launched = pb.start();
            launchedWriter = new BufferedWriter(new OutputStreamWriter(launched.getOutputStream()));
        } catch (IOException e) {
            Log.e(TAG, "Failed to launch the engine", e);
            fail("エンジンを起動できませんでした: " + e.getMessage());
            return;
        }

        synchronized (this) {
            // shutdown() may have happened while ProcessBuilder.start() was blocked.
            if (state != State.STARTING) {
                launched.destroy();
                return;
            }
            process = launched;
            writer = launchedWriter;
            readerThread = new Thread(this::readerLoop, "usi-reader");
            readerThread.start();
        }

        writerLoop();
    }

    /**
     * Starts a search for the given position. Info updates are delivered to the
     * registered {@link EngineUpdateListener}s and the final result to
     * {@code callback}, both on the main thread.
     *
     * @param sfen       position to analyze
     * @param maxTimeMs  search time in milliseconds
     * @return false if the engine is not ready or is already searching
     */
    public synchronized boolean startSearch(String sfen, int maxTimeMs,
                                            @Nullable SearchCallback callback) {
        if (state != State.READY) {
            Log.w(TAG, "startSearch() ignored in state " + state);
            return false;
        }

        searchSFEN = sfen;
        searchBoard = new Board(sfen);
        searchCallback = callback;
        stopRequested = false;
        synchronized (latestInfos) {
            latestInfos.clear();
        }
        lastNotifiedAt = 0;
        state = State.SEARCHING;

        synchronized (options) {
            if (!pendingOptions.isEmpty()) {
                for (Map.Entry<String, String> option : pendingOptions.entrySet()) {
                    enqueue(setOptionCommand(option.getKey(), option.getValue()));
                    appliedOptions.put(option.getKey(), option.getValue());
                }
                pendingOptions.clear();
                // Threads and USI_Hash take effect when the engine handles isready.
                // Commands are answered in order, so the following position/go
                // are not read before it has finished.
                enqueue("isready");
            }
        }

        // Tell the views to drop the previous search's arrows and text; a
        // position with no legal moves never produces an info line of its own.
        notifyListeners(searchBoard, Collections.<EngineInfo>emptyList());

        enqueue("position sfen " + sfen);
        // "go rtime" adds a random component and is meant for playing, not for
        // analysis; a fixed movetime gives reproducible results per position.
        enqueue("go movetime " + maxTimeMs);
        return true;
    }

    /**
     * Asks the engine to stop the current search. The search still completes
     * with a {@code bestmove}, so the callback is invoked as usual with
     * {@link SearchResult#stopped} set.
     */
    public synchronized void stopSearch() {
        if (state != State.SEARCHING) {
            return;
        }
        state = State.STOPPING;
        stopRequested = true;
        enqueue("stop");
    }

    /**
     * Sends {@code quit} and tears the process down. Safe to call more than once;
     * the session cannot be restarted afterwards.
     */
    public synchronized void shutdown() {
        if (state == State.CLOSED || state == State.NEW) {
            state = State.CLOSED;
            return;
        }
        boolean duringHandshake = state == State.STARTING;
        state = State.CLOSED;
        // Drop any pending search commands so that "quit" is sent right away.
        commandQueue.clear();
        commandQueue.offer(QUIT_COMMAND);
        if (duringHandshake && writerThread != null) {
            // Nothing drains the queue until the handshake is over, and that can
            // take a minute while the evaluation function loads. Cut it short
            // instead of waiting for the timeout to report a bogus failure.
            writerThread.interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Writer thread: handshake, then drain the command queue.
    // ------------------------------------------------------------------

    private void writerLoop() {
        try {
            send("usi");
            if (!await(usiOkLatch, USIOK_TIMEOUT_MS)) {
                fail("エンジンが応答しません (usiok)");
                return;
            }

            Map<String, String> snapshot;
            synchronized (options) {
                snapshot = new LinkedHashMap<>(options);
            }
            for (Map.Entry<String, String> option : snapshot.entrySet()) {
                send(setOptionCommand(option.getKey(), option.getValue()));
            }
            send("isready");
            if (!await(readyOkLatch, READYOK_TIMEOUT_MS)) {
                fail("エンジンの準備が完了しません (readyok)");
                return;
            }
            send("usinewgame");
            synchronized (options) {
                appliedOptions.putAll(snapshot);
                // Anything set during the handshake that the snapshot already
                // carried needs no second trip.
                pendingOptions.entrySet().removeIf(
                        e -> e.getValue().equals(appliedOptions.get(e.getKey())));
            }

            synchronized (this) {
                if (state == State.STARTING) {
                    state = State.READY;
                }
            }
            final String name = engineName;
            mainHandler.post(() -> {
                StateListener listener = stateListener;
                if (listener != null) {
                    listener.onEngineReady(name);
                }
            });

            while (true) {
                String command = commandQueue.take();
                send(command);
                if (command.equals(QUIT_COMMAND)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to the engine", e);
            fail("エンジンとの通信が切断されました");
        } finally {
            closeProcess();
        }
    }

    private void enqueue(String command) {
        commandQueue.offer(command);
    }

    private void send(String command) throws IOException {
        Log.d(TAG, "> " + command);
        writer.write(command);
        writer.write("\n");
        writer.flush();
    }

    private static boolean await(CountDownLatch latch, long timeoutMs) throws InterruptedException {
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private String setOptionCommand(String name, String value) {
        String actual = knownOptions.get(name.toLowerCase(Locale.ROOT));
        return "setoption name " + (actual != null ? actual : name) + " value " + value;
    }

    // ------------------------------------------------------------------
    // Reader thread: parse the engine output.
    // ------------------------------------------------------------------

    private void readerLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line.trim());
            }
        } catch (IOException e) {
            if (state != State.CLOSED) {
                Log.e(TAG, "Failed to read from the engine", e);
            }
        } finally {
            // The engine is gone. Close the session before finishing the search
            // so that no caller sees READY and starts a search nothing can read.
            if (state != State.CLOSED) {
                fail("エンジンが予期せず終了しました");
            }
            // Unblock the handshake if the engine died before answering.
            usiOkLatch.countDown();
            readyOkLatch.countDown();
            finishSearchIfRunning(null, null);
        }
    }

    private void handleLine(String line) {
        if (line.isEmpty()) {
            return;
        }

        if (line.equals("usiok")) {
            usiOkLatch.countDown();
            return;
        }
        if (line.equals("readyok")) {
            if (state == State.STARTING) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                Log.i(TAG, "Initial readyok after " + elapsedMs + " ms");
            }
            readyOkLatch.countDown();
            return;
        }
        if (line.startsWith("id name ")) {
            engineName = line.substring("id name ".length()).trim();
            Log.i(TAG, "< " + line);
            return;
        }
        if (line.startsWith("option name ")) {
            // Logged so the advertised options and defaults (FV_SCALE, USI_Hash, ...)
            // of the bundled binary can be read off a device.
            Log.i(TAG, "< " + line);
            rememberOption(line);
            return;
        }
        if (line.startsWith("bestmove")) {
            handleBestMove(line);
            return;
        }
        if (line.startsWith("info")) {
            handleInfo(line);
        }
    }

    /** Records the exact spelling of an option so that setoption matches it. */
    private void rememberOption(String line) {
        String rest = line.substring("option name ".length());
        int typeIndex = rest.indexOf(" type ");
        String name = (typeIndex >= 0 ? rest.substring(0, typeIndex) : rest).trim();
        if (!name.isEmpty()) {
            knownOptions.put(name.toLowerCase(Locale.ROOT), name);
        }
    }

    private void handleInfo(String line) {
        EngineInfo info = EngineInfo.parse(line);
        if (info == null || !info.hasPv()) {
            // Lines without a principal variation (currmove, engine chatter)
            // carry nothing the views can display.
            return;
        }

        int index = info.multipv();
        if (index < 1) {
            return;
        }
        synchronized (latestInfos) {
            latestInfos.put(index, info);
        }

        long now = System.currentTimeMillis();
        if (now - lastNotifiedAt >= NOTIFY_INTERVAL_MS) {
            lastNotifiedAt = now;
            notifyListeners();
        }
    }

    private void handleBestMove(String line) {
        String[] tokens = line.split("\\s+");
        String best = tokens.length > 1 ? tokens[1] : null;
        String ponder = null;
        for (int i = 2; i + 1 < tokens.length; i++) {
            if (tokens[i].equals("ponder")) {
                ponder = tokens[i + 1];
                break;
            }
        }
        if (best != null && (best.equals("resign") || best.equals("win") || best.equals("(none)"))) {
            best = null;
        }
        finishSearchIfRunning(best, ponder);
    }

    private void finishSearchIfRunning(@Nullable String bestMoveUSI, @Nullable String ponderUSI) {
        final Board board;
        final String sfen;
        final SearchCallback callback;
        final boolean stopped;
        final List<EngineInfo> infos;
        synchronized (this) {
            board = searchBoard;
            if (board == null) {
                return;
            }
            sfen = searchSFEN;
            callback = searchCallback;
            stopped = stopRequested;
            // Take the snapshot before releasing the session: once the state is
            // READY the main thread may start a search and clear `latestInfos`.
            infos = snapshotInfos();
            searchBoard = null;
            searchCallback = null;
            if (state == State.SEARCHING || state == State.STOPPING) {
                state = State.READY;
            }
        }

        int bestMoveCode = Shogi.MOVE_NONE;
        if (bestMoveUSI != null) {
            bestMoveCode = Move.fromUSI(board, bestMoveUSI);
        }
        final SearchResult result = new SearchResult(sfen, bestMoveUSI, bestMoveCode,
                ponderUSI, infos, stopped);

        // Post the final update, the callback and the release of the native
        // board in this order: the main thread runs them in the order they are
        // posted, so no listener can touch the board after it is freed.
        notifyListeners(board, infos);
        mainHandler.post(() -> {
            try {
                if (callback != null) {
                    callback.onSearchFinished(result);
                }
            } finally {
                // A throwing callback must not leak the native position.
                board.cleanup();
            }
        });
    }

    /** Latest info of each multi-PV index, ordered by index. */
    private List<EngineInfo> snapshotInfos() {
        synchronized (latestInfos) {
            return new ArrayList<>(latestInfos.values());
        }
    }

    private void notifyListeners() {
        Board board = searchBoard;
        if (board != null) {
            notifyListeners(board);
        }
    }

    private void notifyListeners(final Board board) {
        notifyListeners(board, snapshotInfos());
    }

    private void notifyListeners(final Board board, final List<EngineInfo> infos) {
        if (listeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (EngineUpdateListener listener : listeners) {
                listener.onEngineUpdated(board, infos);
            }
        });
    }

    // ------------------------------------------------------------------
    // Teardown
    // ------------------------------------------------------------------

    private void fail(String message) {
        state = State.CLOSED;
        Log.e(TAG, message);
        if (!failureReported.compareAndSet(false, true)) {
            // Both workers notice the same breakdown; report it once.
            closeProcess();
            return;
        }
        mainHandler.post(() -> {
            StateListener listener = stateListener;
            if (listener != null) {
                listener.onEngineFailed(message);
            }
        });
        closeProcess();
    }

    private void closeProcess() {
        Process p = process;
        if (p == null) {
            return;
        }
        try {
            if (!p.waitFor(QUIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                p.destroy();
            }
        } catch (InterruptedException e) {
            p.destroy();
            Thread.currentThread().interrupt();
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "EngineSession{" + execFile.getName() + ", " + state + "}";
    }
}
