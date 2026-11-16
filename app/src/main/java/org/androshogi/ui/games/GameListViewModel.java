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
    enum Operation { DELETE, SAVE, HISTORY }

    static final class State {
        final List<GameSummary> games;
        final boolean busy;
        final Set<String> deletedIds;
        final int operationVersion;
        final Operation operation;
        final int changedCount;
        final int failedCount;

        State(List<GameSummary> games, boolean busy, Set<String> deletedIds,
              int operationVersion, Operation operation, int changedCount, int failedCount) {
            this.games = Collections.unmodifiableList(new ArrayList<>(games));
            this.busy = busy;
            this.deletedIds = Collections.unmodifiableSet(new HashSet<>(deletedIds));
            this.operationVersion = operationVersion;
            this.operation = operation;
            this.changedCount = changedCount;
            this.failedCount = failedCount;
        }
    }

    private final GameStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(Collections.emptyList(), true, Collections.emptySet(), 0, null, 0, 0));

    public GameListViewModel(@NonNull Application application) {
        super(application);
        store = new GameStore(application);
        io.execute(() -> state.postValue(new State(store.list(), false,
                Collections.emptySet(), 0, null, 0, 0)));
    }

    LiveData<State> state() {
        return state;
    }

    /** Called on the UI thread. A batch cannot overlap another file operation. */
    void delete(List<String> ids) {
        runBatch(ids, Operation.DELETE);
    }

    void move(List<String> ids, boolean saved) {
        runBatch(ids, saved ? Operation.SAVE : Operation.HISTORY);
    }

    private void runBatch(List<String> ids, Operation operation) {
        State before = state.getValue();
        if (before == null || before.busy || ids.isEmpty()) {
            return;
        }
        List<String> requested = new ArrayList<>(new HashSet<>(ids));
        state.setValue(new State(before.games, true, before.deletedIds,
                before.operationVersion, operation, 0, 0));
        io.execute(() -> {
            Set<String> deletedIds = new HashSet<>(before.deletedIds);
            Set<String> succeeded = new HashSet<>();
            for (String id : requested) {
                boolean success = operation == Operation.DELETE ? store.delete(id)
                        : store.setSaved(id, operation == Operation.SAVE);
                if (success) {
                    succeeded.add(id);
                    if (operation == Operation.DELETE) deletedIds.add(id);
                }
            }
            // Failed records retain their category and selection; successful moves keep their IDs.
            List<GameSummary> remaining = new ArrayList<>();
            for (GameSummary game : before.games) {
                if (!succeeded.contains(game.id)) {
                    remaining.add(game);
                } else if (operation != Operation.DELETE) {
                    remaining.add(game.withSaved(operation == Operation.SAVE));
                }
            }
            state.postValue(new State(remaining, false, deletedIds,
                    before.operationVersion + 1, operation, succeeded.size(), requested.size() - succeeded.size()));
        });
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }
}
