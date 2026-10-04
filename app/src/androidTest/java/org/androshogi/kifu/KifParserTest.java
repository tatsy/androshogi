package org.androshogi.kifu;

import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;


import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Instrumented tests for {@link KifParser}. They run on a device/emulator
 * because the parser drives {@link Board}, which is backed by JNI.
 */
@RunWith(AndroidJUnit4.class)
public class KifParserTest {

    /** 1-based square index used by Move: (rank - 1) + (file - 1) * 9. */
    private static int square(int file, int rank) {
        return (rank - 1) + (file - 1) * 9;
    }

    @Test
    public void parsesHeaderAndMoves() {
        String kif = String.join("\n",
                "# ---- Kifu for Windows ----",
                "開始日時：2024/12/01 10:00:00",
                "手合割：平手",
                "先手：先手太郎",
                "後手：後手花子",
                "手数----指手---------消費時間--",
                "   1 ７六歩(77)   ( 0:03/00:00:03)",
                "   2 ３四歩(33)   ( 0:02/00:00:02)",
                "   3 ２二角成(88) ( 0:05/00:00:08)",
                "   4 同　銀(31)   ( 0:01/00:00:03)",
                "   5 ４五角打     ( 0:10/00:00:18)",
                "   6 投了         ( 0:00/00:00:03)",
                "まで5手で先手の勝ち");

        KifParser parser = KifParser.parse(kif);

        assertEquals("先手太郎", parser.blackName());
        assertEquals("後手花子", parser.whiteName());
        assertEquals(Shogi.STARTING_SFEN, parser.getSFEN());
        assertEquals(Shogi.GameResult.BLACK_WIN, parser.getGameResult());

        List<Integer> moves = parser.getMoves();
        assertEquals(5, moves.size());

        assertEquals(square(7, 6), Move.dest(moves.get(0)));
        assertEquals(square(7, 7), Move.source(moves.get(0)));
        assertFalse(Move.isDrop(moves.get(0)));

        // ２二角成: promotion
        assertTrue(Move.isProm(moves.get(2)));
        assertEquals(square(2, 2), Move.dest(moves.get(2)));

        // 同　銀: same destination as the previous move
        assertEquals(Move.dest(moves.get(2)), Move.dest(moves.get(3)));
        assertEquals(square(3, 1), Move.source(moves.get(3)));

        // ４五角打: drop
        assertTrue(Move.isDrop(moves.get(4)));
        assertEquals(Shogi.PieceType.BISHOP.ordinal(), Move.piece(moves.get(4)));
        assertEquals(square(4, 5), Move.dest(moves.get(4)));
    }

    @Test public void ordinaryInitialCommentsSurviveBothEncodingsAndPlainExport() throws Exception {
        String kif = "手合割：平手\n*開始局面のコメント\n*続き\n"
                + "手数----指手---------消費時間--\n1 ７六歩(77)\n*初手のコメント\n";
        for (Charset charset : Arrays.asList(KifTextCodec.SHIFT_JIS, StandardCharsets.UTF_8)) {
            KifParser parser = KifParser.parse(new String(KifTextCodec.encode(kif, charset), charset));
            assertEquals("開始局面のコメント\n続き", parser.getStartComment());
            org.androshogi.game.GameRecord record = new org.androshogi.game.GameRecord(parser.getSFEN(),
                    parser.getMoves(), parser.getTimes(), parser.getComments(),
                    parser.blackName(), parser.whiteName(), parser.getStartComment());
            KifParser back = KifParser.parse(KifWriter.write(record, 0));
            assertEquals(parser.getStartComment(), back.getStartComment());
            assertEquals(parser.getComments(), back.getComments());
        }
    }

    @Test
    public void defaultsPlayerNamesWhenHeadersAreMissing() {
        KifParser parser = KifParser.parse("   1 ７六歩(77)");

        assertEquals("先手", parser.blackName());
        assertEquals("後手", parser.whiteName());
        assertEquals(1, parser.getMoves().size());
    }

