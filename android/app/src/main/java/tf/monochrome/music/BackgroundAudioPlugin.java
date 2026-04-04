package tf.monochrome.music;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Capacitor plugin that exposes start/stop controls for the foreground
 * AudioPlaybackService. Called from JS when audio playback begins or ends
 * so Android keeps the process alive in the background.
 */
@CapacitorPlugin(name = "BackgroundAudio")
public class BackgroundAudioPlugin extends Plugin {

    /** Maximum time (ms) to wait for the service to call startForeground(). */
    private static final long SERVICE_READY_TIMEOUT_MS = 5000;

    @PluginMethod
    public void start(PluginCall call) {
        // Use a latch so we don't resolve until the service has posted its
        // foreground notification. This prevents ForegroundServiceStartNotAllowedException
        // on Android 12+ if stop() is called before the service is ready.
        CountDownLatch latch = new CountDownLatch(1);
        AudioPlaybackService.setOnReadyCallback(latch::countDown);

        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }

        // Wait off the main thread so the UI isn't blocked
        new Thread(() -> {
            try {
                boolean ready = latch.await(SERVICE_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!ready) {
                    // Timed out — clear the stale callback to avoid leaks
                    AudioPlaybackService.setOnReadyCallback(null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Resolve on the main thread as required by Capacitor
            new Handler(Looper.getMainLooper()).post(call::resolve);
        }).start();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        getContext().stopService(intent);
        call.resolve();
    }
}
