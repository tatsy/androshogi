package org.androshogi.storage;

import org.androshogi.engine.EngineInfo;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameNode;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lossless app storage, independent of KIF export and the native board.
 * Format 2 stores the whole position tree and results keyed by node ID.
 * The format 1 linear records written by v0.1.0 are migrated on read.
 */
public final class GameJson {
    public static final int FORMAT = 2;

    private GameJson() {}

    public static String write(SavedGame game) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("id", game.id);
        root.put("createdAt", game.createdAt);
        root.put("updatedAt", game.updatedAt);
        GameRecord record = game.record;
        root.put("black", record.blackName());
        root.put("white", record.whiteName());
        root.put("startSfen", record.startSfen());
        root.put("currentNodeId", record.currentNodeId());
        root.put("nextNodeId", record.nextNodeId());
        JSONArray nodes = new JSONArray();
        for (GameNode node : record.nodes()) {
            JSONObject entry = new JSONObject();
            entry.put("id", node.id());
            entry.put("parentId", nullable(node.parentId()));
            entry.put("move", node.move());
            entry.put("seconds", node.timeSeconds());
            entry.put("comment", nullable(node.comment()));
            entry.put("mainChildId", nullable(node.mainChildId()));
            entry.put("selectedChildId", nullable(record.selectedChildId(node.id())));
            nodes.put(entry);
        }
        root.put("nodes", nodes);

        JSONArray analysis = new JSONArray();
        for (Map.Entry<Long, PositionAnalysis> result : game.analysis.entries().entrySet()) {
            PositionAnalysis a = result.getValue();
            JSONObject entry = new JSONObject();
            entry.put("nodeId", result.getKey());
            entry.put("sfen", a.sfen());
            entry.put("bestMove", nullable(a.bestMoveUsi()));
            JSONArray infos = new JSONArray();
            for (EngineInfo info : a.infos()) infos.put(info.toLine());
            entry.put("infos", infos);
            analysis.put(entry);
        }
        root.put("analysis", analysis);
        return root.toString(2);
    }

    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }

    /** Names, selected route length, analysis count and dates, without parsing engine info lines. */
    public static GameSummary readSummary(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int format = checkFormat(root);
        GameRecord record = readRecord(root, format);
        JSONArray analysis = root.optJSONArray("analysis");
        return new GameSummary(root.getString("id"), record.blackName(), record.whiteName(),
                record.length(), analysis == null ? 0 : analysis.length(),
                root.optLong("createdAt", 0), root.optLong("updatedAt", 0));
    }

    public static SavedGame read(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int format = checkFormat(root);
        GameRecord record = readRecord(root, format);
        GameAnalysis analysis = new GameAnalysis(record);
        JSONArray entries = root.optJSONArray("analysis");
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                long nodeId;
                if (format == 1) {
                    int ply = entry.getInt("ply");
                    if (ply < 0 || ply > record.length()) continue;
                    nodeId = record.nodeIdAtPly(ply);
                } else {
                    nodeId = entry.getLong("nodeId");
                    if (record.node(nodeId) == null) continue;
                }
                List<EngineInfo> infos = new ArrayList<>();
                JSONArray lines = entry.optJSONArray("infos");
                for (int j = 0; lines != null && j < lines.length(); j++) {
                    EngineInfo info = EngineInfo.parse(lines.getString(j));
                    if (info != null) infos.add(info);
                }
                String bestMove = entry.isNull("bestMove") ? null : entry.getString("bestMove");
                analysis.putByNode(nodeId, PositionAnalysis.from(entry.getString("sfen"), infos, bestMove));
            }
        }
        return new SavedGame(root.getString("id"), root.optLong("createdAt", 0),
                root.optLong("updatedAt", 0), record, analysis);
    }

    private static int checkFormat(JSONObject root) throws JSONException {
        int format = root.optInt("format", 0);
        if (format != 1 && format != FORMAT) {
            throw new JSONException("unsupported saved game format " + format);
        }
        return format;
    }

    private static GameRecord readRecord(JSONObject root, int format) throws JSONException {
        if (format == 1) return readLinearRecord(root);
        JSONArray array = root.getJSONArray("nodes");
        List<GameRecord.NodeData> nodes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            nodes.add(new GameRecord.NodeData(entry.getLong("id"), optionalId(entry, "parentId"),
                    entry.getInt("move"), entry.optInt("seconds", 0),
                    entry.isNull("comment") ? null : entry.getString("comment"),
                    optionalId(entry, "mainChildId"), optionalId(entry, "selectedChildId")));
        }
        try {
            return GameRecord.fromTree(root.getString("startSfen"), root.optString("black", ""),
                    root.optString("white", ""), nodes, root.getLong("currentNodeId"), root.getLong("nextNodeId"));
        } catch (IllegalArgumentException e) {
            throw new JSONException("invalid saved game tree: " + e.getMessage());
        }
    }

    private static Long optionalId(JSONObject entry, String key) throws JSONException {
        return entry.isNull(key) ? null : entry.getLong(key);
    }

    private static GameRecord readLinearRecord(JSONObject root) throws JSONException {
        JSONArray moveArray = root.getJSONArray("moves");
        JSONArray timeArray = root.optJSONArray("times");
        JSONArray commentArray = root.optJSONArray("comments");
        List<Integer> moves = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < moveArray.length(); i++) {
            moves.add(moveArray.getInt(i));
            times.add(timeArray != null && i < timeArray.length() ? timeArray.optInt(i, 0) : 0);
            comments.add(commentArray != null && i < commentArray.length() && !commentArray.isNull(i)
                    ? commentArray.getString(i) : null);
        }
        GameRecord record = new GameRecord(root.getString("startSfen"), moves, times, comments,
                root.optString("black", ""), root.optString("white", ""));
        record.seek(root.optInt("currentPly", record.length()));
        return record;
    }
}
