package org.androshogi.game;

import static org.junit.Assert.*;

import org.androshogi.shogi.Shogi;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LeafDeletionTest {
    private GameRecord record() {
        return new GameRecord(Shogi.STARTING_SFEN, Arrays.asList(11, 22),
                Arrays.asList(5, 7), Arrays.asList("first", "last"), "B", "W");
    }

    private PositionAnalysis result(String sfen) {
        return PositionAnalysis.from(sfen, Collections.emptyList(), null);
    }

    @Test public void rejectsRootAndNonLeafAndRemovesOnlyTheLastMove() {
        GameRecord r = record();
        long leaf = r.currentNodeId();
        long counter = r.nextNodeId();
        r.seekStart();
        assertFalse(r.deleteCurrentLeaf());
        r.seek(1);
        assertFalse(r.deleteCurrentLeaf());
        r.seekEnd();
        assertTrue(r.deleteCurrentLeaf());
        assertNull(r.node(leaf));
        assertEquals(Collections.singletonList(11), r.moves());
        assertEquals(1, r.currentPly());
        assertEquals(5, r.timeSeconds(0));
        assertEquals("first", r.comment(0));
        assertEquals(counter, r.nextNodeId());
        assertTrue(r.deleteCurrentLeaf());
        assertFalse(r.deleteCurrentLeaf());
        r.playVariation(33);
        assertTrue(r.currentNodeId() > leaf);
    }

    @Test public void soleSurvivingBranchIsSelectedButNeverPromotedToMain() {
        GameRecord r = record();
        long parent = r.nodeIdAtPly(1);
        long branch = r.addVariation(parent, 99, 17, "branch");
        long branchEnd = r.addVariation(branch, 88, 9, "end");
        assertTrue(r.deleteCurrentLeaf());
        assertEquals(parent, r.currentNodeId());
        assertNull(r.node(parent).mainChildId());
        assertEquals(Long.valueOf(branch), r.selectedChildId(parent));
        assertEquals(Arrays.asList(11, 99, 88), r.moves());
        assertTrue(r.forward());
        assertEquals(branch, r.currentNodeId());
        r.selectMainLine();
        assertEquals(parent, r.currentNodeId());
        assertEquals(Collections.singletonList(11), r.moves());
        assertTrue(r.isMainLineSelected());
        assertTrue(r.forward()); // A sole continuation remains navigable after main return.
        assertEquals(branch, r.currentNodeId());
        assertEquals(branchEnd, r.nodeIdAtPly(3));
        r.addVariation(parent, 77, 0, null);
        assertNull(r.node(parent).mainChildId()); // Adding another branch must not repair the main marker.
    }

    @Test public void multipleSurvivingBranchesWaitForSelection() {
        GameRecord r = record();
        long parent = r.nodeIdAtPly(1);
        long a = r.addVariation(parent, 99, 0, "a");
        long b = r.addVariation(parent, 88, 0, "b");
        assertTrue(r.deleteCurrentLeaf());
        assertNull(r.selectedChildId(parent));
        assertNull(r.node(parent).mainChildId());
        assertEquals(parent, r.currentNodeId());
        assertEquals(Shogi.MOVE_NONE, r.nextMove());
        assertFalse(r.forward());
        assertFalse(r.canDeleteCurrentLeaf()); // End of selected route is still a fork, not a leaf.
        assertEquals(Arrays.asList(a, b), r.node(parent).childIds());
        r.selectNode(b);
        r.selectMainLine();
        assertEquals(parent, r.currentNodeId());
        assertEquals(Collections.singletonList(11), r.moves());
        assertNull(r.selectedChildId(parent));
    }

    @Test public void deletingAVariationPreservesTheOriginalAndSibling() {
        GameRecord r = record();
        long parent = r.nodeIdAtPly(1);
        long main = r.currentNodeId();
        long a = r.addVariation(parent, 99, 0, "a");
        long b = r.addVariation(parent, 88, 0, "b");
        r.selectNode(a);
        assertTrue(r.deleteCurrentLeaf());
        assertEquals(Long.valueOf(main), r.node(parent).mainChildId());
        assertNull(r.node(a));
        assertEquals("b", r.node(b).comment());
        assertNull(r.selectedChildId(parent));
        r.selectMainLine();
        assertEquals(Arrays.asList(11, 22), r.moves());
        assertEquals(parent, r.currentNodeId());
    }

    @Test public void undoRestoresIdentityMetadataMarkersAndOnlyTheDeletedResult() {
        GameRecord r = record();
        long leaf = r.currentNodeId();
        long parent = r.nodeIdAtPly(1);
        long sibling = r.addVariation(parent, 99, 0, "sibling");
        GameSession game = new GameSession(r, () -> "same-id", () -> 100L);
        PositionAnalysis leafResult = result("leaf");
        PositionAnalysis siblingResult = result("sibling");
        game.analysis().putByNode(leaf, leafResult);
        game.analysis().putByNode(sibling, siblingResult);
        GameSession.LeafDeletion deletion = game.deleteCurrentLeaf();
        assertNotNull(deletion);
        assertNull(game.analysis().getByNode(leaf));
        assertSame(siblingResult, game.analysis().getByNode(sibling));
        PositionAnalysis lateResult = result("late");
        game.analysis().putByNode(parent, lateResult);
        assertTrue(game.undoLeafDeletion(deletion));
        assertEquals("same-id", game.id());
        assertEquals(100, game.createdAt());
        assertEquals(leaf, game.record().currentNodeId());
        assertEquals(7, game.record().timeSeconds(1));
        assertEquals("last", game.record().comment(1));
        assertEquals(Long.valueOf(leaf), game.record().node(parent).mainChildId());
        assertEquals(Long.valueOf(leaf), game.record().selectedChildId(parent));
        assertSame(leafResult, game.analysis().getByNode(leaf));
        assertSame(siblingResult, game.analysis().getByNode(sibling));
        assertSame(lateResult, game.analysis().getByNode(parent));
        assertFalse(game.undoLeafDeletion(deletion));
    }

    @Test public void undoCannotOverwriteAnEditOrAnotherGame() {
        GameSession game = new GameSession(record(), () -> "id", () -> 100L);
        GameSession.LeafDeletion deletion = game.deleteCurrentLeaf();
        game.record().playVariation(77);
        assertFalse(game.undoLeafDeletion(deletion));
        deletion = game.deleteCurrentLeaf();
        game.record().setPlayerNames("edited", "W");
        assertFalse(game.undoLeafDeletion(deletion));
        game.record().playVariation(88);
        deletion = game.deleteCurrentLeaf();
        game.startNew(record(), true);
        assertFalse(game.undoLeafDeletion(deletion));
    }
}
