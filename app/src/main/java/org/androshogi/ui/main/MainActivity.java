package org.androshogi.ui.main;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;
import org.androshogi.R;
import org.androshogi.ui.games.GameListActivity;

import org.androshogi.engine.EngineInfo;
import org.androshogi.engine.AnalysisController;
import org.androshogi.engine.EngineSession;
import org.androshogi.engine.EvaluationFileStore;
import org.androshogi.engine.EngineUpdateListener;

import org.androshogi.kifu.KifParser;
import org.androshogi.kifu.KifTextCodec;
import org.androshogi.kifu.KifWriter;

import org.androshogi.settings.AppSettings;
import org.androshogi.ui.settings.SettingsActivity;

import org.androshogi.storage.GameStore;
import org.androshogi.storage.SavedGame;

import org.androshogi.game.GameRecord;
import org.androshogi.game.GameSession;
import org.androshogi.game.PositionAnalysis;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String AI_NAME = "YaneuraOu_NNUE";

    private BoardView boardView;
    private TextView blackInfoView;
    private TextView whiteInfoView;
    private TextView blackScoreView;
    private TextView whiteScoreView;
    /** Evaluation currently shown in the player rows; null when there is none. */
    @Nullable
    private PositionAnalysis shownEvaluation;
    private EngineView engineView;
    private RecyclerView moveListView;
    private MoveListAdapter moveListAdapter;
    private WinRateGraphView graphView;
    private MaterialButtonToggleGroup infoTabs;
    private MaterialButton hintButton;
    private ClipboardManager clipboard;
    private EngineSession engine;
    /** Revision of the evaluation data loaded by this process. */
    private long engineEvaluationRevision;

    /** Non-null while a game analysis is running. */
    @Nullable
    private AnalysisController analysisRun;

    /** The game shown on the board and its persistent identity. */
    private GameSession game;
    private GameStore gameStore;
    /** Serializes disk access so saves cannot overtake one another. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Invalidates a slow load when the user has already chosen or created another game. */
    private long gameLoadGeneration;
    /** Prevents the temporary empty startup board from being saved before loadLast() finishes. */
    private boolean initialGameLoadPending;

    @Nullable
    private String pendingKifExport;
    private int pendingKifFormat;

    /** Brings back the choice made on the saved-game list. */
    private final ActivityResultLauncher<Intent> gameListLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) {
                    return;
                }
                boolean currentDeleted =
                        data.getBooleanExtra(GameListActivity.EXTRA_CURRENT_DELETED, false);
                String openId = data.getStringExtra(GameListActivity.EXTRA_OPEN_ID);
                if (openId != null && !openId.equals(game.id())) {
                    // If another game was chosen, open it directly even when the current one was deleted.
                    openSavedGame(openId);
                } else if (currentDeleted) {
                    // The game on the board is gone from disk; replace it with a fresh id so it cannot reappear.
                    startFreshGame(true);
                }
            });

    /** Picks a KIF document from the device without requiring storage permission. */
    private final ActivityResultLauncher<String[]> openKifLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    loadKifFromUri(uri, R.string.kifu_file_read_failed);
                }
            });

    private final ActivityResultLauncher<Uri> kifFolderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) {
                    pendingKifExport = null;
                    return;
                }
                if (!AppSettings.setKifSaveFolder(this, uri)) {
                    Toast.makeText(this, R.string.kifu_folder_failed, Toast.LENGTH_LONG).show();
                }
                launchPendingKifExport();
            });

    private final ActivityResultLauncher<String> createKifLauncher = registerForActivityResult(
            new KifCreateDocument("application/vnd.androshogi.kif"),
            uri -> exportKif(uri, KifTextCodec.SHIFT_JIS));

    private final ActivityResultLauncher<String> createKifuLauncher = registerForActivityResult(
            new KifCreateDocument("application/vnd.androshogi.kifu"),
            uri -> exportKif(uri, StandardCharsets.UTF_8));

    // Inline progress panel for the analysis loop
    private View analysisPanel;
    private ProgressBar analysisProgress;
    private TextView analysisLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Check ABI
        String abi = Build.SUPPORTED_ABIS[0];
        Log.d(TAG, String.format("CPU ABI: %s", abi));

        // Components
        boardView = findViewById(R.id.board_view);
        blackInfoView = findViewById(R.id.black_info_view);
        whiteInfoView = findViewById(R.id.white_info_view);
        blackScoreView = findViewById(R.id.black_score_view);
        whiteScoreView = findViewById(R.id.white_score_view);

        // Show an empty board immediately; the previous session is loaded off the UI thread.
        gameStore = new GameStore(this);
        game = new GameSession(newEmptyRecord(), GameStore::newId, System::currentTimeMillis);
        initialGameLoadPending = true;

        // Clipboard
        clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);

        // Set event listeners
        Button menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(this::showPopupMenu);
        Button flipButton = findViewById(R.id.flip_button);
        flipButton.setOnClickListener(v -> flipBoard());

        hintButton = findViewById(R.id.hint_button);
        hintButton.setOnClickListener(this::onHintButtonClicked);

        Button backButton = findViewById(R.id.backward_button);
        backButton.setOnClickListener(v -> {
            if (requireIdleNavigation()) {
                boardView.backwardBoard();
            }
        });
        Button foreButton = findViewById(R.id.forward_button);
        foreButton.setOnClickListener(v -> {
            if (requireIdleNavigation()) {
                boardView.forwardBoard();
            }
        });

        // Analysis progress panel; hidden until a run starts.
        analysisPanel = findViewById(R.id.analysis_panel);
        analysisProgress = findViewById(R.id.analysis_progress);
        analysisLabel = findViewById(R.id.analysis_label);
        findViewById(R.id.analysis_cancel_button).setOnClickListener(v -> {
            if (analysisRun != null) {
                analysisRun.cancel();
            }
        });

        // Whenever the displayed position changes, show what we already know about it.
        boardView.setOnPositionChangedListener(new BoardView.OnPositionChangedListener() {
            @Override
            public void onPositionChanged() {
                MainActivity.this.onPositionChanged();
            }

            @Override
            public void onRecordTruncated(int fromPly) {
                // A user edit wins over any game file that is still being loaded.
                invalidatePendingGameLoad();
                // Those positions left the record, so their results are meaningless now.
                game.truncateAnalysis(fromPly);
                moveListAdapter.recordChanged();
                graphView.invalidate();
            }
        });

        engineView = findViewById(R.id.engine_view);

        // Move list; tapping a row jumps to that position.
        moveListView = findViewById(R.id.move_list_view);
        moveListView.setLayoutManager(new LinearLayoutManager(this));
        moveListAdapter = new MoveListAdapter(game.analysis(), ply -> {
            if (requireIdleNavigation()) {
                boardView.seekTo(ply);
            }
        });
        moveListView.setAdapter(moveListAdapter);

        // Win-rate graph; tapping the plot jumps to the ply under the finger.
        graphView = findViewById(R.id.win_rate_graph);
        graphView.setOnPlySelectedListener(ply -> {
            if (requireIdleNavigation()) {
                boardView.seekTo(ply);
            }
        });

        infoTabs = findViewById(R.id.info_tabs);
        infoTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showInfoTab(checkedId);
            }
        });
        showInfoTab(infoTabs.getCheckedButtonId());

        startEngineSession();

        // Shows the record and fires the listener above, which needs the views bound so far.
        showRecord();

        applyDisplaySettings();

        // Restore the previous game first, then handle a KIF that launched the app.
        // On recreation the intent has already been handled.
        loadInitialGame(savedInstanceState == null ? getIntent() : null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // singleTask: a share while the app is running lands here.
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // MainActivity keeps the engine and any running analysis across rotation,
        // resizing and night-mode changes. Refresh the size/theme-dependent views.
        boardView.requestLayout();
        boardView.invalidate();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshEngineForEvaluation();
        // The settings screen may have changed anything; re-apply it all. Values
        // the engine already holds are dropped by EngineSession, so this is cheap.
        applySettings();
        if (!engine.isSearching()) {
            // Re-render the stored result under the possibly changed score perspective.
            showStoredAnalysis();
        }
    }

    /** Creates one engine process. A closed session is discarded rather than restarted. */
    private void startEngineSession() {
        engineEvaluationRevision = AppSettings.evaluationRevision(this);
        String abi = Build.SUPPORTED_ABIS[0];
        final EngineSession session =
                new EngineSession(AI_NAME + "_" + abi, getApplicationInfo().nativeLibraryDir);
        engine = session;

        session.addEngineUpdateListener((board, infos) -> {
            // A failed session can still have callbacks queued on the main thread.
            if (engine != session) {
                return;
            }
            // Only paint updates for the position on screen. A search for a
            // position the user has already left must not draw its arrows here.
            if (!board.getSFEN().equals(boardView.getSFEN())) {
                return;
            }
            boardView.onEngineUpdated(board, infos);
            engineView.onEngineUpdated(board, infos);
            showEvaluation(PositionAnalysis.from(board.getSFEN(), infos, null));
        });

        session.setStateListener(new EngineSession.StateListener() {
            @Override
            public void onEngineReady(String engineName) {
                if (engine != session) {
                    return;
                }
                Log.i(TAG, "エンジン準備完了: " + engineName);
            }

            @Override
            public void onEngineFailed(String message) {
                if (engine != session) {
                    return;
                }
                boolean analyzing = analysisRun != null;
                if (analysisRun != null) {
                    analysisRun.engineFailed();
                }
                if (!analyzing) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });

        applyEngineSettings(session);
        // A NNUE engine without nn.bin fails during isready. Keep the board usable
        // and start the process only after an evaluation file has been provided.
        if (evaluationFile().isFile()) {
            session.start();
        }
    }

    private File evaluationFile() {
        return EvaluationFileStore.file(this);
    }

    /** The imported file is replaced atomically; a running engine still has the old model. */
    private void refreshEngineForEvaluation() {
        if (engineEvaluationRevision == AppSettings.evaluationRevision(this)) {
            return;
        }
        if (analysisRun != null) {
            analysisRun.engineFailed();
        }
        if (engine.isSearching()) {
            setHintButtonRunning(false);
        }
        engine.shutdown();
        startEngineSession();
    }

    private void showMissingEvaluationDialog() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.engine_eval_missing_title)
                .setMessage(R.string.engine_eval_missing_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Pushes all current settings into the engine and the views. */
    private void applySettings() {
        applyEngineSettings(engine);
        applyDisplaySettings();
    }

    private void applyEngineSettings(EngineSession session) {
        session.setOption("MultiPV", String.valueOf(AppSettings.multiPv(this)));
        session.setOption("Threads", String.valueOf(AppSettings.threads(this)));
        session.setOption("USI_Hash", String.valueOf(AppSettings.hashMb(this)));
        session.setOption("FV_SCALE", String.valueOf(AppSettings.fvScale(this)));
        session.setOption("EvalDir", evaluationFile().getParentFile().getAbsolutePath());
    }

    private void applyDisplaySettings() {
        engineView.setScoreFromBlack(AppSettings.scoreFromBlack(this));

        // The win-rate curve is global to every display; redraw them under the new scale.
        PositionAnalysis.setWinRateScale(AppSettings.winRateScale(this));
        showEvaluation(shownEvaluation);
        moveListAdapter.refreshLabels();
        graphView.invalidate();
    }

    @Override
    protected void onStop() {
        // The activity can be killed without further notice once it is stopped,
        // so this is the last reliable moment to keep what is on screen.
        saveGame();
        super.onStop();
    }

    /** Writes a detached snapshot on the I/O thread. */
    private void saveGame() {
        saveGame(null);
    }

    /**
     * Writes a detached snapshot, then runs {@code onComplete} on the UI thread.
     * The callback is used when a screen must not read the file before this save finishes.
     */
    private void saveGame(@Nullable Runnable onComplete) {
        if (initialGameLoadPending) {
            // loadLast() was queued before this task. For actions such as opening the game
            // list, retry on the UI thread after that queued read has posted its result.
            if (onComplete != null) {
                io.execute(() -> runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        saveGame(onComplete);
                    }
                }));
            }
            return;
        }

        SavedGame snapshot = game.snapshot(System.currentTimeMillis());
        io.execute(() -> {
            gameStore.save(snapshot);
            if (onComplete != null) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        onComplete.run();
                    }
                });
            }
        });
    }

    /** Loads the previous session without blocking first-frame rendering. */
    private void loadInitialGame(@Nullable Intent initialIntent) {
        final long requestGeneration = gameLoadGeneration;
        io.execute(() -> {
            SavedGame saved = gameStore.loadLast();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestGeneration != gameLoadGeneration) {
                    return;
                }
                initialGameLoadPending = false;
                if (saved != null) {
                    applySavedGame(saved);
                }
                if (initialIntent != null) {
                    handleIncomingIntent(initialIntent);
                }
            });
        });
    }

    /** Replaces the in-memory game; all disk access has already happened. */
    private void applySavedGame(SavedGame saved) {
        game.load(saved);
        showRecord();
    }

    /** Makes outstanding asynchronous loads stale and lets the current game be saved. */
    private void invalidatePendingGameLoad() {
        gameLoadGeneration++;
        initialGameLoadPending = false;
    }

    @Override
    protected void onDestroy() {
        // Leaving the engine process behind would keep several hundred MB of
        // evaluation tables resident, so always quit it.
        if (analysisRun != null) {
            analysisRun.abandon();
            analysisRun = null;
        }
        engine.shutdown();
        // Do not cancel the onStop() save that may already be queued.
        io.shutdown();
        super.onDestroy();
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.inflate(R.menu.main_menu);
        MenuCompat.setGroupDividerEnabled(popupMenu.getMenu(), true);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.new_game) {
                newGame();
                return true;
            } else if (itemId == R.id.start_board) {
                if (requireIdleNavigation()) {
                    boardView.setStartBoard();
                }
                return true;
            } else if (itemId == R.id.end_board) {
                if (requireIdleNavigation()) {
                    boardView.setEndBoard();
                }
                return true;
            } else if (itemId == R.id.auto_analyze) {
                //　自動検討
                autoAnalysis(view);
                return true;
            } else if (itemId == R.id.game_list) {
                // 保存済みの棋譜を開く／削除する。最新状態を書き終えてから一覧に渡す。
                if (requireIdleNavigation()) {
                    final String currentId = game.id();
                    saveGame(() -> {
                        if (!currentId.equals(game.id())) {
                            return;
                        }
                        Intent intent = new Intent(this, GameListActivity.class);
                        intent.putExtra(GameListActivity.EXTRA_CURRENT_ID, currentId);
                        gameListLauncher.launch(intent);
                    });
                }
                return true;
            } else if (itemId == R.id.open_kifu) {
                if (requireIdleNavigation()) {
                    // Providers use different MIME types for .kif/.kifu files.
                    openKifLauncher.launch(new String[] {"*/*"});
                }
                return true;
            } else if (itemId == R.id.paste_kifu) {
                // クリップボードから棋譜を貼り付け
                pasteFromClipboard();
                return true;
            } else if (itemId == R.id.save_kifu) {
                saveKifFile();
                return true;
            } else if (itemId == R.id.copy_kifu) {
                copyKif();
                return true;
            } else if (itemId == R.id.share_kifu) {
                shareKif();
                return true;
            } else if (itemId == R.id.action_settings) {
                // 設定ページの表示
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void flipBoard() {
        boardView.flipUpsideDown();
        refresh();
    }

    /**
     * Starts over from the initial position. The current game is saved under its
     * own id first and stays in the 棋譜一覧, so nothing is lost and nothing is asked.
     */
    private void newGame() {
        if (!requireIdleNavigation()) {
            return;
        }
        resetGame();
    }

    private void resetGame() {
        if (engine.isSearching()) {
            // A manual search for a position we are about to throw away.
            engine.stopSearch();
        }
        saveGame();
        startFreshGame();
        Toast.makeText(this, R.string.new_game_done, Toast.LENGTH_SHORT).show();
    }

    /** Puts an empty game on the board; the previous one is left as it was saved. */
    private void startFreshGame() {
        startFreshGame(false);
    }

    private void startFreshGame(boolean forceNewId) {
        invalidatePendingGameLoad();
        game.startNew(newEmptyRecord(), forceNewId);
        showRecord();
    }

    /**
     * Shows a saved game. The one on the board was already written when this
     * activity stopped for the list, so nothing is saved here.
     */
    private void openSavedGame(String id) {
        final long requestGeneration = ++gameLoadGeneration;
        initialGameLoadPending = false;
        final String previousId = game.id();
        final boolean deletePreviousScratch = game.isScratchGame() && !id.equals(previousId);

        io.execute(() -> {
            SavedGame saved = gameStore.load(id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestGeneration != gameLoadGeneration) {
                    return;
                }
                if (saved == null) {
                    Toast.makeText(this, R.string.game_list_open_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (engine.isSearching()) {
                    // A manual search for a position we are about to leave.
                    engine.stopSearch();
                }
                applySavedGame(saved);
                AppSettings.setLastGameId(this, game.id());
                if (deletePreviousScratch) {
                    io.execute(() -> gameStore.delete(previousId));
                }
            });
        });
    }

    private GameRecord newEmptyRecord() {
        return new GameRecord(Shogi.STARTING_SFEN,
                getString(R.string.default_black_name), getString(R.string.default_white_name));
    }

    /** Shows the session's record everywhere: board, move list and name rows. */
    private void showRecord() {
        GameRecord current = game.record();
        moveListAdapter.setRecord(current);
        graphView.setData(current, game.analysis());
        // Fires the position listener, which clears the arrows and the reading.
        boardView.setRecord(current);
        refresh();
    }

    /** Shows the reading, the move list or the graph below the board, whichever tab is checked. */
    private void showInfoTab(int tabId) {
        boolean showMoves = tabId == R.id.tab_record;
        boolean showGraph = tabId == R.id.tab_graph;
        engineView.setVisibility(!showMoves && !showGraph ? View.VISIBLE : View.GONE);
        moveListView.setVisibility(showMoves ? View.VISIBLE : View.GONE);
        graphView.setVisibility(showGraph ? View.VISIBLE : View.GONE);
        if (showMoves) {
            moveListView.scrollToPosition(game.record().currentPly());
        }
    }

    private void onHintButtonClicked(View view) {
        if (analysisRun != null) {
            // The analysis loop owns the engine until it finishes or is cancelled.
            Toast.makeText(this, R.string.engine_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        if (engine.isSearching()) {
            engine.stopSearch();
            return;
        }
        if (!requireReadyEngine()) {
            return;
        }

        setHintButtonRunning(true);
        // The engine stops by itself when its time is up, so reset the
        // button from the completion callback rather than only on tap.
        final int ply = game.record().currentPly();
        final GameRecord searched = game.record();
        final EngineSession searchedWith = engine;
        boolean started = searchedWith.startSearch(boardView.getSFEN(), AppSettings.thinkTimeMs(this),
                result -> {
                    if (engine != searchedWith) {
                        return; // The evaluation file was replaced during this search.
                    }
                    storeResult(searched, ply, result);
                    setHintButtonRunning(false);
                });
        if (!started) {
            setHintButtonRunning(false);
        }
    }

    /** Returns true if a new search may be started, showing why if it may not. */
    private boolean requireReadyEngine() {
        refreshEngineForEvaluation();
        if (!evaluationFile().isFile()) {
            showMissingEvaluationDialog();
            return false;
        }
        switch (engine.getState()) {
            case NEW:
                engine.start();
                Toast.makeText(this, R.string.engine_starting, Toast.LENGTH_SHORT).show();
                return false;
            case READY:
                return true;
            case STARTING:
                Toast.makeText(this, R.string.engine_starting, Toast.LENGTH_SHORT).show();
                return false;
            case SEARCHING:
            case STOPPING:
                Toast.makeText(this, R.string.engine_busy, Toast.LENGTH_SHORT).show();
                return false;
            case CLOSED:
                // EngineSession represents one process lifetime. Retry with a fresh instance.
                startEngineSession();
                Toast.makeText(this,
                        engine.getState() == EngineSession.State.STARTING
                                ? R.string.engine_starting
                                : R.string.engine_unavailable,
                        Toast.LENGTH_SHORT).show();
                return false;
            default:
                Toast.makeText(this, R.string.engine_unavailable, Toast.LENGTH_SHORT).show();
                return false;
        }
    }

    private void setHintButtonRunning(boolean running) {
        hintButton.setText(running ? R.string.stop_text : R.string.hint_text);
        hintButton.setIconResource(running ? R.drawable.ic_cancel : R.drawable.ic_auto_awesome);
    }

    /** Returns true if the user may move through the record right now. */
    private boolean requireIdleNavigation() {
        if (analysisRun != null) {
            Toast.makeText(this, R.string.analysis_navigation_locked, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void onPositionChanged() {
        if (analysisRun == null && engine.isSearching()) {
            // A manual search for the position the user just left. Its result
            // is still stored when it arrives, but the engine is wanted here now.
            engine.stopSearch();
        }
        // The reading needs the move that led here to write its first move as 同.
        engineView.setPreviousMove(game.record().lastMove());
        showStoredAnalysis();
        moveListAdapter.setCurrentPly(game.record().currentPly());
        graphView.setCurrentPly(game.record().currentPly());
        if (moveListView.getVisibility() == View.VISIBLE) {
            moveListView.scrollToPosition(game.record().currentPly());
        }
    }

    /**
     * Keeps a finished search under the ply it was started from, unless the
     * record has been replaced or changed at that ply in the meantime.
     *
     * <p>A manual search can outlive a user move that branches the record:
     * the ply still exists, but now holds another position, so the searched
     * SFEN is compared with the record's position at {@code ply} before it
     * is stored and shown in the move list.
     */
    private void storeResult(GameRecord searched, int ply, EngineSession.SearchResult result) {
        // A user-requested stop is not a completed analysis. Keep any previous result intact.
        if (!result.completedNormally()
                || searched != game.record()
                || ply > game.record().length()
                || !game.storeResult(searched, ply, sfenAtPly(ply), result)) {
            return;
        }
        // The session replaces the previous completed result regardless of time or depth.
        moveListAdapter.analysisChanged(ply);
        graphView.invalidate();
    }

    /** The SFEN of the record's position after {@code ply} moves, replayed on a scratch board. */
    private String sfenAtPly(int ply) {
        Board board = new Board(game.record().startSfen());
        try {
            for (int i = 0; i < ply; i++) {
                board.push(game.record().move(i));
            }
            return board.getSFEN();
        } finally {
            board.cleanup();
        }
    }

    /**
     * Shows the stored result for the displayed position, or clears the
     * candidate arrows and the reading when there is none, so that nothing
     * from another position lingers on screen.
     */
    private void showStoredAnalysis() {
        String sfen = boardView.getSFEN();
        PositionAnalysis result = game.analysis().get(game.record().currentPly());
        if (result != null && !result.sfen().equals(sfen)) {
            // Should not happen: results are dropped with the plies they belong to.
            Log.w(TAG, "stored analysis does not match the position at ply " + game.record().currentPly());
            result = null;
        }
        List<EngineInfo> infos = result != null ? result.infos() : Collections.<EngineInfo>emptyList();
        Board board = new Board(sfen);
        try {
            boardView.onEngineUpdated(board, infos);
            engineView.onEngineUpdated(board, infos);
        } finally {
            board.cleanup();
        }
        showEvaluation(result);
        if (result != null) {
            // Make clear that this is a finished search, not the engine thinking.
            engineView.setText(getString(R.string.analysis_stored_marker) + "\n" + engineView.getText());
        }
    }

    private void pasteFromClipboard() {
        // クリップボードのデータを確認
        if (!(clipboard.hasPrimaryClip())) {
            Toast.makeText(this, "クリップボードにデータがありません", Toast.LENGTH_SHORT).show();
        } else if (!(clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))) {
            // テキスト以外のデータ (処理しない)
            Toast.makeText(this, "クリップボードのデータが文字列ではありません", Toast.LENGTH_SHORT).show();
        } else {
            // テキストデータの場合
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            String pasteData = item.getText().toString();
            parseShogiData(pasteData);
        }
    }

    /** The game on the board as KIF, or null (with a toast) when there is nothing to write. */
    @Nullable
    private String kifText() {
        if (!game.record().hasMoves()) {
            Toast.makeText(this, R.string.kifu_empty, Toast.LENGTH_SHORT).show();
            return null;
        }
        return KifWriter.write(game.record(), game.createdAt());
    }

    /** Lets the user choose the on-disk encoding explicitly before opening the document picker. */
    private void saveKifFile() {
        if (!requireIdleNavigation()) {
            return;
        }
        String text = kifText();
        if (text == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.save_kifu)
                .setItems(R.array.kifu_file_types, (dialog, choice) -> {
                    pendingKifExport = text;
                    pendingKifFormat = choice;
                    if (AppSettings.kifSaveFolder(this) == null) {
                        promptForKifFolder();
                    } else {
                        launchPendingKifExport();
                    }
                })
                .show();
    }

    private void promptForKifFolder() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.kifu_folder_title)
                .setMessage(R.string.kifu_folder_message)
                .setPositiveButton(R.string.kifu_folder_choose,
                        (dialog, which) -> kifFolderPicker.launch(null))
                .setNeutralButton(R.string.kifu_folder_skip,
                        (dialog, which) -> launchPendingKifExport())
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> pendingKifExport = null)
                .show();
    }

    private void launchPendingKifExport() {
        if (pendingKifFormat == 0) {
            createKifLauncher.launch("AndroShogi.kif");
        } else {
            createKifuLauncher.launch("AndroShogi.kifu");
        }
    }

    /** Writes a snapshot from before the picker opened, so later game changes cannot alter it. */
    private void exportKif(@Nullable Uri uri, Charset charset) {
        String text = pendingKifExport;
        pendingKifExport = null;
        if (uri == null) {
            return;
        }
        if (text == null) {
            Toast.makeText(this, R.string.kifu_file_write_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        io.execute(() -> {
            int message;
            try {
                // Encode before opening the document; unsupported characters must not be replaced.
                byte[] data = KifTextCodec.encode(text, charset);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) {
                        throw new IOException("Cannot open document for writing");
                    }
                    out.write(data);
                }
                message = R.string.kifu_file_saved;
            } catch (CharacterCodingException e) {
                Log.w(TAG, "KIF contains characters unsupported by " + charset, e);
                message = R.string.kifu_file_encoding_failed;
            } catch (IOException | SecurityException e) {
                Log.w(TAG, "Cannot write KIF document " + uri, e);
                message = R.string.kifu_file_write_failed;
            }
            final int resultMessage = message;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void copyKif() {
        String kif = kifText();
        if (kif == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("KIF", kif));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 13 and later show their own confirmation.
            Toast.makeText(this, R.string.kifu_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Hands the KIF to another app through the share sheet. */
    private void shareKif() {
        String kif = kifText();
        if (kif == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, kif);
        send.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.kifu_share_subject, game.record().blackName(), game.record().whiteName()));
        Intent chooser = Intent.createChooser(send, getString(R.string.share_kifu));
        // This app receives shared KIF too; sharing to itself would only reload the same game.
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                new ComponentName[] {new ComponentName(this, MainActivity.class)});
        startActivity(chooser);
    }

    /** Loads a KIF shared from another app, as text or as a file; other intents are ignored. */
    @SuppressWarnings("deprecation")
    private void handleIncomingIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }

        // EXTRA_TEXT is already in memory; some senders put a Spanned in it.
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared != null) {
            invalidatePendingGameLoad();
            parseSharedText(shared.toString());
            return;
        }

        Uri stream = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                : intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream == null) {
            Toast.makeText(this, R.string.kifu_received_read_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        loadKifFromUri(stream, R.string.kifu_received_read_failed);
    }

    /** Reads a selected or shared KIF document off the UI thread. */
    private void loadKifFromUri(Uri uri, int readErrorMessage) {
        final long requestGeneration = ++gameLoadGeneration;
        initialGameLoadPending = false;
        io.execute(() -> {
            String text = readSharedText(uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestGeneration != gameLoadGeneration) {
                    return;
                }
                if (text == null) {
                    Toast.makeText(this, readErrorMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                parseSharedText(text);
            });
        });
    }

    private void parseSharedText(String text) {
        // Editors on Windows often put a byte order mark before the first header line.
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        parseShogiData(text);
    }

    /**
     * Reads a selected or shared file as text on the I/O executor. KIF files are traditionally
     * Shift_JIS while .kifu files are UTF-8, so UTF-8 is tried first and Shift_JIS
     * is the fallback.
     */
    @Nullable
    private String readSharedText(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, n);
            }
            return KifTextCodec.decode(bytes.toByteArray());
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Cannot read KIF document " + uri, e);
            return null;
        }
    }

    public void parseShogiData(String data) {
        if (analysisRun != null || engine.isSearching()) {
            Toast.makeText(this, R.string.engine_busy, Toast.LENGTH_SHORT).show();
            return;
        }

        KifParser parser;
        try {
            parser = KifParser.parse(data);
        } catch (KifParser.KifParseException e) {
            Log.w(TAG, "Failed to parse KIF", e);
            new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                    .setTitle(R.string.kifu_parse_error_title)
                    .setMessage(e.getMessage())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        Log.i(TAG, String.format("先手は「%s」\n", parser.blackName()));
        Log.i(TAG, String.format("後手は「%s」\n", parser.whiteName()));
        saveGame();
        invalidatePendingGameLoad();
        game.startNew(new GameRecord(parser.getSFEN(), parser.getMoves(), parser.getTimes(),
                parser.getComments(), parser.blackName(), parser.whiteName()), false);
        showRecord();
        Toast.makeText(this, R.string.kifu_loaded, Toast.LENGTH_SHORT).show();
    }

    /** Puts the names and the evaluation into the player rows, following the board's orientation. */
    public void refresh() {
        if (boardView.isUpsideDown()) {
            whiteInfoView.setText(game.record().blackName());
            blackInfoView.setText(game.record().whiteName());
        } else {
            whiteInfoView.setText(game.record().whiteName());
            blackInfoView.setText(game.record().blackName());
        }
        showEvaluation(shownEvaluation);
    }

    /**
     * Shows the win rate and score of {@code analysis} in the player rows, each
     * side from its own point of view, or clears them when there is none.
     */
    private void showEvaluation(@Nullable PositionAnalysis analysis) {
        shownEvaluation = analysis;
        String black = EvaluationLabel.forBlack(analysis);
        String white = EvaluationLabel.forWhite(analysis);
        if (boardView.isUpsideDown()) {
            whiteScoreView.setText(black);
            blackScoreView.setText(white);
        } else {
            whiteScoreView.setText(white);
            blackScoreView.setText(black);
        }
    }

    public void autoAnalysis(View view) {
        if (analysisRun != null) {
            Toast.makeText(this, R.string.engine_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!requireReadyEngine()) {
            return;
        }
        showParameterDialog(this, (time, start) -> {
            // 開始局面から検討なら局面を最初に戻す
            if (start == ConsiderStart.BEGINNING) {
                boardView.setStartBoard();
            }

            Toast.makeText(this, R.string.analysis_started, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "自動検討時間: " + time + "ms");
            startAutoAnalysis(time);
        });
    }

    /** Connects the view and engine to the UI-independent analysis state machine. */
    private void startAutoAnalysis(int timePerMoveMs) {
        final GameRecord searchedRecord = game.record();
        final EngineSession session = engine;
        AnalysisController.Searcher searcher = new AnalysisController.Searcher() {
            @Override
            public boolean start(String sfen, int timeMs, EngineSession.SearchCallback callback) {
                return session.startSearch(sfen, timeMs, callback);
            }

            @Override
            public boolean isSearching() {
                return session.isSearching();
            }

            @Override
            public boolean isAvailable() {
                return session.getState() != EngineSession.State.CLOSED;
            }

            @Override
            public void stop() {
                session.stopSearch();
            }
        };
        AnalysisController.Position position = new AnalysisController.Position() {
            @Override
            public String sfen() {
                return boardView.getSFEN();
            }

            @Override
            public void forward() {
                boardView.forwardBoard();
            }
        };
        AnalysisController.Listener listener = new AnalysisController.Listener() {
            @Override
            public void onProgress(int analyzed, int total) {
                analysisProgress.setMax(total);
                analysisProgress.setProgress(analyzed);
                analysisLabel.setText(getString(R.string.analysis_progress_format,
                        Math.min(analyzed + 1, total), total));
            }

            @Override
            public void onResult(int ply, EngineSession.SearchResult result) {
                storeResult(searchedRecord, ply, result);
            }

            @Override
            public void onPositionAdvanced() {
                // BoardView.forwardBoard() already updates all position-dependent views.
            }

            @Override
            public void onFinished(AnalysisController.Status status) {
                analysisRun = null;
                analysisPanel.setVisibility(View.GONE);
                boardView.setInputLocked(false);
                // Preserve the finished positions even after cancellation or an engine error.
                saveGame();
                int message = status == AnalysisController.Status.COMPLETED
                        ? R.string.analysis_finished
                        : status == AnalysisController.Status.CANCELLED
                                ? R.string.analysis_cancelled : R.string.engine_unavailable;
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        };
        analysisRun = new AnalysisController(searchedRecord, timePerMoveMs,
                searcher, position, listener);
        boardView.setInputLocked(true);
        analysisPanel.setVisibility(View.VISIBLE);
        analysisRun.start();
    }

    public void showParameterDialog(Context context, ParameterCallback callback) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_engine_parameters, null);

        Spinner timeSpinner = dialogView.findViewById(R.id.consider_time_spinner);
        RadioGroup startPositionGroup = dialogView.findViewById(R.id.radio_group_start_position);

        // ResourceのarrayからSpinnerのアイテム名をコピー
        String[] displayArray = context.getResources().getStringArray(R.array.consider_time_choices);
        String[] valueArray = context.getResources().getStringArray(R.array.consider_time_values);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, displayArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(adapter);
        int lastTime = AppSettings.analysisTimeMs(context);
        for (int i = 0; i < valueArray.length; i++) {
            if (Integer.parseInt(valueArray[i]) == lastTime) {
                timeSpinner.setSelection(i);
                break;
            }
        }

        // ダイアログの表示
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Androshogi_AlertDialog);
        builder.setTitle("棋譜検討");
        builder.setView(dialogView);
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Get the selected item's position
            int selectedPosition = timeSpinner.getSelectedItemPosition();

            // Map to corresponding value
            int timeValue = Integer.parseInt(valueArray[selectedPosition]);
            AppSettings.setAnalysisTimeMs(context, timeValue);

            // Get the selected start position
            int selectedId = startPositionGroup.getCheckedRadioButtonId();
            ConsiderStart start = selectedId == R.id.radio_beginning ? ConsiderStart.BEGINNING : ConsiderStart.CURRENT;

            // Pass the parameters back using the callback
            callback.onParameterSet(timeValue, start);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}

enum ConsiderStart {
    BEGINNING,
    CURRENT
}

interface ParameterCallback {
    void onParameterSet(int timeValue, ConsiderStart start);
}
