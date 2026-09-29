package org.androshogi.shogi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Tests the JNI bridge to cshogi: playing moves, decoding move codes and
 * taking them back.
 *
 * <p>Runs on a device because it needs {@code libandroshogi.so}. The expected
 * values come from the USI strings themselves, so a change in cshogi's move
 * encoding or square numbering shows up here rather than as wrong arrows on
 * the board.
 */
@RunWith(AndroidJUnit4.class)
public class BoardTest {
    /** 相掛かりの出だし。最後の 1 手は歩打ち。 */
    private static final String[] OPENING = {
            "6i7h", "8c8d", "7g7f", "4a3b", "2g2f", "8d8e", "2f2e", "8e8f",
            "8g8f", "8b8f", "2e2d", "2c2d", "2h2d", "P*2c",
    };

    @Test
    public void newBoardIsTheStartingPosition() {
        Board board = new Board();
        try {
            assertEquals(Shogi.STARTING_SFEN, board.getSFEN());
            assertEquals(Shogi.BLACK, board.turn());
            assertEquals(Shogi.MOVE_NONE, board.peek());
        } finally {
            board.cleanup();
        }
    }

    @Test
    public void replaysAnOpening() {
        Board board = new Board();
        try {
            int expectedTurn = Shogi.BLACK;
            for (String usi : OPENING) {
                assertEquals("turn before " + usi, expectedTurn, board.turn());

                int code = Move.fromUSI(board, usi);
                assertNotEquals("could not decode " + usi, Shogi.MOVE_NONE, code);
                assertTrue(usi + " should be legal", board.isLegal(code));
                // The move code must survive a round trip through cshogi.
                assertEquals(usi, Move.toUSI(code));

                board.push(code);
                assertEquals("peek after " + usi, code, board.peek());
                expectedTurn = expectedTurn == Shogi.BLACK ? Shogi.WHITE : Shogi.BLACK;
            }
            // 14 moves played from the initial position, so the SFEN is at ply 15.
            assertTrue(board.getSFEN(), board.getSFEN().endsWith(" 15"));
        } finally {
            board.cleanup();
        }
    }

    @Test
    public void decodesABoardMove() {
        Board board = new Board();
        try {
            Move move = new Move(Move.fromUSI(board, "7g7f"));
            assertEquals(squareIndex("7g"), move.source());
            assertEquals(squareIndex("7f"), move.dest());
            assertEquals(Shogi.PieceType.PAWN, move.piece());
            assertFalse(move.isDrop());
            assertFalse(move.isProm());
        } finally {
            board.cleanup();
        }
    }

    @Test
    public void decodesADrop() {
        Board board = new Board();
        try {
            for (int i = 0; i < OPENING.length - 1; i++) {
                board.pushUSI(OPENING[i]);
            }

            String drop = OPENING[OPENING.length - 1];
            int code = Move.fromUSI(board, drop);
            Move move = new Move(code);
            assertTrue(move.isDrop());
            assertEquals(Shogi.PieceType.PAWN, move.piece());
            assertEquals(squareIndex("2c"), move.dest());
            assertEquals(drop, Move.toUSI(code));
        } finally {
            board.cleanup();
        }
    }

    @Test
    public void popUndoesEveryMove() {
        Board board = new Board();
        try {
            for (String usi : OPENING) {
                board.pushUSI(usi);
            }
            for (int i = OPENING.length - 1; i >= 0; i--) {
                assertEquals(OPENING[i], Move.toUSI(board.pop()));
            }

            assertEquals(Shogi.STARTING_SFEN, board.getSFEN());
            assertEquals(Shogi.MOVE_NONE, board.peek());
        } finally {
            board.cleanup();
        }
    }

    @Test
    public void refusesIllegalMoves() {
        Board board = new Board();
        try {
            // 歩は 2 マス進めない
            assertEquals(Shogi.MOVE_NONE, Move.fromUSI(board, "7g7e"));
            // 初手で後手の駒は動かせない
            assertEquals(Shogi.MOVE_NONE, Move.fromUSI(board, "3c3d"));
            // 盤上にない升
            assertEquals(Shogi.MOVE_NONE, Move.fromUSI(board, "7g7z"));
        } finally {
            board.cleanup();
        }
    }

    /** Index of a USI square such as "7f" in cshogi's square numbering. */
    private static int squareIndex(String usiSquare) {
        int index = Arrays.asList(Shogi.SQUARE_NAMES).indexOf(usiSquare);
        assertTrue("unknown square " + usiSquare, index >= 0);
        return index;
    }
}
