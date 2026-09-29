package org.androshogi.ui.main;

import org.androshogi.settings.AppSettings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

/** Keeps a KIF filename's extension and opens the user's chosen export directory. */
final class KifCreateDocument extends ActivityResultContracts.CreateDocument {
    KifCreateDocument(String mimeType) {
        super(mimeType);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull String input) {
        Intent intent = super.createIntent(context, input);
        Uri folder = AppSettings.kifSaveFolder(context);
        if (folder != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
        }
        return intent;
    }
}
