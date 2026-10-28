package org.androshogi.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.androshogi.R;

/** Edits the ordinary multiline comment attached to one position node. */
final class CommentEditDialog {
    interface Callback {
        void onSave(String comment);
    }

    private CommentEditDialog() {}

    static void show(Context context, String comment, Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_comment_edit, null);
        EditText editor = view.findViewById(R.id.comment_edit_text);
        editor.setText(comment == null ? "" : comment);
        editor.setSelection(editor.length());
        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.edit_comment)
                .setView(view)
                .setPositiveButton(R.string.save_comment, (dialog, which) -> callback.onSave(
                        normalizeLineEndings(editor.getText().toString())))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