    @Test
    public void usesHandicapStartPosition() {
        String kif = String.join("\n",
                "手合割：角落ち",
                "   1 ３四歩(33)",
                "   2 ７六歩(77)");

        KifParser parser = KifParser.parse(kif);

        assertEquals(KifFormat.HANDYCAP_SFENS.get("角落ち"), parser.getSFEN());
        assertEquals(2, parser.getMoves().size());
    }

    @Test
    public void acceptsPiecesInHandHeaders() {
        String kif = String.join("\n",
                "手合割：平手",
                "先手の持駒：歩三　角",
                "後手の持駒：なし",
                "   1 ７六歩(77)");

        // Without a board diagram the values have nothing to apply to, but the
        // header must not abort the parse.
        assertEquals(1, KifParser.parse(kif).getMoves().size());
    }

    @Test
    public void readsBoardDiagramAsStartPosition() {
        // Black to move with a gold in hand, white's king on ５一. (Dropping the
        // gold on ５二 would be mate; ４三 leaves the king a move.)
        String kif = String.join("\n",
                "手合割：その他　",
                "後手の持駒：なし",
                "  ９ ８ ７ ６ ５ ４ ３ ２ １",
                "+---------------------------+",
                "| ・ ・ ・ ・v玉 ・ ・ ・ ・|一",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|二",
                "| ・ ・ ・ ・ 金 ・ ・ ・ ・|三",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|四",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|五",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|六",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|七",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|八",
                "| ・ ・ ・ ・ 玉 ・ ・ ・ ・|九",
                "+---------------------------+",
                "先手の持駒：金",
                "手数----指手---------消費時間--",
                "   1 ４三金打",
                "   2 ４一玉(51)");

        KifParser parser = KifParser.parse(kif);

        assertEquals("4k4/9/4G4/9/9/9/9/9/4K4 b G 1", parser.getSFEN());
        assertEquals(2, parser.getMoves().size());
        assertEquals(square(4, 3), Move.dest(parser.getMoves().get(0)));
        assertTrue(Move.isDrop(parser.getMoves().get(0)));
    }

    @Test
    public void boardDiagramWithoutBothKingsIsRejectedBeforeNativeSet() {
        String kif = String.join("\n",
                "手合割：その他",
                "後手の持駒：なし",
                "  ９ ８ ７ ６ ５ ４ ３ ２ １",
                "+---------------------------+",
                "| ・ ・ ・ ・v玉 ・ ・ ・ ・|一",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|二",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|三",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|四",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|五",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|六",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|七",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|八",
                "| ・ ・ ・ ・ ・ ・ ・ ・ ・|九",
                "+---------------------------+",
                "先手の持駒：飛",
                "手数----指手---------消費時間--",
                "   1 ５二飛打");

        KifParser.KifParseException e = assertThrows(KifParser.KifParseException.class,
                () -> KifParser.parse(kif));
        assertEquals(-1, e.getLineNo());
        assertTrue(e.getMessage().contains("盤面図を解釈できません"));
    }

    @Test
    public void boardDiagramWrittenByKifWriterRoundTrips() {
        String sfen = "lnsgkgsnl/1r5b1/pppp1pppp/9/4p4/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 1";
        String kif = String.join("\n",
                KifWriter.boardDiagram(sfen),
                "   1 ８四歩(83)");

        KifParser parser = KifParser.parse(kif);

        assertEquals(sfen, parser.getSFEN());
        assertEquals(1, parser.getMoves().size());
    }

    @Test
    public void acceptsDrawResultLine() {
        String kif = String.join("\n",
                "   1 ７六歩(77)",
                "   2 ３四歩(33)",
                "   3 ２六歩(27)",
                "   4 ８四歩(83)",
                "まで4手で千日手");

        assertEquals(4, KifParser.parse(kif).getMoves().size());
    }

