package org.androshogi.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.androshogi.R;

/** Edits the names in side order regardless of the board's orientation. */
final class PlayerNamesDialog {
    interface Callback {
        void onSave(String black, String white);
    }

    static void show(Context context, String blackName, String whiteName, Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_player_names, null);
        EditText black = view.findViewById(R.id.black_player_name);
        EditText white = view.findViewById(R.id.white_player_name);
        black.setText(blackName);
        white.setText(whiteName);
        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Androshogi_AlertDialog)
                .setTitle(R.string.edit_player_names)
                .setView(view)
                .setPositiveButton(R.string.save_player_names, (dialog, which) -> callback.onSave(
                        normalize(black.getText(), context.getString(R.string.default_black_name)),
                        normalize(white.getText(), context.getString(R.string.default_white_name))))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String normalize(CharSequence input, String fallback) {
        // Pasted line breaks must not create extra KIF header lines.
        String name = input.toString().replace('\r', ' ').replace('\n', ' ');
        int start = 0;
        int end = name.length();
        while (start < end && isSpace(name.charAt(start))) start++;
        while (end > start && isSpace(name.charAt(end - 1))) end--;
        return start == end ? fallback : name.substring(start, end);
    }

    private static boolean isSpace(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }
}
