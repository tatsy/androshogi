package org.androshogi.storage;

/** What the list of saved games shows about one file, without loading its analysis. */
public final class GameSummary {
    public final String id;
    public final String blackName;
    public final String whiteName;
    public final int moveCount;
    public final int analyzedCount;
    public final long createdAt;
    public final long updatedAt;
    public final boolean saved;

    public GameSummary(String id, String blackName, String whiteName, int moveCount,
                       int analyzedCount, long createdAt, long updatedAt) {
        this(id, blackName, whiteName, moveCount, analyzedCount, createdAt, updatedAt, true);
    }

    public GameSummary(String id, String blackName, String whiteName, int moveCount,
                       int analyzedCount, long createdAt, long updatedAt, boolean saved) {
        this.id = id;
        this.blackName = blackName;
        this.whiteName = whiteName;
        this.moveCount = moveCount;
        this.analyzedCount = analyzedCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.saved = saved;
    }

    public GameSummary withSaved(boolean value) {
        return new GameSummary(id, blackName, whiteName, moveCount, analyzedCount, createdAt, updatedAt, value);
    }
}