    @Test
    public void rejectsIllegalMoveWithLineNumber() {
        String kif = String.join("\n",
                "手合割：平手",
                "   1 ７六歩(77)",
                "   2 ７五歩(76)");   // black's pawn moved on white's turn

        KifParser.KifParseException e = assertThrows(KifParser.KifParseException.class,
                () -> KifParser.parse(kif));
        assertEquals(3, e.getLineNo());
    }

    @Test
    public void rejectsUnknownHandicap() {
        KifParser.KifParseException e = assertThrows(KifParser.KifParseException.class,
                () -> KifParser.parse("手合割：九枚落ち"));
        assertEquals(1, e.getLineNo());
    }

    @Test
    public void rejectsOtherHandicapWithoutBoardDiagram() {
        // その他 announces a diagram; without one the start position is unknown.
        KifParser.KifParseException e = assertThrows(KifParser.KifParseException.class,
                () -> KifParser.parse("先手：a\n手合割：その他\n   1 ７六歩(77)"));
        assertEquals(2, e.getLineNo());
    }

    @Test
    public void acceptsDouWithNoOrAsciiSpace() {
        for (String dou : new String[] {"同", "同 "}) {
            String kif = String.join("\n",
                    "手合割：平手",
                    "1 ７六歩(77)",
                    "2 ３四歩(33)",
                    "3 ２二角成(88)",
                    "4 " + dou + "銀(31)");

            KifParser parser = KifParser.parse(kif);
            assertEquals(4, parser.getMoves().size());
            assertEquals(Move.dest(parser.getMoves().get(2)), Move.dest(parser.getMoves().get(3)));
        }
    }

    @Test
    public void acceptsExplicitNonPromotion() {
        String kif = String.join("\n",
                "手合割：平手",
                "1 ７六歩(77)",
                "2 ３四歩(33)",
                "3 ２二角不成(88)");

        KifParser parser = KifParser.parse(kif);
        assertEquals(3, parser.getMoves().size());
        assertFalse(Move.isProm(parser.getMoves().get(2)));
    }

    @Test
    public void rejectsUnknownNumberedMoveWithLineNumber() {
        String kif = String.join("\n",
                "手合割：平手",
                "1 ７六歩(77)",
                "2 これは指し手ではない");

        KifParser.KifParseException e = assertThrows(KifParser.KifParseException.class,
                () -> KifParser.parse(kif));
        assertEquals(3, e.getLineNo());
    }

    @Test
    public void stopsBeforeVariationMoves() {
        String kif = String.join("\n",
                "手合割：平手",
                "1 ７六歩(77)",
                "2 ３四歩(33)",
                "変化：2手",
                "2 ８四歩(83)",
                "3 ２六歩(27)");

        KifParser parser = KifParser.parse(kif);
        assertEquals(2, parser.getMoves().size());
    }

    @Test
    public void recognizesNyugyokuWinAsTerminalLine() {
        String kif = String.join("\n",
                "手合割：平手",
                "1 ７六歩(77)",
                "2 入玉勝ち");

        KifParser parser = KifParser.parse(kif);
        assertEquals(1, parser.getMoves().size());
    }

    @Test
    public void ignoresUnrelatedText() {
        KifParser parser = KifParser.parse("hello world\n\n");

        assertTrue(parser.getMoves().isEmpty());
        assertEquals(Shogi.STARTING_SFEN, parser.getSFEN());
    }

