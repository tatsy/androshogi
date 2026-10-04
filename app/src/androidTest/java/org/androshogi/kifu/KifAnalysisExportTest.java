package org.androshogi.kifu;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.androshogi.engine.EngineInfo;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;
import org.androshogi.shogi.Board;
import org.androshogi.shogi.Move;
import org.androshogi.shogi.Shogi;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class KifAnalysisExportTest {
    @Test public void resultIsAfterItsPositionAndFollowsSelectedVariation() throws Exception {
        Board board = new Board(Shogi.STARTING_SFEN);
        try {
            int first = Move.fromUSI(board, "7g7f");
            board.push(first);
            String afterFirst = board.getSFEN();
            int second = Move.fromUSI(board, "3c3d");
            int variation = Move.fromUSI(board, "8c8d");
            GameRecord record = new GameRecord(Shogi.STARTING_SFEN, Arrays.asList(first, second),
                    Arrays.asList(1, 2), Arrays.asList("元コメント", "本譜"), "先手", "後手");
            GameAnalysis analysis = new GameAnalysis(record);
            analysis.put(0, PositionAnalysis.from(Shogi.STARTING_SFEN,
                    Collections.singletonList(EngineInfo.parse("info depth 10 score cp 50 pv 7g7f 3c3d")), "7g7f"));
            analysis.put(1, PositionAnalysis.from(afterFirst,
                    Arrays.asList(EngineInfo.parse("info multipv 2 score cp 80 pv 8c8d 2g2f"),
                            EngineInfo.parse("info depth 18 multipv 1 score cp -120 pv 3c3d 2g2f")), "3c3d"));
            String plain = KifWriter.write(record, 0);
            String exported = KifWriter.write(record, 0, analysis);
            assertFalse(plain.contains("AndroShogi解析"));
            assertTrue(exported.contains("*元コメント\n*[AndroShogi解析] 1手目後"));
            assertTrue(exported.contains("*候補1：評価値 +120 / 深さ 18\n*読み筋1：△３四歩 ▲２六歩"));
            assertTrue(exported.indexOf("候補1：評価値 +120") < exported.indexOf("候補2：評価値 -80"));
            assertTrue(exported.indexOf("開始局面") < exported.indexOf("   1 "));
            assertEquals("元コメント", record.comment(0));
            assertEquals(2, analysis.count());
            assertEquals(afterFirst, analysis.get(1).sfen());
            KifParser parser = KifParser.parse(exported);
            assertTrue(parser.getComments().get(0).contains("読み筋1：△３四歩"));
            GameRecord imported = new GameRecord(parser.getSFEN(), parser.getMoves(), parser.getTimes(),
                    parser.getComments(), parser.blackName(), parser.whiteName());
            GameAnalysis repeated = new GameAnalysis(imported);
            repeated.put(1, analysis.get(1));
            String again = KifWriter.write(imported, 0, repeated);
            // Re-imported analysis is ordinary text: preserve it and append the current result.
            assertEquals(2, again.split("1手目後", -1).length - 1);
            assertTrue(again.contains("*" + imported.comment(0).replace("\n", "\n*") + "\n"
                    + "*[AndroShogi解析] 1手目後"));
            assertEquals(parser.getComments().get(0), imported.comment(0));
            assertEquals(analysis.get(1), repeated.get(1));
            assertTrue(KifWriter.write(imported, 0).contains("*読み筋1：△３四歩"));
            assertEquals(exported, new String(KifTextCodec.encode(exported, KifTextCodec.SHIFT_JIS), KifTextCodec.SHIFT_JIS));

            board.push(second);
            analysis.put(2, PositionAnalysis.from(board.getSFEN(),
                    Collections.singletonList(EngineInfo.parse("info score cp 999")), null));
            record.selectNode(record.addVariation(record.nodeIdAtPly(1), variation, 0, "分岐"));
            String branched = KifWriter.write(record, 0, analysis);
            assertTrue(branched.contains("８四歩"));
            assertFalse(branched.contains("+999"));
        } finally {
            board.cleanup();
        }
    }
}
