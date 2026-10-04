package org.androshogi.kifu;

import org.androshogi.engine.EngineInfo;
import org.androshogi.shogi.Shogi;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class KifAnalysisCommentTest {
    @Test public void scoresAndBoundsUseBlackPerspectiveAndKeepCandidateIndices() {
        String text = KifAnalysisComment.format(Shogi.STARTING_SFEN.replace(" b ", " w "), 1,
                Arrays.asList(EngineInfo.parse("info depth 18 multipv 1 score cp -120 lowerbound"),
                        EngineInfo.parse("info depth 17 multipv 2 score cp 80 upperbound")),
                Arrays.asList("△３四歩 ▲２六歩", "△８四歩 ▲２六歩"));
        assertEquals("[AndroShogi解析] 1手目後（評価値は先手基準）\n"
                + "候補1：評価値 +120（上限） / 深さ 18\n読み筋1：△３四歩 ▲２六歩\n"
                + "候補2：評価値 -80（下限） / 深さ 17\n読み筋2：△８四歩 ▲２六歩", text);
    }

    @Test public void mateIsNotExportedAsArtificialCentipawnScore() {
        String text = KifAnalysisComment.format(Shogi.STARTING_SFEN.replace(" b ", " w "), 5,
                Arrays.asList(EngineInfo.parse("info score mate -5"), EngineInfo.parse("info score mate +")),
                Arrays.asList("", ""));
        assertTrue(text.contains("評価値 +詰5"));
        assertTrue(text.contains("評価値 -詰"));
        assertFalse(text.contains("30000"));
        assertFalse(text.contains("2147483647"));
    }

    @Test public void missingFieldsAreOmittedOrShownAsUnknown() {
        assertNull(KifAnalysisComment.format(Shogi.STARTING_SFEN, 0,
                Collections.emptyList(), Collections.emptyList()));
        String text = KifAnalysisComment.format(Shogi.STARTING_SFEN, 0,
                Collections.singletonList(EngineInfo.parse("info nodes 10")), Collections.singletonList(""));
        assertTrue(text.contains("開始局面"));
        assertTrue(text.contains("評価値 不明"));
        assertFalse(text.contains("深さ"));
        assertFalse(text.contains("読み筋"));
    }

    @Test public void startingPositionCommentPrecedesTheFirstMoveAndShiftJisCanEncodeIt() throws Exception {
        String comment = KifAnalysisComment.format(Shogi.STARTING_SFEN, 0,
                Collections.singletonList(EngineInfo.parse("info depth 18 score cp 120")),
                Collections.singletonList("▲７六歩 △３四歩"));
        String text = KifWriter.format(Shogi.STARTING_SFEN, "先手", "後手", 0,
                Collections.singletonList(new KifWriter.Ply(Shogi.PieceType.PAWN, 60, 59, false, 0, "元コメント")), comment);
        assertTrue(text.indexOf("*[AndroShogi解析]") < text.indexOf("   1 "));
        assertTrue(text.contains("*読み筋1：▲７六歩 △３四歩\n"));
        assertTrue(text.endsWith("*元コメント\n"));
        assertEquals(text, new String(KifTextCodec.encode(text, KifTextCodec.SHIFT_JIS), KifTextCodec.SHIFT_JIS));
    }
}