    /** Kifu exported from 将棋ウォーズ (shogi-extend): comment lines between moves,
     *  extra headers such as 勝者, and a 詰み terminator. */
    @Test
    public void parsesShogiWarsExport() {
        String kifuText = "*詳細URL：https://www.shogi-extend.com/swars/battles/zkyuu-runner3494-20241204_162300\n" +
                "*ぴよ将棋：https://www.shogi-extend.com/swars/battles/zkyuu-runner3494-20241204_162300/piyo_shogi\n" +
                "*KENTO：https://www.shogi-extend.com/swars/battles/zkyuu-runner3494-20241204_162300/kento\n" +
                "先手：zkyuu 3級\n" +
                "後手：runner3494 2級\n" +
                "開始日時：2024/12/04 16:23:00\n" +
                "棋戦：将棋ウォーズ(10分切れ負け)\n" +
                "持ち時間：10分\n" +
                "結末：詰み\n" +
                "勝者：△\n" +
                "先手の戦法：相掛かり\n" +
                "後手の戦法：相掛かり\n" +
                "後手の囲い：中住まい\n" +
                "先手の手筋：たたきの歩, 桂頭の銀\n" +
                "後手の手筋：たたきの歩, 垂れ歩\n" +
                "先手の備考：居飛車, 相居飛車, 対居飛車, 持久戦, 長手数\n" +
                "後手の備考：居飛車, 相居飛車, 対居飛車, 持久戦, 長手数\n" +
                "先手の棋風：王道\n" +
                "後手の棋風：王道\n" +
                "手合割：平手\n" +
                "手数----指手---------消費時間--\n" +
                "   1 ２六歩(27)   (00:01/00:00:01)\n" +
                "*▲備考：居飛車\n" +
                "   2 ８四歩(83)   (00:01/00:00:01)\n" +
                "*△備考：居飛車\n" +
                "   3 ２五歩(26)   (00:01/00:00:02)\n" +
                "   4 ８五歩(84)   (00:01/00:00:02)\n" +
                "   5 ７八金(69)   (00:04/00:00:06)\n" +
                "   6 ３二金(41)   (00:01/00:00:03)\n" +
                "*△戦法：相掛かり\n" +
                "   7 ２四歩(25)   (00:01/00:00:07)\n" +
                "   8 ２四歩(23)   (00:01/00:00:04)\n" +
                "   9 ２四飛(28)   (00:04/00:00:11)\n" +
                "  10 １四歩(13)   (00:01/00:00:05)\n" +
                "  11 ２五飛(24)   (00:04/00:00:15)\n" +
                "  12 ２三歩打     (00:05/00:00:10)\n" +
                "  13 １六歩(17)   (00:03/00:00:18)\n" +
                "  14 ７二銀(71)   (00:05/00:00:15)\n" +
                "  15 ４八銀(39)   (00:01/00:00:19)\n" +
                "  16 ５二玉(51)   (00:01/00:00:16)\n" +
                "*△囲い：中住まい\n" +
                "  17 ４六歩(47)   (00:02/00:00:21)\n" +
                "  18 ６四歩(63)   (00:02/00:00:18)\n" +
                "  19 ９六歩(97)   (00:02/00:00:23)\n" +
                "  20 ９四歩(93)   (00:01/00:00:19)\n" +
                "  21 ４七銀(48)   (00:01/00:00:24)\n" +
                "  22 ６三銀(72)   (00:02/00:00:21)\n" +
                "  23 ３六歩(37)   (00:03/00:00:27)\n" +
                "  24 ３四歩(33)   (00:04/00:00:25)\n" +
                "  25 ７六歩(77)   (00:03/00:00:30)\n" +
                "  26 ７四歩(73)   (00:11/00:00:36)\n" +
                "  27 ３七桂(29)   (00:01/00:00:31)\n" +
                "  28 ７三桂(81)   (00:02/00:00:38)\n" +
                "  29 ４八金(49)   (00:09/00:00:40)\n" +
                "  30 ８六歩(85)   (00:24/00:01:02)\n" +
                "  31 ８六歩(87)   (00:04/00:00:44)\n" +
                "  32 ８六飛(82)   (00:02/00:01:04)\n" +
                "  33 ８七歩打     (00:03/00:00:47)\n" +
                "  34 ８一飛(86)   (00:01/00:01:05)\n" +
                "  35 ２二角成(88) (00:10/00:00:57)\n" +
                "  36 ２二銀(31)   (00:02/00:01:07)\n" +
                "  37 ８八銀(79)   (00:02/00:00:59)\n" +
                "  38 ３三銀(22)   (00:01/00:01:08)\n" +
                "  39 ７七銀(88)   (00:03/00:01:02)\n" +
                "  40 ６二金(61)   (00:23/00:01:31)\n" +
                "  41 ２九飛(25)   (00:07/00:01:09)\n" +
                "  42 ４二玉(52)   (00:04/00:01:35)\n" +
                "  43 ６八玉(59)   (00:02/00:01:11)\n" +
                "  44 ７五歩(74)   (00:17/00:01:52)\n" +
                "  45 ７五歩(76)   (00:03/00:01:14)\n" +
                "  46 ９五歩(94)   (00:01/00:01:53)\n" +
                "  47 ９五歩(96)   (00:02/00:01:16)\n" +
                "  48 ６五桂(73)   (00:01/00:01:54)\n" +
                "  49 ６六銀(77)   (00:04/00:01:20)\n" +
                "  50 ８六歩打     (00:05/00:01:59)\n" +
                "  51 ８六歩(87)   (00:04/00:01:24)\n" +
                "  52 ８六飛(81)   (00:01/00:02:00)\n" +
                "  53 ８七歩打     (00:02/00:01:26)\n" +
                "  54 ８一飛(86)   (00:18/00:02:18)\n" +
                "  55 ９四歩(95)   (00:28/00:01:54)\n" +
                "  56 ７六角打     (00:04/00:02:22)\n" +
                "  57 ９三歩成(94) (00:51/00:02:45)\n" +
                "  58 ８七角成(76) (00:11/00:02:33)\n" +
                "  59 ８二歩打     (00:03/00:02:48)\n" +
                "*▲手筋：たたきの歩\n" +
                "  60 ８六馬(87)   (00:38/00:03:11)\n" +
                "  61 ７七桂(89)   (00:12/00:03:00)\n" +
                "  62 ７一飛(81)   (00:12/00:03:23)\n" +
                "  63 ９五角打     (00:12/00:03:12)\n" +
                "  64 ７七桂成(65) (00:50/00:04:13)\n" +
                "  65 ７七銀(66)   (00:08/00:03:20)\n" +
                "  66 ９五馬(86)   (00:34/00:04:47)\n" +
                "  67 ９五香(99)   (00:08/00:03:28)\n" +
                "  68 ６五桂打     (00:05/00:04:52)\n" +
                "  69 ８四角打     (00:09/00:03:37)\n" +
                "  70 ７七桂成(65) (00:44/00:05:36)\n" +
                "  71 ７七金(78)   (00:02/00:03:39)\n" +
                "  72 ７三銀打     (00:02/00:05:38)\n" +
                "  73 ８三桂打     (00:11/00:03:50)\n" +
                "  74 ７二飛(71)   (00:16/00:05:54)\n" +
                "  75 ７三角成(84) (00:05/00:03:55)\n" +
                "  76 ７三飛(72)   (00:04/00:05:58)\n" +
                "  77 ８四銀打     (00:13/00:04:08)\n" +
                "  78 ７二飛(73)   (00:20/00:06:18)\n" +
                "  79 ８五桂打     (00:21/00:04:29)\n" +
                "  80 ７六歩打     (00:22/00:06:40)\n" +
                "*△手筋：たたきの歩\n" +
                "  81 ７六金(77)   (00:05/00:04:34)\n" +
                "  82 ６一金(62)   (00:17/00:06:57)\n" +
                "  83 ９一桂成(83) (00:02/00:04:36)\n" +
                "  84 ８七角打     (00:20/00:07:17)\n" +
                "  85 ７七金(76)   (00:05/00:04:41)\n" +
                "  86 ９六角成(87) (00:02/00:07:19)\n" +
                "  87 ７三桂成(85) (00:05/00:04:46)\n" +
                "  88 ５二飛(72)   (00:04/00:07:23)\n" +
                "  89 ６三成桂(73) (00:03/00:04:49)\n" +
                "  90 ６三馬(96)   (00:01/00:07:24)\n" +
                "  91 ８三と(93)   (00:06/00:04:55)\n" +
                "  92 ６五桂打     (00:05/00:07:29)\n" +
                "  93 ７八金(77)   (00:07/00:05:02)\n" +
                "  94 ９六馬(63)   (00:23/00:07:52)\n" +
                "  95 ６六銀打     (00:26/00:05:28)\n" +
                "*▲手筋：桂頭の銀\n" +
                "  96 ７六歩打     (00:17/00:08:09)\n" +
                "*△手筋：垂れ歩\n" +
                "  97 ６三香打     (00:39/00:06:07)\n" +
                "  98 ６三馬(96)   (00:15/00:08:24)\n" +
                "  99 ７三と(83)   (00:08/00:06:15)\n" +
                " 100 ９六馬(63)   (00:05/00:08:29)\n" +
                " 101 ７四歩(75)   (00:55/00:07:10)\n" +
                " 102 ８六角打     (00:12/00:08:41)\n" +
                " 103 ５八玉(68)   (00:07/00:07:17)\n" +
                " 104 ７七歩成(76) (00:04/00:08:45)\n" +
                " 105 ７九金(78)   (00:04/00:07:21)\n" +
                " 106 ６七と(77)   (00:06/00:08:51)\n" +
                " 107 ６七玉(58)   (00:06/00:07:27)\n" +
                " 108 ８五馬(96)   (00:03/00:08:54)\n" +
                " 109 ７八玉(67)   (00:03/00:07:30)\n" +
                " 110 ７七香打     (00:05/00:08:59)\n" +
                " 111 ６八玉(78)   (00:02/00:07:32)\n" +
                " 112 ７九香成(77) (00:02/00:09:01)\n" +
                " 113 ７九玉(68)   (00:09/00:07:41)\n" +
                " 114 ６八金打     (00:19/00:09:20)\n" +
                " 115 ８九玉(79)   (00:04/00:07:45)\n" +
                " 116 ６七馬(85)   (00:02/00:09:22)\n" +
                " 117 ９九玉(89)   (00:04/00:07:49)\n" +
                " 118 ６六馬(67)   (00:01/00:09:23)\n" +
                " 119 ８八香打     (00:05/00:07:54)\n" +
                " 120 ９七角成(86) (00:05/00:09:28)\n" +
                " 121 ９八歩打     (00:08/00:08:02)\n" +
                " 122 ８八馬(97)   (00:02/00:09:30)\n" +
                " 123 詰み\n" +
                "まで122手で後手の勝ち\n";

        KifParser parser = KifParser.parse(kifuText);

        assertEquals("zkyuu 3級", parser.blackName());
        assertEquals("runner3494 2級", parser.whiteName());
        assertEquals(122, parser.getMoves().size());
        assertEquals(Shogi.GameResult.WHITE_WIN, parser.getGameResult());
    }

