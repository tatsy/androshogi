package org.androshogi.storage;

import org.androshogi.engine.EngineInfo;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GameJsonTest {
    private static final String START = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1";
    private static final String AFTER_TWO = "lnsgkgsnl/1r5b1/pppppp1pp/6p2/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL b - 3";

    private static SavedGame sampleGame() {
        GameRecord record = new GameRecord(START, Arrays.asList(1001, 1002, 1003),
                Arrays.asList(12, 0, 7), Arrays.asList("良い手", null, "疑問手"), "先手 太郎", "後手 花子");
        record.seek(2);
        GameAnalysis analysis = new GameAnalysis(record);
        analysis.put(0, PositionAnalysis.from(START, Arrays.asList(
                EngineInfo.parse("info depth 12 seldepth 20 multipv 1 time 1000 nodes 500000 nps 500000 hashfull 12 score cp 84 pv 7g7f 3c3d"),
                EngineInfo.parse("info depth 12 multipv 2 score cp 20 lowerbound pv 2g2f")), "7g7f"));
        analysis.put(2, PositionAnalysis.from(AFTER_TWO,
                Collections.singletonList(EngineInfo.parse("info depth 5 multipv 1 score mate + pv 2h2b+")), null));
        return new SavedGame("20260917-120000-abcd", 1700000000000L, 1700000001000L, record, analysis);
    }

    @Test
    public void roundTripsTheRecordAndItsPosition() throws JSONException {
        SavedGame back = GameJson.read(GameJson.write(sampleGame()));
        assertEquals("20260917-120000-abcd", back.id);
        assertEquals(1700000000000L, back.createdAt);
        assertEquals(1700000001000L, back.updatedAt);
        GameRecord r = back.record;
        assertEquals(START, r.startSfen());
        assertEquals("先手 太郎", r.blackName());
        assertEquals("後手 花子", r.whiteName());
        assertEquals(Arrays.asList(1001, 1002, 1003), r.moves());
        assertEquals(12, r.timeSeconds(0));
        assertEquals(0, r.timeSeconds(1));
        assertEquals("良い手", r.comment(0));
        assertNull(r.comment(1));
        assertEquals("疑問手", r.comment(2));
        assertEquals(2, r.currentPly());
    }

    @Test
    public void roundTripsTheAnalysisWithItsInfos() throws JSONException {
        SavedGame back = GameJson.read(GameJson.write(sampleGame()));
        GameAnalysis a = back.analysis;
        assertEquals(2, a.count());
        assertFalse(a.has(1));

        PositionAnalysis first = a.get(0);
        assertEquals(START, first.sfen());
        assertEquals(84, first.scoreForBlack());
        assertEquals("7g7f", first.bestMoveUsi());
        assertEquals(2, first.infos().size());
        EngineInfo best = first.infos().get(0);
        assertEquals(12, best.depth());
        assertEquals(20, best.seldepth());
        assertEquals(500000L, best.nodes());
        assertEquals(12, best.hashfull());
        assertEquals(Arrays.asList("7g7f", "3c3d"), best.pv());
        assertEquals(EngineInfo.Bound.LOWER, first.infos().get(1).bound());

        PositionAnalysis third = a.get(2);
        assertTrue(third.isMate());
        assertEquals(PositionAnalysis.MATE_SCORE, third.scoreForBlack());
        assertNull(third.bestMoveUsi());
        assertTrue(third.infos().get(0).isMateWithUnknownPly());
    }

    @Test
    public void summaryCountsMovesAndAnalyzedPliesWithoutTheDetails() throws JSONException {
        GameSummary s = GameJson.readSummary(GameJson.write(sampleGame()));
        assertEquals("20260917-120000-abcd", s.id);
        assertEquals("先手 太郎", s.blackName);
        assertEquals("後手 花子", s.whiteName);
        assertEquals(3, s.moveCount);
        assertEquals(2, s.analyzedCount);
        assertEquals(1700000000000L, s.createdAt);
        assertEquals(1700000001000L, s.updatedAt);
    }

    @Test
    public void rejectsAnotherFormatVersion() {
        try {
            GameJson.read("{\"format\": 99, \"id\": \"x\", \"startSfen\": \"" + START + "\", \"moves\": []}");
            fail("expected a JSONException");
        } catch (JSONException expected) {
            // A newer app wrote it; this version must not guess at its meaning.
        }
    }

    @Test
    public void toleratesMissingOptionalFields() throws JSONException {
        SavedGame back = GameJson.read("{\"format\": 1, \"id\": \"x\", \"startSfen\": \"" + START + "\", \"moves\": [1001]}");
        assertEquals(1, back.record.length());
        assertEquals(1, back.record.currentPly());
        assertEquals(0, back.record.timeSeconds(0));
        assertNull(back.record.comment(0));
        assertEquals(0, back.analysis.count());
    }

    @Test public void migratesV1RecordAndAnalysisAndResavesWithoutDataLoss() throws Exception {
        String json;
        try (java.io.InputStream input = getClass().getResourceAsStream("v1-game.json")) {
            org.junit.Assert.assertNotNull(input);
            json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        SavedGame legacy = GameJson.read(json);
        GameSummary summary = GameJson.readSummary(json);
        assertEquals(3, summary.moveCount);
        assertEquals(2, summary.analyzedCount);
        SavedGame back = GameJson.read(GameJson.write(legacy));
        assertEquals("legacy-v1", back.id);
        assertEquals(100, back.createdAt);
        assertEquals(200, back.updatedAt);
        assertEquals("先手", back.record.blackName());
        assertEquals("後手", back.record.whiteName());
        assertEquals("start b - 1", back.record.startSfen());
        assertEquals(Arrays.asList(11, 22, 33), back.record.moves());
        assertEquals(1, back.record.currentPly());
        assertEquals(5, back.record.timeSeconds(0));
        assertEquals(7, back.record.timeSeconds(1));
        assertEquals("初手", back.record.comment(0));
        assertNull(back.record.comment(1));
        assertEquals("終盤", back.record.comment(2));
        assertEquals(2, back.analysis.count());
        assertEquals(84, back.analysis.get(0).scoreForBlack());
        assertEquals(12, back.analysis.get(0).infos().get(0).depth());
        assertEquals(Arrays.asList("7g7f", "3c3d"), back.analysis.get(0).infos().get(0).pv());
        assertEquals("7g7f", back.analysis.get(0).bestMoveUsi());
        assertEquals("after-two b - 3", back.analysis.get(2).sfen());
        assertTrue(back.analysis.get(2).isMate());
        assertNull(back.analysis.get(2).bestMoveUsi());
    }

    @Test public void roundTripsUnselectedBranchesAndTheirResults() throws JSONException {
        SavedGame game = sampleGame();
        GameRecord r = game.record;
        long parent = r.nodeIdAtPly(1);
        long main = r.nodeIdAtPly(2);
        long branch = r.addVariation(parent, 99, 17, "別案");
        long leaf = r.addVariation(branch, 88, 9, "続き");
        r.selectNode(branch);
        game.analysis.put(2, PositionAnalysis.from("branch b - 3", Collections.singletonList(
                EngineInfo.parse("info depth 3 score cp -15 pv 2g2f")), "2g2f"));
        SavedGame back = GameJson.read(GameJson.write(game));
        assertEquals(Arrays.asList(1001, 99, 88), back.record.moves());
        assertEquals(branch, back.record.currentNodeId());
        assertEquals(2, back.record.currentPly());
        assertEquals(leaf, back.record.nodeIdAtPly(3));
        assertEquals(17, back.record.timeSeconds(1));
        assertEquals("別案", back.record.comment(1));
        assertEquals(Long.valueOf(main), back.record.node(parent).mainChildId());
        assertEquals(-15, back.analysis.get(2).scoreForBlack());
        assertEquals(3, back.analysis.count());
        back.record.selectNode(main);
        assertEquals(Arrays.asList(1001, 1002, 1003), back.record.moves());
        assertTrue(back.analysis.get(2).isMate());
        assertEquals("疑問手", back.record.comment(2));
    }

    @Test public void preservesIdCounterAfterDeletingTheHighestNodes() throws JSONException {
        SavedGame game = sampleGame();
        long deleted = game.record.nodeIdAtPly(3);
        game.record.truncate(1);
        game.analysis.truncate(1);
        SavedGame back = GameJson.read(GameJson.write(game));
        back.record.play(55);
        assertTrue(back.record.currentNodeId() > deleted);
        assertEquals(1, back.analysis.count());
    }

    @Test public void rejectsInvalidTopologyAndCursorForReadAndSummary() throws JSONException {
        String valid = GameJson.write(sampleGame());
        org.json.JSONObject invalid = new org.json.JSONObject(valid);
        invalid.put("nextNodeId", 1); // Would reuse a live or deleted ID.
        assertInvalidTree(invalid);

        invalid = new org.json.JSONObject(valid);
        invalid.getJSONArray("nodes").getJSONObject(0).put("selectedChildId", 3);
        assertInvalidTree(invalid); // The root cannot directly select its grandchild.

        invalid = new org.json.JSONObject(valid);
        invalid.getJSONArray("nodes").getJSONObject(1).put("id", 0);
        assertInvalidTree(invalid); // Duplicate root ID.

        invalid = new org.json.JSONObject(valid);
        invalid.getJSONArray("nodes").getJSONObject(3).put("parentId", 99);
        assertInvalidTree(invalid); // Missing parent.

        invalid = new org.json.JSONObject(valid);
        invalid.put("currentNodeId", 99);
        assertInvalidTree(invalid);

        // A disconnected self-cycle cannot become a usable record.
        invalid = new org.json.JSONObject(valid);
        org.json.JSONObject cycle = new org.json.JSONObject();
        cycle.put("id", 9).put("parentId", 9).put("move", 77)
                .put("mainChildId", 9).put("selectedChildId", 9);
        invalid.getJSONArray("nodes").put(cycle);
        invalid.put("nextNodeId", 10);
        assertInvalidTree(invalid);
    }

    private static void assertInvalidTree(org.json.JSONObject root) {
        org.junit.Assert.assertThrows(JSONException.class, () -> GameJson.read(root.toString()));
        org.junit.Assert.assertThrows(JSONException.class, () -> GameJson.readSummary(root.toString()));
    }

}
