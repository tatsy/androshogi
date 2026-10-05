package org.androshogi.game;

import org.androshogi.engine.EngineSession;
import org.androshogi.shogi.Shogi;
import org.androshogi.storage.SavedGame;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The current game, its analysis, and the identity used when saving it. */
public final class GameSession {
    private final Supplier<String> newId;
    private final LongSupplier clock;
    private final String defaultBlackName;
    private final String defaultWhiteName;
    // Keep this instance stable: views hold a reference to it while a game is loaded.
    private final GameAnalysis analysis;
    private GameRecord record;
    private String id;
    private long createdAt;

    /** The initial record supplies the default names for identifying untouched scratch games. */
    public GameSession(GameRecord initialRecord, Supplier<String> newId, LongSupplier clock) {
        this.newId = newId;
        this.clock = clock;
        defaultBlackName = initialRecord.blackName();
        defaultWhiteName = initialRecord.whiteName();
        record = initialRecord;
        analysis = new GameAnalysis(record);
        id = newId.get();
        createdAt = clock.getAsLong();
    }

    public GameRecord record() { return record; }
    public GameAnalysis analysis() { return analysis; }
    public String id() { return id; }
    public long createdAt() { return createdAt; }

    public boolean isScratchGame() {
        return !record.hasMoves() && analysis.count() == 0
                && Objects.equals(defaultBlackName, record.blackName())
                && Objects.equals(defaultWhiteName, record.whiteName())
                && (record.startComment() == null || record.startComment().isEmpty())
                && Shogi.STARTING_SFEN.equals(record.startSfen());
    }

    /** Starts another game, reusing only an untouched scratch game's slot. */
    public void startNew(GameRecord next, boolean forceNewId) {
        if (forceNewId || id == null || !isScratchGame()) {
            id = newId.get();
        }
        createdAt = clock.getAsLong();
        record = next;
        analysis.resetForRecord(record);
    }

    public void load(SavedGame saved) {
        id = saved.id;
        createdAt = saved.createdAt;
        record = saved.record;
        analysis.resetForRecord(record);
        analysis.putAll(saved.analysis);
    }

    public void truncateAnalysis(int fromPly) {
        analysis.truncate(fromPly);
    }

    /** One undo token, valid only until another data edit or record replacement. */
    public static final class LeafDeletion {
        private final GameRecord editedRecord;
        private final long editVersion;
        private final SavedGame before;

        private LeafDeletion(GameRecord editedRecord, SavedGame before) {
            this.editedRecord = editedRecord;
            this.editVersion = editedRecord.editVersion();
            this.before = before;
        }
    }

    public LeafDeletion deleteCurrentLeaf() {
        if (!record.canDeleteCurrentLeaf()) return null;
        SavedGame before = snapshot(clock.getAsLong());
        record.deleteCurrentLeaf();
        analysis.pruneDeletedNodes();
        return new LeafDeletion(record, before);
    }

    public boolean undoLeafDeletion(LeafDeletion deletion) {
        if (deletion == null || record != deletion.editedRecord
                || record.editVersion() != deletion.editVersion) return false;
        // A completed search may have posted a result after the deletion snapshot.
        GameAnalysis retained = new GameAnalysis(analysis, deletion.before.record);
        load(deletion.before);
        analysis.putAll(retained);
        return true;
    }

    /** Stores only a normally completed result for the unchanged record and position. */
    public boolean storeResult(GameRecord searched, int ply, long nodeId, String positionSfen,
                               EngineSession.SearchResult result) {
        if (result == null || !result.completedNormally() || searched != record
                || ply < 0 || ply > record.length() || record.nodeIdAtPly(ply) != nodeId
                || !result.sfen.equals(positionSfen)) {
            return false;
        }
        analysis.putByNode(nodeId, PositionAnalysis.from(result));
        return true;
    }

    /** Makes detached data for the I/O executor; the live game remains on the UI thread. */
    public SavedGame snapshot(long updatedAt) {
        GameRecord copy = new GameRecord(record);
        return new SavedGame(id, createdAt, updatedAt, copy, new GameAnalysis(analysis, copy));
    }
}
