package org.androshogi.kifu;

import static org.junit.Assert.*;

import org.androshogi.shogi.Shogi;
import org.junit.Test;

public class SfenParserTest {
    public static final String BOOK_POSITION =
            "ln1gk2nl/1r4g2/3pp2pp/5sP2/2S2Pp1P/PPp2SbR1/1S1PP4/2G6/LN2KG1NL w B2P3p 1";
    private static final String KINGS = "4k4/9/9/9/9/9/9/9/4K4";

    @Test public void acceptsStartingPositionAndBookDiagram() {
        assertEquals(Shogi.STARTING_SFEN, SfenParser.parse(Shogi.STARTING_SFEN));
        assertEquals(BOOK_POSITION, SfenParser.parse(BOOK_POSITION));
    }

    @Test public void acceptsPrefixesWhitespaceAndBomAndResetsTheMoveNumber() {
        assertEquals(BOOK_POSITION, SfenParser.parse(" \uFEFF position\tsfen\n"
                + BOOK_POSITION.replace(" w ", "\tw\t").replace(" 1", " 65") + "\n"));
        assertEquals(Shogi.STARTING_SFEN, SfenParser.parse("sfen " + Shogi.STARTING_SFEN));
    }

    @Test public void canonicalizesHandsAndAllowsHandicapPieceCounts() {
        assertEquals(KINGS + " b R2P3p 1", SfenParser.parse(KINGS + " b 3p2P1R 12"));
        assertEquals(KINGS + " w - 1", SfenParser.parse(KINGS + " w - 99"));
        assertEquals("4k4/9/4+P4/9/9/9/9/9/4K3L w 2GSb2p 1",
                SfenParser.parse("4k4/9/4+P4/9/9/9/9/9/4K3L w 2GSb2p 1"));
    }

    @Test public void detectsClipboardSfenWithoutMistakingKifHeadersForIt() {
        for (String s : new String[] {BOOK_POSITION, "\uFEFFsfen " + BOOK_POSITION,
                "position\tsfen " + BOOK_POSITION, "sfen", "9/9/9 b - 1",
                "Z8/9/9/9/9/9/9/9/4K4 b - 1", "9 b - 1"}) {
            assertTrue(s, SfenParser.looksLikeSfen(s));
        }
        for (String s : new String[] {"", "先手：B\n後手：W\n", "開始日時：2026/11/16 12:00:00\n",
                "# KIF\n手合割：平手\n", "手数----指手---------消費時間--\n"}) {
            assertFalse(s, SfenParser.looksLikeSfen(s));
        }
    }

    @Test public void rejectsMalformedRanksSymbolsAndPromotions() {
        for (String board : new String[] {"9/9", KINGS + "/9", KINGS.replace("/9/", "/8/"),
                KINGS.replace("/9/", "/91/"), KINGS.replace("/9/", "/45/"),
                KINGS.replace("/9/", "/4X4/"), KINGS.replace("/9/", "/4+4/"),
                KINGS.replace("/9/", "/4+G4/"), KINGS.replace("k", "+k"),
                KINGS.replace("/9/", "/4ſ4/"), KINGS.replace("/9/", "/4+14/")}) {
            rejects(board + " b - 1");
        }
    }

    @Test public void rejectsWrongFieldsTurnsMoveNumbersAndMoveHistory() {
        for (String suffix : new String[] {"", " b -", " black - 1", " b - 0", " b - -1",
                " b - +1", " b - 1.0", " b - 2147483648", " b - 1 moves 7g7f", " b - 1 extra"}) {
            rejects(KINGS + suffix);
        }
        rejects(null);
    }

    @Test public void rejectsMissingDuplicateAndAdjacentKings() {
        rejects(KINGS.replace("K", "1") + " b - 1");
        rejects(KINGS.replace("k", "1") + " b - 1");
        rejects(KINGS.replace("/9/", "/4K4/") + " b - 1");
        rejects(KINGS.replace("/9/", "/4k4/") + " b - 1");
        rejects(board("5ek", "5fK") + " b - 1");
        rejects(board("5ek", "4fK") + " w - 1");
    }

    @Test public void rejectsInvalidHandsAndExcessMaterialIncludingPromotedPieces() {
        for (String hand : new String[] {"K", "k", "+P", "0P", "01P", "19P", "999999999P",
                "3R", "P2P", "pp", "P-", "3", "1-", "ſ"}) {
            rejects(KINGS + " b " + hand + " 1");
        }
        rejects(board("5ak", "5iK", "1e+P") + " b 18P 1");
        rejects(board("5ak", "5iK", "1eR", "2e+r") + " b r 1");
    }

    @Test public void rejectsNifuAndImmobilePiecesForBothSides() {
        rejects(board("5ak", "5iK", "1eP", "1fP") + " b - 1");
        rejects(board("5ak", "5iK", "1ep", "1fp") + " b - 1");
        // A promoted pawn on the same file is permitted.
        SfenParser.parse(board("5ak", "5iK", "1eP", "1f+P") + " b - 1");
        for (String piece : new String[] {"1aP", "1aL", "1aN", "1bN", "1ip", "1il", "1in", "1hn"}) {
            rejects(board("5ak", "5iK", piece) + " b - 1");
        }
    }

    @Test public void rejectsCapturableOpponentKingButAllowsTheSideToMoveInCheck() {
        for (String attacker : new String[] {"5fP", "5hL", "4gN", "4fS", "5fG", "3gB", "1eR",
                "5f+P", "5f+L", "5f+N", "5f+S", "5f+B", "4f+R"}) {
            String position = board("5ek", "9iK", attacker);
            rejects(position + " b - 1");
            SfenParser.parse(position + " w - 1");
        }
        // Verify white's direction too, with a knight attacking the black king.
        String whiteAttack = board("5eK", "9ak", "4cn");
        rejects(whiteAttack + " w - 1");
        SfenParser.parse(whiteAttack + " b - 1");
    }

    @Test public void slidingAttacksRespectBlockers() {
        SfenParser.parse(board("5ek", "9iK", "1eR", "3eS") + " b - 1");
        SfenParser.parse(board("5ek", "9iK", "3gB", "4fN") + " b - 1");
        SfenParser.parse(board("5ek", "9iK", "5hL", "5fN") + " b - 1");
    }

    private static void rejects(String text) {
        assertThrows(String.valueOf(text), IllegalArgumentException.class, () -> SfenParser.parse(text));
    }

    /** Pieces such as "5ak" and "4f+S", expressed in USI squares. */
    private static String board(String... pieces) {
        String[][] squares = new String[9][9];
        for (String piece : pieces) {
            squares[piece.charAt(1) - 'a']['9' - piece.charAt(0)] = piece.substring(2);
        }
        StringBuilder sfen = new StringBuilder();
        for (int rank = 0; rank < 9; rank++) {
            if (rank > 0) sfen.append('/');
            int empty = 0;
            for (int file = 0; file < 9; file++) {
                if (squares[rank][file] == null) { empty++; continue; }
                if (empty > 0) sfen.append(empty);
                empty = 0;
                sfen.append(squares[rank][file]);
            }
            if (empty > 0) sfen.append(empty);
        }
        return sfen.toString();
    }
}
