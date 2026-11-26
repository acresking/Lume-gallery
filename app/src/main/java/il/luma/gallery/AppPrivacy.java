package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.view.*;
import android.widget.*;

/** Optional UI lock through Android, never a custom PIN entry form. */
final class AppPrivacy {
  final ModernActivity a;
  boolean authenticated = false, authenticating = false;
  Dialog gate;
  CancellationSignal signal;

  AppPrivacy(ModernActivity activity) {
    a = activity;
  }

  static void apply(Window window, Context context) {
    if (window == null) return;
    var p = context.getSharedPreferences("luma", 0);
    if (p.getBoolean("protectScreen", true) || p.getBoolean("appLock", false))
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  void resume() {
    apply(a.getWindow(), a);
    if (a.prefs.getBoolean("appLock", false) && !authenticated && !a.isFinishing()) showGate();
  }

  void background() {
    authenticated = false;
  }

  boolean available() {
    KeyguardManager manager = (KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
    return manager != null && manager.isDeviceSecure();
  }

  void authenticate(Runnable success) {
    if (authenticating) return;
    if (!available()) {
      a.message("נעילת מכשיר נדרשת", "הגדר נעילת מסך ב־Android לפני הפעלת נעילת האפליקציה");
      return;
    }
    authenticating = true;
    signal = new CancellationSignal();
    new BiometricPrompt.Builder(a)
        .setTitle("פתיחת Lume Gallery")
        .setSubtitle("אימות באמצעות טביעת אצבע או נעילת המכשיר")
        .setAllowedAuthenticators(
            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
        .authenticate(
            signal,
            a.getMainExecutor(),
            new BiometricPrompt.AuthenticationCallback() {
              @Override
              public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                authenticating = false;
                authenticated = true;
                success.run();
              }

              @Override
              public void onAuthenticationError(int code, CharSequence error) {
                authenticating = false;
              }
            });
  }

  void showGate() {
    if (gate != null && gate.isShowing()) return;
    gate =
        new Dialog(
            a,
            a.prefs.getBoolean("light", true)
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
    LinearLayout content = a.column();
    content.setGravity(Gravity.CENTER);
    content.setPadding(a.dp(32), a.dp(32), a.dp(32), a.dp(32));
    content.setBackgroundColor(a.bg);
    ImageView logo = new ImageView(a);
    logo.setImageDrawable(new BrandMark(a.getResources()));
    content.addView(logo, new LinearLayout.LayoutParams(a.dp(80), a.dp(80)));
    TextView title = a.label("Lume Gallery נעול", 25, a.ink, true);
    title.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = a.dp(24);
    content.addView(title, lp);
    TextView detail = a.label("הגלריה תיפתח לאחר אימות ב־Android", 14, a.ink, false);
    detail.setGravity(Gravity.CENTER);
    detail.setPadding(0, a.dp(12), 0, a.dp(24));
    content.addView(detail);
    content.addView(
        a.button(
            "פתיחה מאובטחת",
            () ->
                authenticate(
                    () -> {
                      if (gate != null) gate.dismiss();
                    })),
        new LinearLayout.LayoutParams(-1, -2));
    content.addView(a.button("יציאה", a::finish), new LinearLayout.LayoutParams(-1, -2));
    gate.setContentView(content);
    gate.setCancelable(false);
    gate.setOnKeyListener(
        (d, key, event) -> {
          if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            a.finish();
            return true;
          }
          return false;
        });
    gate.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    gate.show();
    gate.getWindow().setLayout(-1, -1);
    authenticate(
        () -> {
          if (gate != null) gate.dismiss();
        });
  }

  void toggleLock() {
    authenticate(
        () -> {
          a.prefs.edit().putBoolean("appLock", !a.prefs.getBoolean("appLock", false)).apply();
          apply(a.getWindow(), a);
          a.render();
        });
  }

  void close() {
    if (signal != null) signal.cancel();
    if (gate != null) gate.dismiss();
  }
}
