package org.androshogi.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.androshogi.R;

/** Collects encoding and comment choices before opening the document picker. */
final class KifExportDialog {
    interface Callback {
        void onSave(int format, boolean includeAnalysis);
    }

    static void show(Context context, boolean hasAnalysis, Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_kif_export, null);
        CheckBox analysis = view.findViewById(R.id.kif_include_analysis);
        analysis.setEnabled(hasAnalysis);
        if (!hasAnalysis) view.findViewById(R.id.kif_no_analysis).setVisibility(View.VISIBLE);
        RadioGroup formats = view.findViewById(R.id.kif_export_format);
        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.save_kifu)
                .setView(view)
                .setPositiveButton(R.string.kifu_export_save, (dialog, which) -> callback.onSave(
                        formats.getCheckedRadioButtonId() == R.id.kif_export_shift_jis ? 0 : 1,
                        hasAnalysis && analysis.isChecked()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
