package org.androshogi.ui.games;

import org.androshogi.R;
import org.androshogi.ui.main.MainActivity;

import org.androshogi.storage.GameStore;
import org.androshogi.storage.GameSummary;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The saved games, most recently updated first. Tapping one hands its id
 * back to {@link MainActivity}; the trash button deletes one after asking.
 *
 * <p>The result carries {@link #EXTRA_OPEN_ID} when a game was chosen and
 * {@link #EXTRA_CURRENT_DELETED} when the game that was on the board is gone,
 * so the caller can drop it instead of saving it again.
 */
public class GameListActivity extends AppCompatActivity {
    /** In: id of the game shown on the board, marked in the list. */
    public static final String EXTRA_CURRENT_ID = "current_id";
    /** Out: id of the game to open, absent when none was chosen. */
    public static final String EXTRA_OPEN_ID = "open_id";
    /** Out: true when the game named by {@link #EXTRA_CURRENT_ID} was deleted. */
    public static final String EXTRA_CURRENT_DELETED = "current_deleted";

    private GameStore store;
    @Nullable
    private String currentId;
    private boolean currentDeleted;
    private GameListAdapter adapter;
    private TextView emptyView;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        store = new GameStore(this);
        currentId = getIntent().getStringExtra(EXTRA_CURRENT_ID);

        emptyView = findViewById(R.id.game_list_empty);
        RecyclerView list = findViewById(R.id.game_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GameListAdapter();
        list.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult(null);
            }
        });
        reload();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finishWithResult(null);
        return true;
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** Reads the files off the main thread, then shows them. */
    private void reload() {
        io.execute(() -> {
            List<GameSummary> games = store.list();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                adapter.submit(games);
                emptyView.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void finishWithResult(@Nullable String openId) {
        Intent result = new Intent();
        if (openId != null) {
            result.putExtra(EXTRA_OPEN_ID, openId);
        }
        result.putExtra(EXTRA_CURRENT_DELETED, currentDeleted);
        setResult(RESULT_OK, result);
        finish();
    }

    private void confirmDelete(GameSummary game) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.game_list_delete_title)
                .setMessage(getString(R.string.game_list_delete_message, describePlayers(game)))
                .setPositiveButton(R.string.game_list_delete_action, (d, which) -> delete(game))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void delete(GameSummary game) {
        io.execute(() -> {
            boolean deleted = store.delete(game.id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!deleted) {
                    Toast.makeText(this, R.string.game_list_delete_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (game.id.equals(currentId)) {
                    currentDeleted = true;
                }
                Toast.makeText(this, R.string.game_list_deleted, Toast.LENGTH_SHORT).show();
                reload();
            });
        });
    }

    private String describePlayers(GameSummary game) {
        return getString(R.string.game_list_players, game.blackName, game.whiteName);
    }

    private final class GameListAdapter extends RecyclerView.Adapter<RowHolder> {
        private final List<GameSummary> games = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);

        void submit(List<GameSummary> newGames) {
            games.clear();
            games.addAll(newGames);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return games.size();
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_game, parent, false);
            return new RowHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            GameSummary game = games.get(position);
            String players = describePlayers(game);
            if (game.id.equals(currentId) && !currentDeleted) {
                players += getString(R.string.game_list_current);
            }
            holder.players.setText(players);
            holder.details.setText(getString(R.string.game_list_details,
                    game.moveCount, game.analyzedCount, dateFormat.format(new Date(game.updatedAt))));
            holder.itemView.setOnClickListener(v -> finishWithResult(game.id));
            holder.delete.setOnClickListener(v -> confirmDelete(game));
        }
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView players;
        final TextView details;
        final ImageButton delete;

        RowHolder(View itemView) {
            super(itemView);
            players = itemView.findViewById(R.id.game_players);
            details = itemView.findViewById(R.id.game_details);
            delete = itemView.findViewById(R.id.game_delete);
        }
    }
}
