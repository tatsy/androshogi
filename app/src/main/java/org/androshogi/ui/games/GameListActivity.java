package org.androshogi.ui.games;

import org.androshogi.R;
import org.androshogi.ui.main.MainActivity;

import org.androshogi.storage.GameSummary;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The saved games, most recently updated first. Tapping one hands its id
 * back to {@link MainActivity}; selection mode deletes multiple games after asking.
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

    private static final String STATE_SELECTION = "selection";
    private static final String STATE_SELECTING = "selecting";
    private static final String STATE_DELETION_VERSION = "deletion_version";
    private final Set<String> selectedIds = new HashSet<>();
    private GameListViewModel model;
    private GameListViewModel.State state;
    private boolean selecting;
    private boolean dialogOpen;
    private int handledDeletionVersion;
    @Nullable
    private String currentId;
    private boolean currentDeleted;
    private GameListAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentId = getIntent().getStringExtra(EXTRA_CURRENT_ID);
        if (savedInstanceState != null) {
            selecting = savedInstanceState.getBoolean(STATE_SELECTING);
            currentDeleted = savedInstanceState.getBoolean(EXTRA_CURRENT_DELETED);
            handledDeletionVersion = savedInstanceState.getInt(STATE_DELETION_VERSION);
            ArrayList<String> selection = savedInstanceState.getStringArrayList(STATE_SELECTION);
            if (selection != null) selectedIds.addAll(selection);
        }

        emptyView = findViewById(R.id.game_list_empty);
        RecyclerView list = findViewById(R.id.game_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GameListAdapter();
        list.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBack();
            }
        });
        model = new ViewModelProvider(this).get(GameListViewModel.class);
        // A new process starts a new sequence of deletion notifications.
        if (model.state().getValue().deletionVersion < handledDeletionVersion) {
            handledDeletionVersion = 0;
        }
        model.state().observe(this, this::showState);
    }

    @Override
    public boolean onSupportNavigateUp() {
        navigateBack();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean(STATE_SELECTING, selecting);
        out.putBoolean(EXTRA_CURRENT_DELETED, currentDeleted);
        out.putInt(STATE_DELETION_VERSION, handledDeletionVersion);
        out.putStringArrayList(STATE_SELECTION, new ArrayList<>(selectedIds));
        super.onSaveInstanceState(out);
    }

    private boolean busy() {
        return state == null || state.busy;
    }

    private void navigateBack() {
        if (busy()) return;
        if (selecting) {
            selecting = false;
            selectedIds.clear();
            refreshSelection();
        } else {
            finishWithResult(null);
        }
    }

    private void showState(GameListViewModel.State newState) {
        state = newState;
        currentDeleted |= state.deletedIds.contains(currentId);
        if (!state.busy) {
            Set<String> available = new HashSet<>();
            for (GameSummary game : state.games) available.add(game.id);
            selectedIds.retainAll(available);
            if (state.deletionVersion > handledDeletionVersion) {
                handledDeletionVersion = state.deletionVersion;
                String message = state.failedCount == 0
                        ? getString(R.string.game_list_deleted_count, state.deletedCount)
                        : getString(R.string.game_list_delete_partial, state.deletedCount, state.failedCount);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (selectedIds.isEmpty()) selecting = false;
            }
        }
        adapter.submit(state.games);
        emptyView.setVisibility(!state.busy && state.games.isEmpty() ? View.VISIBLE : View.GONE);
        updateToolbar();
    }

    private void refreshSelection() {
        adapter.notifyDataSetChanged();
        updateToolbar();
    }

    private void updateToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(selecting
                    ? getString(R.string.game_list_selected_count, selectedIds.size())
                    : getString(R.string.game_list));
            getSupportActionBar().setSubtitle(busy() ? getString(R.string.game_list_busy) : null);
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.game_select).setVisible(!selecting).setEnabled(!busy() && adapter.getItemCount() > 0);
        menu.findItem(R.id.game_select_all).setVisible(selecting).setEnabled(!busy())
                .setTitle(selectedIds.size() == adapter.getItemCount()
                        ? R.string.game_list_deselect_all : R.string.game_list_select_all);
        menu.findItem(R.id.game_delete_selected).setVisible(selecting).setEnabled(!busy() && !selectedIds.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.game_select || id == R.id.game_select_all || id == R.id.game_delete_selected) {
            if (busy()) return true;
            if (id == R.id.game_select) {
                selecting = true;
                refreshSelection();
            } else if (id == R.id.game_select_all) {
                if (selectedIds.size() == adapter.getItemCount()) {
                    selectedIds.clear();
                } else {
                    for (GameSummary game : state.games) selectedIds.add(game.id);
                }
                refreshSelection();
            } else {
                confirmDelete(new ArrayList<>(selectedIds),
                        getString(R.string.game_list_delete_selected_message, selectedIds.size()));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleSelection(String id) {
        if (busy()) return;
        if (!selectedIds.remove(id)) selectedIds.add(id);
        refreshSelection();
    }

    private void finishWithResult(@Nullable String openId) {
        if (busy()) return;
        Intent result = new Intent();
        if (openId != null) {
            result.putExtra(EXTRA_OPEN_ID, openId);
        }
        result.putExtra(EXTRA_CURRENT_DELETED, currentDeleted);
        setResult(RESULT_OK, result);
        finish();
    }

    private void confirmDelete(GameSummary game) {
        confirmDelete(Collections.singletonList(game.id),
                getString(R.string.game_list_delete_message, describePlayers(game)));
    }

    private void confirmDelete(List<String> ids, String message) {
        if (busy() || dialogOpen || ids.isEmpty()) return;
        dialogOpen = true;
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.game_list_delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.game_list_delete_action, (d, which) -> model.delete(ids))
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> dialogOpen = false)
                .show();
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
            holder.itemView.setEnabled(!busy());
            holder.itemView.setOnClickListener(v -> {
                if (selecting) toggleSelection(game.id);
                else finishWithResult(game.id);
            });
            holder.check.setVisibility(selecting ? View.VISIBLE : View.GONE);
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(selectedIds.contains(game.id));
            holder.check.setEnabled(!busy());
            holder.check.setContentDescription(getString(R.string.game_list_select_game, describePlayers(game)));
            holder.check.setOnCheckedChangeListener((button, checked) -> toggleSelection(game.id));
            holder.delete.setVisibility(selecting ? View.GONE : View.VISIBLE);
            holder.delete.setEnabled(!busy());
            holder.delete.setOnClickListener(v -> confirmDelete(game));
        }
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView players;
        final TextView details;
        final ImageButton delete;
        final CheckBox check;

        RowHolder(View itemView) {
            super(itemView);
            players = itemView.findViewById(R.id.game_players);
            details = itemView.findViewById(R.id.game_details);
            delete = itemView.findViewById(R.id.game_delete);
            check = itemView.findViewById(R.id.game_check);
        }
    }
}
