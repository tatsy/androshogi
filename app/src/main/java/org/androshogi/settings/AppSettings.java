package org.androshogi.settings;

import org.androshogi.game.PositionAnalysis;
import org.androshogi.engine.EngineKind;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * Typed access to the user settings. Keys and defaults live here and in
 * {@code res/xml/preferences.xml}; keep the two in step.
 */
public final class AppSettings {
    public static final String KEY_ENGINE_KIND = "engine_kind";
    public static final String KEY_THINK_TIME_MS = "engine_think_time";
    public static final String KEY_MULTI_PV = "engine_multipv";
    public static final String KEY_THREADS = "engine_threads";
    public static final String KEY_HASH_MB = "engine_hash_mb";
    public static final String KEY_FV_SCALE = "engine_fv_scale";
    public static final String KEY_EVAL_FILE = "engine_eval_file";
    public static final String KEY_KIF_SAVE_FOLDER = "kif_save_folder";
    private static final String KEY_EVAL_REVISION = "engine_eval_revision";
    public static final String KEY_SCORE_PERSPECTIVE = "score_perspective";
    public static final String KEY_WIN_RATE_SCALE = "win_rate_scale";
    public static final String KEY_THEME = "app_theme";
    /** Last value chosen in the analysis dialog; not shown on the settings screen. */
    public static final String KEY_ANALYSIS_TIME_MS = "analysis_time_ms";
    public static final String KEY_ANALYSIS_MULTI_PV = "analysis_multipv";
    public static final String KEY_APP_VERSION = "app_version";
    /** Id of the game shown when the app was last stopped; restored on the next start. */
    public static final String KEY_LAST_GAME_ID = "last_game_id";
    public static final String KEY_LICENSES = "licenses";

    public static final int DEFAULT_THINK_TIME_MS = 10000;
    public static final int DEFAULT_MULTI_PV = 5;
    /** The engine's own default is 4; never ask for more threads than the device has. */
    public static final int DEFAULT_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    /** Deliberately small: phones have far less RAM to spare than the engine's desktop default assumes. */
    public static final int DEFAULT_HASH_MB = 64;
    /** Standard YaneuraOu default; Suisho5 recommends 24. */
    public static final int DEFAULT_FV_SCALE = 16;
    public static final int DEFAULT_ANALYSIS_TIME_MS = 1000;
    public static final int DEFAULT_WIN_RATE_SCALE = (int) PositionAnalysis.DEFAULT_WIN_RATE_SCALE;

    public static final String PERSPECTIVE_BLACK = "black";
    public static final String PERSPECTIVE_SIDE_TO_MOVE = "side_to_move";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private AppSettings() {}

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** ListPreference stores its values as strings, so parse with a fallback. */
    private static int getInt(Context context, String key, int fallback) {
        String value = prefs(context).getString(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Search time of the "検討" button, in milliseconds. */
    public static int thinkTimeMs(Context context) {
        return getInt(context, KEY_THINK_TIME_MS, DEFAULT_THINK_TIME_MS);
    }

    public static int multiPv(Context context) {
        return Math.max(1, getInt(context, KEY_MULTI_PV, DEFAULT_MULTI_PV));
    }

    public static int threads(Context context) {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(cores, getInt(context, KEY_THREADS, DEFAULT_THREADS)));
    }

    public static int hashMb(Context context) {
        return Math.max(1, getInt(context, KEY_HASH_MB, DEFAULT_HASH_MB));
    }

    public static int fvScale(Context context) {
        EngineKind kind = engineKind(context);
        return getInt(context, fvScaleKey(kind), kind.defaultFvScale());
    }

    public static EngineKind engineKind(Context context) {
        return EngineKind.fromId(prefs(context).getString(KEY_ENGINE_KIND, null));
    }

    private static String fvScaleKey(EngineKind kind) {
        // Keep the existing Háo / standard-engine setting when upgrading the app.
        return kind == EngineKind.DEFAULT ? KEY_FV_SCALE : KEY_FV_SCALE + "_" + kind.id();
    }

    public static void setFvScale(Context context, int value) {
        prefs(context).edit().putString(fvScaleKey(engineKind(context)),
                String.valueOf(value)).apply();
    }

    /** A user-selected directory shown when the KIF save picker opens. */
    @Nullable
    public static Uri kifSaveFolder(Context context) {
        String value = prefs(context).getString(KEY_KIF_SAVE_FOLDER, null);
        if (value == null) {
            return null;
        }
        Uri uri = Uri.parse(value);
        // Preferences can be restored from backup without their SAF grants.
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri) && permission.isReadPermission()) {
                return uri;
            }
        }
        return null;
    }

    /** Retain the tree grant so the directory remains usable after app restart. */
    public static boolean setKifSaveFolder(Context context, Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            return false;
        }
        prefs(context).edit().putString(KEY_KIF_SAVE_FOLDER, uri.toString()).apply();
        return true;
    }

    /** Incremented only after a new nn.bin is safely installed. */
    public static long evaluationRevision(Context context) {
        return prefs(context).getLong(KEY_EVAL_REVISION, 0L);
    }

    public static void markEvaluationChanged(Context context) {
        SharedPreferences preferences = prefs(context);
        preferences.edit().putLong(KEY_EVAL_REVISION,
                preferences.getLong(KEY_EVAL_REVISION, 0L) + 1L).apply();
    }

    /** AppCompatDelegate night mode for a theme preference value. */
    public static int nightModeFor(@Nullable String theme) {
        if (THEME_LIGHT.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (THEME_DARK.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /**
     * Applies the theme preference. Called from the Application before any
     * activity exists, and again when the preference changes; AppCompat then
     * recreates the started activities.
     */
    public static void applyTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(
                nightModeFor(prefs(context).getString(KEY_THEME, THEME_SYSTEM)));
    }

    /** True to show scores from black's point of view, false for the side to move. */
    public static boolean scoreFromBlack(Context context) {
        String value = prefs(context).getString(KEY_SCORE_PERSPECTIVE, PERSPECTIVE_BLACK);
        return !PERSPECTIVE_SIDE_TO_MOVE.equals(value);
    }

    /** Centipawn scale of the win-rate curve; larger is flatter. */
    public static double winRateScale(Context context) {
        return Math.max(1, getInt(context, KEY_WIN_RATE_SCALE, DEFAULT_WIN_RATE_SCALE));
    }

    @Nullable
    public static String lastGameId(Context context) {
        return prefs(context).getString(KEY_LAST_GAME_ID, null);
    }

    public static void setLastGameId(Context context, String id) {
        prefs(context).edit().putString(KEY_LAST_GAME_ID, id).apply();
    }

    public static void clearLastGameId(Context context) {
        prefs(context).edit().remove(KEY_LAST_GAME_ID).apply();
    }

    public static int analysisTimeMs(Context context) {
        return prefs(context).getInt(KEY_ANALYSIS_TIME_MS, DEFAULT_ANALYSIS_TIME_MS);
    }

    public static void setAnalysisTimeMs(Context context, int timeMs) {
        prefs(context).edit().putInt(KEY_ANALYSIS_TIME_MS, timeMs).apply();
    }

    /** First use follows the manual setting; later runs remember the analysis choice. */
    public static int analysisMultiPv(Context context) {
        return Math.max(1, Math.min(5,
                prefs(context).getInt(KEY_ANALYSIS_MULTI_PV, multiPv(context))));
    }

    public static void setAnalysisMultiPv(Context context, int multiPv) {
        prefs(context).edit().putInt(KEY_ANALYSIS_MULTI_PV,
                Math.max(1, Math.min(5, multiPv))).apply();
    }
}
