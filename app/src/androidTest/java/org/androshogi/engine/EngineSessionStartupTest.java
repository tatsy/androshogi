package org.androshogi.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real process-launch failure paths on Android. */
@RunWith(AndroidJUnit4.class)
public class EngineSessionStartupTest {
    @Test
    public void missingEngineReportsFailure() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "missing_engine_" + System.nanoTime();
        File executable = new File(context.getCacheDir(), "lib" + name + ".so");
        assertFalse(executable.exists());

        assertFailure(name, context.getCacheDir(),
                "エンジンが見つかりません: " + executable.getName(), true);
    }

    @Test
    public void unlaunchableEngineReportsFailure() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File executable = File.createTempFile("libengine-unlaunchable-", ".so",
                context.getCacheDir());
        try {
            assertTrue(executable.setExecutable(false, false));
            assertFalse(executable.canExecute());
            String name = executable.getName().substring(3, executable.getName().length() - 3);

            assertFailure(name, context.getCacheDir(),
                    "エンジンを起動できませんでした: ", false);
        } finally {
            assertTrue(executable.delete());
        }
    }

    private static void assertFailure(String name, File workingDir, String expectedMessage,
                                      boolean exact) throws InterruptedException {
        EngineSession session = new EngineSession(name, workingDir.getAbsolutePath());
        CountDownLatch failed = new CountDownLatch(1);
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger readyCount = new AtomicInteger();
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<Looper> callbackLooper = new AtomicReference<>();
        session.setStateListener(new EngineSession.StateListener() {
            @Override
            public void onEngineReady(String engineName) {
                readyCount.incrementAndGet();
            }

            @Override
            public void onEngineFailed(String reason) {
                message.set(reason);
                callbackLooper.set(Looper.myLooper());
                failureCount.incrementAndGet();
                failed.countDown();
            }
        });

        try {
            session.start();
            assertTrue("Engine did not report startup failure", failed.await(10, TimeUnit.SECONDS));
            assertEquals(EngineSession.State.CLOSED, session.getState());
            assertEquals(Looper.getMainLooper(), callbackLooper.get());
            assertEquals(0, readyCount.get());
            assertEquals(1, failureCount.get());
            if (exact) {
                assertEquals(expectedMessage, message.get());
            } else {
                assertTrue(message.get(), message.get().startsWith(expectedMessage));
            }
        } finally {
            session.shutdown();
        }
    }
}
