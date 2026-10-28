package org.androshogi.ui.main;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import org.androshogi.R;

/** Owns the low-latency sound used when one move is played on the board. */
final class MoveSoundPlayer {
    private final SoundPool soundPool;
    private final int soundId;
    private volatile boolean loaded;
    private volatile boolean released;

    MoveSoundPlayer(Context context) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0 && !released) loaded = true;
        });
        soundId = soundPool.load(context.getApplicationContext(), R.raw.koma_sound, 1);
    }

    void play() {
        if (loaded && !released && soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    void release() {
        if (released) return;
        released = true;
        loaded = false;
        soundPool.release();
    }
}
