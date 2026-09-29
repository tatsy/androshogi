package org.androshogi.storage;

import org.androshogi.engine.EngineInfo;
import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import org.androshogi.game.GameAnalysis;
import org.androshogi.game.GameRecord;
import org.androshogi.game.PositionAnalysis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON form of a {@link SavedGame}.
 *
 * <p>Moves are stored as the app's own move codes, which is lossless and
 * needs no native board to read back; KIF export is a separate, later
 * feature. Analysis entries keep the engine's info lines as text, rebuilt
 * with {@link EngineInfo#toLine()} and parsed again on load, so the reading
 * shown after a restart is the one the engine gave.
 *
 * <p>Free of Android dependencies apart from {@code org.json}, which the
 * platform provides, so it is unit tested on the host JVM.
 */
public final class GameJson {
    /** Bump when the layout changes in a way old readers cannot take. */
    public static final int FORMAT = 1;

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
        root.put("currentPly", record.currentPly());
        JSONArray moves = new JSONArray();
        JSONArray times = new JSONArray();
        JSONArray comments = new JSONArray();
        for (int i = 0; i < record.length(); i++) {
            moves.put(record.move(i));
            times.put(record.timeSeconds(i));
            String comment = record.comment(i);
            comments.put(comment == null ? JSONObject.NULL : comment);
        }
        root.put("moves", moves);
        root.put("times", times);
        root.put("comments", comments);

        JSONArray analysis = new JSONArray();
        for (int ply = 0; ply < game.analysis.plies(); ply++) {
            PositionAnalysis a = game.analysis.get(ply);
            if (a == null) {
                continue;
            }
            JSONObject entry = new JSONObject();
            entry.put("ply", ply);
            entry.put("sfen", a.sfen());
            entry.put("bestMove", a.bestMoveUsi() == null ? JSONObject.NULL : a.bestMoveUsi());
            JSONArray infos = new JSONArray();
            for (EngineInfo info : a.infos()) {
                infos.put(info.toLine());
            }
            entry.put("infos", infos);
            analysis.put(entry);
        }
        root.put("analysis", analysis);
        return root.toString(2);
    }

    /**
     * The list entry for a saved game: names, counts and dates, without
     * rebuilding the record or the analysis.
     *
     * @throws JSONException when the text is not a saved game this version can read
     */
    public static GameSummary readSummary(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int format = root.optInt("format", 0);
        if (format != FORMAT) {
            throw new JSONException("unsupported saved game format " + format);
        }
        JSONArray analysis = root.optJSONArray("analysis");
        return new GameSummary(root.getString("id"), root.optString("black", ""), root.optString("white", ""),
                root.getJSONArray("moves").length(), analysis == null ? 0 : analysis.length(),
                root.optLong("createdAt", 0), root.optLong("updatedAt", 0));
    }

    /** @throws JSONException when the text is not a saved game this version can read */
    public static SavedGame read(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int format = root.optInt("format", 0);
        if (format != FORMAT) {
            throw new JSONException("unsupported saved game format " + format);
        }

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

        GameAnalysis analysis = new GameAnalysis();
        JSONArray entries = root.optJSONArray("analysis");
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                int ply = entry.getInt("ply");
                if (ply < 0 || ply > record.length()) {
                    continue;
                }
                List<EngineInfo> infos = new ArrayList<>();
                JSONArray lines = entry.optJSONArray("infos");
                for (int j = 0; lines != null && j < lines.length(); j++) {
                    EngineInfo info = EngineInfo.parse(lines.getString(j));
                    if (info != null) {
                        infos.add(info);
                    }
                }
                String bestMove = entry.isNull("bestMove") ? null : entry.getString("bestMove");
                analysis.put(ply, PositionAnalysis.from(entry.getString("sfen"), infos, bestMove));
            }
        }

        return new SavedGame(root.getString("id"), root.optLong("createdAt", 0),
                root.optLong("updatedAt", 0), record, analysis);
    }
}
