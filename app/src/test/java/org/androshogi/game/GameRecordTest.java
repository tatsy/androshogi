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

    @Test public void nodeIdsFollowPositionsAndAreNeverReusedAfterAnEdit() {
        GameRecord r = fourMoves();
        long first = r.nodeIdAtPly(1);
        long second = r.nodeIdAtPly(2);
        long end = r.nodeIdAtPly(4);
        r.seek(1);
        r.play(22);
        assertEquals(second, r.currentNodeId());
        r.seek(1);
        r.play(99);
        assertEquals(first, r.nodeIdAtPly(1));
        assertTrue(r.currentNodeId() > end);
        assertNull(r.node(second));
        assertEquals(Long.valueOf(first), r.node(r.currentNodeId()).parentId());
    }

    @Test public void selectionKeepsMainLineAndVariationMetadata() {
        GameRecord r = fourMoves();
        long parent = r.nodeIdAtPly(1);
        long main = r.nodeIdAtPly(2);
        long variation = r.addVariation(parent, 99, 17, "variation");
        long leaf = r.addVariation(variation, 88, 9, "continuation");
        assertEquals(MOVES, r.moves());
        assertEquals(Long.valueOf(main), r.node(parent).mainChildId());
        assertEquals(variation, r.addVariation(parent, 99, 0, null));
        r.selectNode(variation);
        assertEquals(Arrays.asList(11, 99, 88), r.moves());
        assertEquals(2, r.currentPly());
        assertEquals(leaf, r.nodeIdAtPly(3));
        assertEquals(17, r.timeSeconds(1));
        assertEquals("variation", r.comment(1));
        assertEquals(Long.valueOf(main), r.node(parent).mainChildId());
        r.selectNode(main);
        assertEquals(MOVES, r.moves());
        assertEquals(7, r.timeSeconds(1));
        assertEquals("c2", r.comment(1));
    }

    @Test public void snapshotDetachesAllBranchesAndTheirSelections() {
        GameRecord r = fourMoves();
        long main = r.nodeIdAtPly(2);
        long branch = r.addVariation(r.nodeIdAtPly(1), 99, 2, "branch");
        r.selectNode(branch);
        GameRecord copy = new GameRecord(r);
        assertEquals(r.nextNodeId(), copy.nextNodeId());
        r.selectNode(main);
        r.truncate(0);
        assertEquals(Arrays.asList(11, 99), copy.moves());
        assertEquals(branch, copy.currentNodeId());
        assertEquals(6, copy.nodes().size());
        copy.selectNode(main);
        assertEquals(MOVES, copy.moves());
    }

    @Test public void userMoveKeepsOriginalContinuationAndReusesExistingChildren() {
        GameRecord r = fourMoves();
        long main = r.nodeIdAtPly(2);
        r.seek(1);
        r.playVariation(99);
        long branch = r.currentNodeId();
        assertEquals(Arrays.asList(11, 99), r.moves());
        assertEquals(6, r.nodes().size());
        assertFalse(r.isMainLineSelected());
        r.seek(1);
        r.playVariation(22);
        assertEquals(main, r.currentNodeId());
        assertEquals(MOVES, r.moves());
        assertEquals(7, r.timeSeconds(1));
        assertEquals("c2", r.comment(1));
        assertTrue(r.isMainLineSelected());
        r.seek(1);
        r.playVariation(99);
        assertEquals(branch, r.currentNodeId());
        assertEquals(6, r.nodes().size());
    }

    @Test public void returningToMainLineResetsNestedChoicesAndClampsOnlyTheCursor() {
        GameRecord r = fourMoves();
        r.seek(2);
        r.playVariation(88);
        long nestedBranch = r.currentNodeId();
        r.playVariation(77);
        r.playVariation(66);
        r.selectMainLine();
        assertEquals(MOVES, r.moves());
        assertEquals(4, r.currentPly());
        assertTrue(r.isMainLineSelected());
        r.selectNode(nestedBranch);
        assertEquals(Arrays.asList(11, 22, 88, 77, 66), r.moves());
        r.seek(1);
        assertFalse(r.isMainLineSelected()); // A variation is still selected beyond the cursor.
        r.selectMainLine();
        assertEquals(1, r.currentPly());
        assertEquals(MOVES, r.moves());
        assertEquals(8, r.nodes().size());
    }

    @Test public void playingAtAnEmptyEndExtendsTheMainLine() {
        GameRecord r = new GameRecord(Shogi.STARTING_SFEN, "B", "W");
        r.selectMainLine();
        assertTrue(r.isMainLineSelected());
        r.playVariation(11);
        r.playVariation(22);
        assertEquals(Arrays.asList(11, 22), r.moves());
        assertEquals(2, r.currentPly());
        assertTrue(r.isMainLineSelected());
    }

    @Test public void movesViewRemainsReadOnlyAndReflectsEdits() {
        GameRecord r = fourMoves();
        List<Integer> view = r.moves();
        r.truncate(2);
        assertEquals(Arrays.asList(11, 22), view);
        org.junit.Assert.assertThrows(UnsupportedOperationException.class, () -> view.add(55));
        org.junit.Assert.assertThrows(IndexOutOfBoundsException.class, () -> r.move(-1));
    }

}