    /** Kifu in 棋譜DB style: no leading spaces before the move number, single
     *  spaces between columns, and no まで〜 result line after 投了. */
    @Test
    public void parsesKifuDbExport() {
        String kifuText = "開始日時：2024/10/25 10:00:00\n" +
                "終了日時：2024/10/26 18:26:00\n" +
                "棋戦：竜王戦\n" +
                "場所：京都府京都市「総本山仁和寺」\n" +
                "持ち時間：８時間\n" +
                "消費時間：99▲467△471\n" +
                "手合割：平手\n" +
                "先手：藤井聡太 竜王\n" +
                "後手：佐々木勇気 八段\n" +
                "戦型：角交換型振り飛車\n" +
                "手数----指手---------消費時間--\n" +
                "1 ２六歩(27) (00:00/00:00:00)\n" +
                "2 ３四歩(33) (00:00/00:00:00)\n" +
                "3 ７六歩(77) (00:00/00:00:00)\n" +
                "4 ９四歩(93) (00:00/00:00:00)\n" +
                "5 ２五歩(26) (00:00/00:00:00)\n" +
                "6 ９五歩(94) (00:00/00:00:00)\n" +
                "7 ６八玉(59) (00:00/00:00:00)\n" +
                "8 ８八角成(22) (00:00/00:00:00)\n" +
                "9 同　銀(79) (00:00/00:00:00)\n" +
                "10 ２二銀(31) (00:00/00:00:00)\n" +
                "11 ４八銀(39) (00:00/00:00:00)\n" +
                "12 ３三銀(22) (00:00/00:00:00)\n" +
                "13 ５八金(49) (00:00/00:00:00)\n" +
                "14 ２二飛(82) (00:00/00:00:00)\n" +
                "15 ７八玉(68) (00:00/00:00:00)\n" +
                "16 ６二玉(51) (00:00/00:00:00)\n" +
                "17 ４六歩(47) (00:00/00:00:00)\n" +
                "18 ７二玉(62) (00:00/00:00:00)\n" +
                "19 ３六歩(37) (00:00/00:00:00)\n" +
                "20 ８二玉(72) (00:00/00:00:00)\n" +
                "21 ４七銀(48) (00:00/00:00:00)\n" +
                "22 ７二銀(71) (00:00/00:00:00)\n" +
                "23 ８六歩(87) (00:00/00:00:00)\n" +
                "24 ５二金(41) (00:00/00:00:00)\n" +
                "25 ３七桂(29) (00:00/00:00:00)\n" +
                "26 ６四歩(63) (00:00/00:00:00)\n" +
                "27 ８七銀(88) (00:00/00:00:00)\n" +
                "28 ８四歩(83) (00:00/00:00:00)\n" +
                "29 ２九飛(28) (00:00/00:00:00)\n" +
                "30 ８三銀(72) (00:00/00:00:00)\n" +
                "31 ６八玉(78) (00:00/00:00:00)\n" +
                "32 ４四歩(43) (00:00/00:00:00)\n" +
                "33 ７八金(69) (00:00/00:00:00)\n" +
                "34 ７二金(61) (00:00/00:00:00)\n" +
                "35 ７七桂(89) (00:00/00:00:00)\n" +
                "36 ６二金(52) (00:00/00:00:00)\n" +
                "37 ５六歩(57) (00:00/00:00:00)\n" +
                "38 ７四歩(73) (00:00/00:00:00)\n" +
                "39 ６六角打 (00:00/00:00:00)\n" +
                "40 ５四角打 (00:00/00:00:00)\n" +
                "41 ４八金(58) (00:00/00:00:00)\n" +
                "42 ７三金(62) (00:00/00:00:00)\n" +
                "43 ５五歩(56) (00:00/00:00:00)\n" +
                "44 ６三角(54) (00:00/00:00:00)\n" +
                "45 ５六銀(47) (00:00/00:00:00)\n" +
                "46 ３六角(63) (00:00/00:00:00)\n" +
                "47 ８五歩(86) (00:00/00:00:00)\n" +
                "48 同　歩(84) (00:00/00:00:00)\n" +
                "49 同　桂(77) (00:00/00:00:00)\n" +
                "50 ８四金(73) (00:00/00:00:00)\n" +
                "51 ８六銀(87) (00:00/00:00:00)\n" +
                "52 ６三角(36) (00:00/00:00:00)\n" +
                "53 ８九飛(29) (00:00/00:00:00)\n" +
                "54 ２七角成(63) (00:00/00:00:00)\n" +
                "55 ９六歩(97) (00:00/00:00:00)\n" +
                "56 同　歩(95) (00:00/00:00:00)\n" +
                "57 ９三歩打 (00:00/00:00:00)\n" +
                "58 ６三馬(27) (00:00/00:00:00)\n" +
                "59 ９六香(99) (00:00/00:00:00)\n" +
                "60 ９四歩打 (00:00/00:00:00)\n" +
                "61 ４五歩(46) (00:00/00:00:00)\n" +
                "62 ７五歩(74) (00:00/00:00:00)\n" +
                "63 ４四歩(45) (00:00/00:00:00)\n" +
                "64 ４二飛(22) (00:00/00:00:00)\n" +
                "65 ９二歩成(93) (00:00/00:00:00)\n" +
                "66 同　銀(83) (00:00/00:00:00)\n" +
                "67 ７五角(66) (00:00/00:00:00)\n" +
                "68 同　金(84) (00:00/00:00:00)\n" +
                "69 同　銀(86) (00:00/00:00:00)\n" +
                "70 ８四歩打 (00:00/00:00:00)\n" +
                "71 ７四金打 (00:00/00:00:00)\n" +
                "72 同　馬(63) (00:00/00:00:00)\n" +
                "73 同　銀(75) (00:00/00:00:00)\n" +
                "74 ８五歩(84) (00:00/00:00:00)\n" +
                "75 ５四歩(55) (00:00/00:00:00)\n" +
                "76 ８六桂打 (00:00/00:00:00)\n" +
                "77 ５三歩成(54) (00:00/00:00:00)\n" +
                "78 ３五角打 (00:00/00:00:00)\n" +
                "79 ５七角打 (00:00/00:00:00)\n" +
                "80 同　角成(35) (00:00/00:00:00)\n" +
                "81 同　金(48) (00:00/00:00:00)\n" +
                "82 ５五歩打 (00:00/00:00:00)\n" +
                "83 同　銀(56) (00:00/00:00:00)\n" +
                "84 ７八桂成(86) (00:00/00:00:00)\n" +
                "85 同　玉(68) (00:00/00:00:00)\n" +
                "86 ８六角打 (00:00/00:00:00)\n" +
                "87 同　飛(89) (00:00/00:00:00)\n" +
                "88 同　歩(85) (00:00/00:00:00)\n" +
                "89 ４二と(53) (00:00/00:00:00)\n" +
                "90 ８四金打 (00:00/00:00:00)\n" +
                "91 ４一角打 (00:00/00:00:00)\n" +
                "92 ９七飛打 (00:00/00:00:00)\n" +
                "93 ５四角打 (00:00/00:00:00)\n" +
                "94 ８三銀(92) (00:00/00:00:00)\n" +
                "95 ７五桂打 (00:00/00:00:00)\n" +
                "96 ９八飛成(97) (00:00/00:00:00)\n" +
                "97 ７七玉(78) (00:00/00:00:00)\n" +
                "98 ７五金(84) (00:00/00:00:00)\n" +
                "99 ７二角成(54) (00:00/00:00:00)\n" +
                "100 投了 (00:00/00:00:00)";

        KifParser parser = KifParser.parse(kifuText);

        assertEquals("藤井聡太 竜王", parser.blackName());
        assertEquals("佐々木勇気 八段", parser.whiteName());
        assertEquals(99, parser.getMoves().size());
        assertNull(parser.getGameResult());
    }

    @Test
    public void exportedKifParsesAfterUtf8AndShiftJisFileRoundTrip() throws Exception {
        String original = KifWriter.format(Shogi.STARTING_SFEN, "山田太郎", "佐藤花子", 0,
                Arrays.asList(
                        new KifWriter.Ply(Shogi.PieceType.PAWN,
                                square(7, 7), square(7, 6), false, 3, "備考"),
                        new KifWriter.Ply(Shogi.PieceType.PAWN,
                                square(3, 3), square(3, 4), false, 2, null)));
        KifParser expected = KifParser.parse(original);

        for (Charset charset : new Charset[] {StandardCharsets.UTF_8, KifTextCodec.SHIFT_JIS}) {
            byte[] bytes = KifTextCodec.encode(original, charset);
            KifParser actual = KifParser.parse(KifTextCodec.decode(bytes));
            assertEquals(expected.blackName(), actual.blackName());
            assertEquals(expected.whiteName(), actual.whiteName());
            assertEquals(expected.getSFEN(), actual.getSFEN());
            assertEquals(expected.getMoves(), actual.getMoves());
            assertEquals(expected.getTimes(), actual.getTimes());
            assertEquals(expected.getComments(), actual.getComments());
        }
    }

}
