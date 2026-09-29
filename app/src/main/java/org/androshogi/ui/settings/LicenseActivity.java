package org.androshogi.ui.settings;

import org.androshogi.R;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.RawRes;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Shows the third-party notices and the GPLv3 text. Distributing a GPL
 * binary requires the license to be available to the people who install it,
 * and they do not see the repository.
 */
public class LicenseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        TextView text = findViewById(R.id.license_text);
        text.setText(readRaw(R.raw.third_party_notices) + "\n\n" + readRaw(R.raw.license_gpl3));
    }

    private String readRaw(@RawRes int id) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getResources().openRawResource(id), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            sb.append(getString(R.string.license_load_failed));
        }
        return sb.toString();
    }
}
