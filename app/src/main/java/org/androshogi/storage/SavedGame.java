package org.androshogi.storage;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;

/**
 * A game as kept on disk: the record, its analysis, and when it was made and
 * last written. The id names the file and is what the settings remember as
 * the game to reopen.
 */
public final class SavedGame {
    public final String id;
    public final long createdAt;
    public final long updatedAt;
    public final GameRecord record;
    public final GameAnalysis analysis;

    public SavedGame(String id, long createdAt, long updatedAt, GameRecord record, GameAnalysis analysis) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.record = record;
        this.analysis = analysis;
    }
}
