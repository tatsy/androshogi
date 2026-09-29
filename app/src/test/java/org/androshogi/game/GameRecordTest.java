package org.androshogi.game;

import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class GameRecordTest {
    private static final List<Integer> MOVES = Arrays.asList(11, 22, 33, 44);

    private static GameRecord fourMoves() {
        return new GameRecord(Shogi.STARTING_SFEN, MOVES, Arrays.asList(5, 7), Arrays.asList(null, "c2"), "B", "W");
    }

    @Test
    public void emptyRecordIsAtStartAndEnd() {
        GameRecord r = new GameRecord(Shogi.STARTING_SFEN, "B", "W");
        assertEquals(0, r.length());
        assertTrue(r.isAtStart());
        assertTrue(r.isAtEnd());
        assertFalse(r.hasMoves());
        assertEquals(Shogi.MOVE_NONE, r.nextMove());
        assertEquals(Shogi.MOVE_NONE, r.lastMove());
        assertFalse(r.forward());
        assertFalse(r.backward());
    }

    @Test
    public void loadedRecordStartsAtItsEndAndPadsTimesAndComments() {
        GameRecord r = fourMoves();
        assertEquals(4, r.length());
        assertEquals(4, r.currentPly());
        assertTrue(r.isAtEnd());
        assertEquals(0, r.remaining());
        assertEquals(5, r.timeSeconds(0));
        assertEquals(7, r.timeSeconds(1));
        assertEquals(0, r.timeSeconds(3));
        assertNull(r.comment(0));
        assertEquals("c2", r.comment(1));
        assertNull(r.comment(3));
    }

    @Test
    public void steppingBackAndForthStaysWithinTheRecord() {
        GameRecord r = fourMoves();
        assertTrue(r.backward());
        assertTrue(r.backward());
        assertEquals(2, r.currentPly());
        assertEquals(33, r.nextMove());
        assertEquals(22, r.lastMove());
        assertEquals(2, r.remaining());

        assertTrue(r.forward());
        assertTrue(r.forward());
        assertFalse(r.forward());
        assertEquals(4, r.currentPly());
    }

    @Test
    public void seekClamps() {
        GameRecord r = fourMoves();
        assertEquals(0, r.seek(-3));
        assertEquals(4, r.seek(99));
        assertEquals(1, r.seek(1));
        r.seekStart();
        assertTrue(r.isAtStart());
        r.seekEnd();
        assertTrue(r.isAtEnd());
    }

    @Test
    public void playingTheRecordedMoveFollowsTheRecord() {
        GameRecord r = fourMoves();
        r.seek(1);
        assertEquals(-1, r.play(22));
        assertEquals(2, r.currentPly());
        assertEquals(4, r.length());
        assertEquals(33, r.nextMove());
    }

    @Test
    public void playingADifferentMoveDiscardsTheRestOfTheRecord() {
        GameRecord r = fourMoves();
        r.seek(1);
        assertEquals(1, r.play(99));
        assertEquals(2, r.length());
        assertEquals(2, r.currentPly());
        assertTrue(r.isAtEnd());
        assertEquals(Arrays.asList(11, 99), r.moves());
        assertEquals(0, r.timeSeconds(1));
        assertNull(r.comment(1));
        assertEquals(5, r.timeSeconds(0));
    }

    @Test
    public void playingAtTheEndAppends() {
        GameRecord r = fourMoves();
        assertEquals(4, r.play(55));
        assertEquals(5, r.length());
        assertEquals(55, r.lastMove());
    }

    @Test
    public void truncateClampsTheCurrentPly() {
        GameRecord r = fourMoves();
        r.truncate(2);
        assertEquals(2, r.length());
        assertEquals(2, r.currentPly());
        r.truncate(10);
        assertEquals(2, r.length());
        r.truncate(-1);
        assertEquals(0, r.length());
        assertEquals(0, r.currentPly());
    }
}
