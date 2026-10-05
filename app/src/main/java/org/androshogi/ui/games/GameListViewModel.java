package org.androshogi.ui.games;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.androshogi.storage.GameStore;
import org.androshogi.storage.GameSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps file operations and their results alive across activity recreation. */
public final class GameListViewModel extends AndroidViewModel {
    static final class State {
        final List<GameSummary> games;
        final boolean busy;
        final Set<String> deletedIds;
        final int deletionVersion;
        final int deletedCount;
        final int failedCount;

        State(List<GameSummary> games, boolean busy, Set<String> deletedIds,
              int deletionVersion, int deletedCount, int failedCount) {
            this.games = Collections.unmodifiableList(new ArrayList<>(games));
            this.busy = busy;
            this.deletedIds = Collections.unmodifiableSet(new HashSet<>(deletedIds));
            this.deletionVersion = deletionVersion;
            this.deletedCount = deletedCount;
            this.failedCount = failedCount;
        }
    }

    private final GameStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(Collections.emptyList(), true, Collections.emptySet(), 0, 0, 0));

    public GameListViewModel(@NonNull Application application) {
        super(application);
        store = new GameStore(application);
        io.execute(() -> state.postValue(new State(store.list(), false,
                Collections.emptySet(), 0, 0, 0)));
    }

    LiveData<State> state() {
        return state;
    }

    /** Called on the UI thread. A batch cannot overlap another file operation. */
    void delete(List<String> ids) {
        State before = state.getValue();
        if (before == null || before.busy || ids.isEmpty()) {
            return;
        }
        List<String> requested = new ArrayList<>(new HashSet<>(ids));
        state.setValue(new State(before.games, true, before.deletedIds,
                before.deletionVersion, 0, 0));
        io.execute(() -> {
            Set<String> deletedIds = new HashSet<>(before.deletedIds);
            Set<String> succeeded = new HashSet<>();
            for (String id : requested) {
                if (store.delete(id)) {
                    succeeded.add(id);
                    deletedIds.add(id);
                }
            }
            // Keep failed records visible and selected; no extra disk read is needed.
            List<GameSummary> remaining = new ArrayList<>();
            for (GameSummary game : before.games) {
                if (!succeeded.contains(game.id)) {
                    remaining.add(game);
                }
            }
            state.postValue(new State(remaining, false, deletedIds,
                    before.deletionVersion + 1, succeeded.size(), requested.size() - succeeded.size()));
        });
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }
}
