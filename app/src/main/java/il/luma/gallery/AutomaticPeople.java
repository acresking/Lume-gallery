package il.luma.gallery;

import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground, incremental organisation with small batches; persistent jobs remain the fallback. */
final class AutomaticPeople {
  final ModernActivity host;
  private final android.os.Handler handler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private AtomicBoolean cancel = new AtomicBoolean();
  private boolean paused, queued;
  private volatile boolean running;
  private long completedSignature = Long.MIN_VALUE;

  AutomaticPeople(ModernActivity a) {
    host = a;
  }

  void maybe() {
    if (paused
        || queued
        || running
        || host.destroyed
        || host.ai == null
        || host.ai.busy
        || !host.prefs.getBoolean("autoPeople", true)
        || !host.prefs.getBoolean("autoOrganizeForeground", true)
        || !LocalModels.installed(host, "faces")
        || !LocalModels.installed(host, "facegroup")) return;
    if (host.prefs.getBoolean("organizeCharging", false)) {
      android.os.BatteryManager battery = host.getSystemService(android.os.BatteryManager.class);
      if (battery != null && !battery.isCharging()) return;
    }
    android.os.BatteryManager battery = host.getSystemService(android.os.BatteryManager.class);
    android.os.PowerManager power = host.getSystemService(android.os.PowerManager.class);
    if ((power != null && power.isPowerSaveMode())
        || (battery != null
            && battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) > 0
            && battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) < 15))
      return;
    long signature = 1;
    for (MainActivity.Media m : host.media)
      signature = 31 * signature + OrganizeEngine.fingerprint(m).hashCode();
    if (signature == completedSignature) return;
    final long scanned = signature;
    queued = true;
    handler.postDelayed(
        () -> {
          queued = false;
          if (paused || host.destroyed || running) return;
          running = true;
          cancel = new AtomicBoolean();
          final AtomicBoolean token = cancel;
          host.ai.worker.execute(
              () -> {
                boolean pending = false;
                try {
                  OrganizeEngine.Result result =
                      OrganizeEngine.run(
                          host, token, 12, true, host.prefs.getBoolean("autoPlaces", true));
                  pending = result.pending;
                  host.prefs.edit().putString("organizeStatus", result.message).apply();
                  if (!result.pending && !token.get()) completedSignature = scanned;
                } catch (Exception error) {
                  host.prefs
                      .edit()
                      .putString(
                          "organizeStatus", "הסידור ינסה שוב לאחר שינוי הספרייה או פתיחה מחדש")
                      .apply();
                  completedSignature = scanned;
                }
                final boolean again = pending;
                host.runOnUiThread(
                    () -> {
                      running = false;
                      if (host.destroyed || paused) return;
                      if (host.tab.equals("albums")) host.render();
                      if (again || token.get()) maybe();
                    });
              });
        },
        1200);
  }

  void resume() {
    paused = false;
    maybe();
  }

  void pause() {
    paused = true;
    cancel.set(true);
    handler.removeCallbacksAndMessages(null);
    queued = false;
  }

  void close() {
    pause();
  }
}
