package org.androshogi.kifu;

import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The KIF text on hand-made games. Squares are given in USI form ("7g" is ７七).
 */
public class KifWriterTest {
    private static int sq(String usi) {
        int column = usi.charAt(0) - '1';
        int row = usi.charAt(1) - 'a';
        return column * 9 + row;
    }

    private static KifWriter.Ply ply(Shogi.PieceType piece, String from, String to, boolean promote,
                                     int seconds, String comment) {
        return new KifWriter.Ply(piece, from == null ? KifWriter.NO_SQUARE : sq(from), sq(to),
                promote, seconds, comment);
    }

    @Test
    public void moveTextIsSquarePieceAndSource() {
        assertEquals("７六歩(77)", KifWriter.moveText(Shogi.PieceType.PAWN, sq("7g"), sq("7f"), false, KifWriter.NO_SQUARE));
        assertEquals("２二角成(88)", KifWriter.moveText(Shogi.PieceType.BISHOP, sq("8h"), sq("2b"), true, KifWriter.NO_SQUARE));
        assertEquals("５五角打", KifWriter.moveText(Shogi.PieceType.BISHOP, KifWriter.NO_SQUARE, sq("5e"), false, KifWriter.NO_SQUARE));
        assertEquals("８二龍(92)", KifWriter.moveText(Shogi.PieceType.PROM_ROOK, sq("9b"), sq("8b"), false, KifWriter.NO_SQUARE));
    }

    @Test
    public void sameSquareAsThePreviousMoveIsDouWithFullWidthSpace() {
        assertEquals("同　歩(23)", KifWriter.moveText(Shogi.PieceType.PAWN, sq("2c"), sq("2d"), false, sq("2d")));
        assertEquals("同　銀成(33)", KifWriter.moveText(Shogi.PieceType.SILVER, sq("3c"), sq("2d"), true, sq("2d")));
    }

    @Test
    public void formatWritesHeaderMovesTimesAndComments() {
        List<KifWriter.Ply> plies = Arrays.asList(
                ply(Shogi.PieceType.PAWN, "7g", "7f", false, 3, null),
                ply(Shogi.PieceType.PAWN, "3c", "3d", false, 65, "定跡\n二行目"),
                ply(Shogi.PieceType.BISHOP, "8h", "2b", true, 0, null),
                ply(Shogi.PieceType.SILVER, "3a", "2b", false, 7, null),
                ply(Shogi.PieceType.BISHOP, null, "5e", false, 3600, ""));
        String kif = KifWriter.format(Shogi.STARTING_SFEN, "先手太郎", "後手花子", 0, plies);
        String expected = "# ---- AndroShogi 棋譜ファイル ----\n"
                + "手合割：平手\n"
                + "先手：先手太郎\n"
                + "後手：後手花子\n"
                + "手数----指手---------消費時間--\n"
                + "   1 ７六歩(77)     ( 0:03/00:00:03)\n"
                + "   2 ３四歩(33)     ( 1:05/00:01:05)\n"
                + "*定跡\n"
                + "*二行目\n"
                + "   3 ２二角成(88)   ( 0:00/00:00:03)\n"
                + "   4 同　銀(31)     ( 0:07/00:01:12)\n"
                + "   5 ５五角打       (60:00/01:00:03)\n";
        assertEquals(expected, kif);
    }

    @Test
    public void startTimeIsWrittenWhenKnown() {
        // 2024-01-02 03:04:05 JST
        long createdAt = 1704132245000L;
        String kif = KifWriter.format(Shogi.STARTING_SFEN, "a", "b", createdAt, Collections.emptyList());
        String expectedDate = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.JAPAN)
                .format(new java.util.Date(createdAt));
        assertEquals("# ---- AndroShogi 棋譜ファイル ----\n"
                + "開始日時：" + expectedDate + "\n"
                + "手合割：平手\n"
                + "先手：a\n"
                + "後手：b\n"
                + "手数----指手---------消費時間--\n", kif);
    }

    @Test
    public void handicapsAreNamedWhateverTheMoveCounter() {
        assertEquals("平手", KifWriter.handicapName(Shogi.STARTING_SFEN));
        assertEquals("平手", KifWriter.handicapName("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 5"));
        assertEquals("二枚落ち", KifWriter.handicapName("lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1"));
        assertNull(KifWriter.handicapName("lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1"));
    }

    @Test
    public void whiteMovesFirstInAHandicapGame() {
        List<KifWriter.Ply> plies = Arrays.asList(
                ply(Shogi.PieceType.PAWN, "3c", "3d", false, 10, null),
                ply(Shogi.PieceType.PAWN, "7g", "7f", false, 20, null),
                ply(Shogi.PieceType.PAWN, "8c", "8d", false, 5, null));
        String kif = KifWriter.format("lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1", "下手", "上手", 0, plies);
        String moves = kif.substring(kif.indexOf("手数"));
        assertEquals("手数----指手---------消費時間--\n"
                + "   1 ３四歩(33)     ( 0:10/00:00:10)\n"
                + "   2 ７六歩(77)     ( 0:20/00:00:20)\n"
                + "   3 ８四歩(83)     ( 0:05/00:00:15)\n", moves);
    }

    @Test
    public void otherPositionsAreWrittenAsABoardDiagram() {
        // A mate problem style position: white king only, black has pieces in hand, white to move.
        String sfen = "4k4/9/4+P4/9/9/9/9/9/8L w 2GSb2p 1";
        String expected = "後手の持駒：角　歩二\n"
                + "  ９ ８ ７ ６ ５ ４ ３ ２ １\n"
                + "+---------------------------+\n"
                + "| ・ ・ ・ ・v玉 ・ ・ ・ ・|一\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|二\n"
                + "| ・ ・ ・ ・ と ・ ・ ・ ・|三\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|四\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|五\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|六\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|七\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ ・|八\n"
                + "| ・ ・ ・ ・ ・ ・ ・ ・ 香|九\n"
                + "+---------------------------+\n"
                + "先手の持駒：金二　銀\n"
                + "後手番\n";
        assertEquals(expected, KifWriter.boardDiagram(sfen));
        String kif = KifWriter.format(sfen, "a", "b", 0, Collections.emptyList());
        assertEquals(true, kif.contains(expected + "先手：a\n"));
    }

    @Test
    public void largeHandCountsUseTens() {
        assertEquals("後手の持駒：なし\n", KifWriter.boardDiagram("9/9/9/9/9/9/9/9/9 b 18P 1").split("  ９")[0]);
        String black = KifWriter.boardDiagram("9/9/9/9/9/9/9/9/9 b 18P 1");
        assertEquals(true, black.contains("先手の持駒：歩十八\n"));
        assertEquals(true, KifWriter.boardDiagram("9/9/9/9/9/9/9/9/9 b 10p 1").contains("後手の持駒：歩十\n"));
    }
}
