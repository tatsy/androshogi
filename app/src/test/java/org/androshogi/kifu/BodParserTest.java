package org.androshogi.kifu;

import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The diagram reader against the writer: a position must come back as the SFEN it left. */
public class BodParserTest {
    private static List<String> rowsOf(String bod) {
        List<String> rows = new ArrayList<>();
        for (String line : bod.split("\n")) {
            if (BodParser.isBoardRow(line)) {
                rows.add(line);
            }
        }
        return rows;
    }

    @Test
    public void recognisesBoardRowsOnly() {
        assertTrue(BodParser.isBoardRow("|v香v桂 ・ ・ ・ ・ ・v桂v香|一"));
        assertFalse(BodParser.isBoardRow("+---------------------------+"));
        assertFalse(BodParser.isBoardRow("後手の持駒：なし"));
    }

    @Test
    public void startingPositionRoundTrips() {
        String bod = KifWriter.boardDiagram(Shogi.STARTING_SFEN);
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        assertEquals(Shogi.STARTING_SFEN, BodParser.toSfen(rowsOf(bod), none, none, false));
    }

    @Test
    public void handsPromotedPiecesAndWhiteToMoveRoundTrip() {
        String sfen = "4k4/9/4+P4/9/9/9/9/9/4K3L w 2GSb2p 1";
        String bod = KifWriter.boardDiagram(sfen);
        Map<Shogi.PieceType, Integer> black = new HashMap<>();
        black.put(Shogi.PieceType.GOLD, 2);
        black.put(Shogi.PieceType.SILVER, 1);
        Map<Shogi.PieceType, Integer> white = new HashMap<>();
        white.put(Shogi.PieceType.BISHOP, 1);
        white.put(Shogi.PieceType.PAWN, 2);
        assertEquals(sfen, BodParser.toSfen(rowsOf(bod), black, white, true));
    }

    @Test
    public void kingWrittenAsOuIsAccepted() {
        List<String> rows = rowsOf(KifWriter.boardDiagram("4k4/9/9/9/9/9/9/9/4K4 b - 1"));
        rows.set(0, rows.get(0).replace("玉", "王"));
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        assertEquals("4k4/9/9/9/9/9/9/9/4K4 b - 1", BodParser.toSfen(rows, none, none, false));
    }

    private static List<String> minimalTwoKingRows() {
        return rowsOf(KifWriter.boardDiagram("4k4/9/9/9/9/9/9/9/4K4 b - 1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingBlackKingIsRejected() {
        List<String> rows = minimalTwoKingRows();
        rows.set(8, rows.get(8).replace("玉", "・"));
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        BodParser.toSfen(rows, none, none, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingWhiteKingIsRejected() {
        List<String> rows = minimalTwoKingRows();
        rows.set(0, rows.get(0).replace("玉", "・"));
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        BodParser.toSfen(rows, none, none, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateBlackKingIsRejected() {
        List<String> rows = minimalTwoKingRows();
        rows.set(4, rows.get(4).replaceFirst(" ・", " 玉"));
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        BodParser.toSfen(rows, none, none, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateWhiteKingIsRejected() {
        List<String> rows = minimalTwoKingRows();
        rows.set(4, rows.get(4).replaceFirst(" ・", "v玉"));
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        BodParser.toSfen(rows, none, none, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongRowCountIsRejected() {
        Map<Shogi.PieceType, Integer> none = Collections.emptyMap();
        BodParser.toSfen(Collections.singletonList("|v玉 ・ ・ ・ ・ ・ ・ ・ ・|一"), none, none, false);
    }
}
