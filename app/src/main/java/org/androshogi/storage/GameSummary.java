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

    public GameSummary(String id, String blackName, String whiteName, int moveCount,
                       int analyzedCount, long createdAt, long updatedAt) {
        this.id = id;
        this.blackName = blackName;
        this.whiteName = whiteName;
        this.moveCount = moveCount;
        this.analyzedCount = analyzedCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
