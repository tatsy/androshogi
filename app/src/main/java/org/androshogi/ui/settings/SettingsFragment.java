package org.androshogi.ui.settings;

import org.androshogi.engine.EvaluationFileStore;
import org.androshogi.engine.EngineKind;
import org.androshogi.settings.AppSettings;

import org.androshogi.R;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";
    private final ExecutorService evaluationImport = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Preference evaluationPreference;
    private ListPreference enginePreference;
    private ListPreference fvScalePreference;
    private EngineKind pendingEvaluationKind = EngineKind.DEFAULT;
    private Preference kifFolderPreference;
    private boolean importingEvaluation;

    private final ActivityResultLauncher<String[]> evaluationPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importEvaluation(uri, pendingEvaluationKind);
                }
            });

    private final ActivityResultLauncher<Uri> kifFolderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) {
                    return;
                }
                if (AppSettings.setKifSaveFolder(requireContext(), uri)) {
                    updateKifFolderSummary();
                } else {
                    Toast.makeText(requireContext(), R.string.kifu_folder_failed,
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        if (savedInstanceState != null) {
            pendingEvaluationKind = EngineKind.fromId(savedInstanceState.getString("evaluation_picker_kind"));
        }

        // FV_SCALE belongs to the selected architecture, rather than one shared XML key.
        fvScalePreference = findPreference(AppSettings.KEY_FV_SCALE);
        if (fvScalePreference != null) {
            fvScalePreference.setPersistent(false);
            fvScalePreference.setOnPreferenceChangeListener((p, value) -> {
                AppSettings.setFvScale(requireContext(), Integer.parseInt((String) value));
                return true;
            });
            updateFvScale();
        }

        enginePreference = findPreference(AppSettings.KEY_ENGINE_KIND);
        if (enginePreference != null) {
            enginePreference.setValue(AppSettings.engineKind(requireContext()).id());
            enginePreference.setOnPreferenceChangeListener((p, value) -> {
                // Persist first so dependent controls immediately read the new engine's settings.
                enginePreference.setValue((String) value);
                updateFvScale();
                updateEvaluationSummary();
                return true;
            });
        }

        evaluationPreference = findPreference(AppSettings.KEY_EVAL_FILE);
        if (evaluationPreference != null) {
            updateEvaluationSummary();
            evaluationPreference.setOnPreferenceClickListener(p -> {
                pendingEvaluationKind = AppSettings.engineKind(requireContext());
                evaluationPicker.launch(new String[] {"*/*"});
                return true;
            });
        }

        kifFolderPreference = findPreference(AppSettings.KEY_KIF_SAVE_FOLDER);
        if (kifFolderPreference != null) {
            updateKifFolderSummary();
            kifFolderPreference.setOnPreferenceClickListener(p -> {
                kifFolderPicker.launch(AppSettings.kifSaveFolder(requireContext()));
                return true;
            });
        }

        // The thread choices depend on the device, so they cannot live in XML.
        ListPreference threads = findPreference(AppSettings.KEY_THREADS);
        if (threads != null) {
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            String[] values = new String[cores];
            for (int i = 0; i < cores; i++) {
                values[i] = String.valueOf(i + 1);
            }
            threads.setEntries(values);
            threads.setEntryValues(values);
            if (threads.getValue() == null) {
                threads.setValue(String.valueOf(AppSettings.DEFAULT_THREADS));
            }
        }

        // Switch the theme right away rather than on the next launch.
        ListPreference theme = findPreference(AppSettings.KEY_THEME);
        if (theme != null) {
            theme.setOnPreferenceChangeListener((p, value) -> {
                AppCompatDelegate.setDefaultNightMode(AppSettings.nightModeFor((String) value));
                return true;
            });
        }

        Preference version = findPreference(AppSettings.KEY_APP_VERSION);
        if (version != null) {
            // BuildConfig is not generated by default on AGP 8, so ask the package manager.
            try {
                PackageInfo info = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0);
                version.setSummary(info.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                version.setSummary("?");
            }
        }

        Preference licenses = findPreference(AppSettings.KEY_LICENSES);
        if (licenses != null) {
            licenses.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(requireContext(), LicenseActivity.class));
                return true;
            });
        }
    }

    private void updateKifFolderSummary() {
        if (kifFolderPreference != null) {
            kifFolderPreference.setSummary(AppSettings.kifSaveFolder(requireContext()) == null
                    ? R.string.pref_kif_folder_missing : R.string.pref_kif_folder_ready);
        }
    }

    private void updateEvaluationSummary() {
        if (evaluationPreference == null) {
            return;
        }
        evaluationPreference.setEnabled(!importingEvaluation);
        if (enginePreference != null) {
            enginePreference.setEnabled(!importingEvaluation);
        }
        evaluationPreference.setSummary(importingEvaluation
                ? R.string.pref_eval_file_importing
                : EvaluationFileStore.file(requireContext()).isFile()
                        ? R.string.pref_eval_file_ready : R.string.pref_eval_file_missing);
    }

    private void updateFvScale() {
        if (fvScalePreference != null) {
            fvScalePreference.setValue(String.valueOf(AppSettings.fvScale(requireContext())));
        }
    }

    private void importEvaluation(Uri uri, EngineKind kind) {
        if (importingEvaluation) {
            return;
        }
        importingEvaluation = true;
        updateEvaluationSummary();
        final Context appContext = requireContext().getApplicationContext();
        evaluationImport.execute(() -> {
            boolean succeeded = false;
            try {
                EvaluationFileStore.importFile(appContext, uri, kind);
                AppSettings.markEvaluationChanged(appContext);
                succeeded = true;
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Failed to import evaluation file", e);
            }
            final boolean imported = succeeded;
            mainHandler.post(() -> {
                importingEvaluation = false;
                if (!isAdded()) {
                    return;
                }
                updateEvaluationSummary();
                if (imported) {
                    Toast.makeText(requireContext(), R.string.pref_eval_file_imported,
                            Toast.LENGTH_LONG).show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext(),
                            R.style.ThemeOverlay_Androshogi_AlertDialog)
                            .setTitle(R.string.pref_eval_file_failed_title)
                            .setMessage(R.string.pref_eval_file_failed_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateEvaluationSummary();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("evaluation_picker_kind", pendingEvaluationKind.id());
    }

    @Override
    public void onDestroy() {
        evaluationImport.shutdown();
        super.onDestroy();
    }
}
