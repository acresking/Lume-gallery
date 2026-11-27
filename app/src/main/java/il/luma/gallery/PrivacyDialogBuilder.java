package il.luma.gallery;

import android.app.*;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared rounded sheet styling without replacing callers' own OnShow listeners. */
final class PrivacyDialogBuilder extends AlertDialog.Builder {
  private final Context context;
  private final boolean light;

  PrivacyDialogBuilder(Context c) {
    this(c, c.getSharedPreferences("luma", 0).getBoolean("light", true));
  }

  PrivacyDialogBuilder(Context c, boolean light) {
    super(c, light ? R.style.LumeDialogLight : R.style.LumeDialogDark);
    context = c;
    this.light = light;
  }

  private int dp(int n) {
    return Math.round(n * context.getResources().getDisplayMetrics().density);
  }

  @Override
  public AlertDialog create() {
    AlertDialog dialog = super.create();
    AppPrivacy.apply(dialog.getWindow(), context);
    int bg = light ? 0xfffcfaff : 0xff1c1925,
        ink = light ? 0xff211b31 : 0xfff2edff,
        accent = light ? 0xff7150c2 : 0xffc9b1ff;
    GradientDrawable background = new GradientDrawable();
    background.setColor(bg);
    background.setCornerRadius(dp(30));
    dialog.getWindow().setBackgroundDrawable(background);
    boolean motion =
        context.getSharedPreferences("luma", 0).getBoolean("motion", true)
            && android.animation.ValueAnimator.areAnimatorsEnabled();
    dialog.getWindow().setWindowAnimations(motion ? R.style.GallerySheetMotion : 0);
    dialog
        .getWindow()
        .getDecorView()
        .addOnAttachStateChangeListener(
            new View.OnAttachStateChangeListener() {
              public void onViewAttachedToWindow(View view) {
                view.post(
                    () -> {
                      if (!dialog.isShowing()) return;
                      dialog.getWindow().setGravity(Gravity.BOTTOM);
                      WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
                      attrs.y = dp(10);
                      attrs.dimAmount = .45f;
                      dialog.getWindow().setAttributes(attrs);
                      dialog
                          .getWindow()
                          .setLayout(
                              context.getResources().getDisplayMetrics().widthPixels - dp(20), -2);
                      view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                      tint(view, ink);
                      int total = 0;
                      Button first = null;
                      for (int which : new int[] {-1, -2, -3}) {
                        Button b = dialog.getButton(which);
                        if (b == null || b.getVisibility() != View.VISIBLE) continue;
                        if (first == null) first = b;
                        b.setTextColor(accent);
                        b.setAllCaps(false);
                        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                        b.setTextSize(14);
                        b.setMinWidth(0);
                        b.setMinimumWidth(0);
                        b.setMinHeight(dp(48));
                        b.setMaxLines(2);
                        total += (int) b.getPaint().measureText(b.getText().toString()) + dp(36);
                      }
                      if (first != null
                          && total > context.getResources().getDisplayMetrics().widthPixels - dp(64)
                          && first.getParent() instanceof LinearLayout) {
                        LinearLayout bar = (LinearLayout) first.getParent();
                        bar.setOrientation(LinearLayout.VERTICAL);
                        for (int i = 0; i < bar.getChildCount(); i++) {
                          View child = bar.getChildAt(i);
                          if (child instanceof Button) {
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            child.setLayoutParams(lp);
                          }
                        }
                      }
                    });
              }

              public void onViewDetachedFromWindow(View v) {}
            });
    return dialog;
  }

  private void tint(View v, int ink) {
    if (v instanceof TextView) ((TextView) v).setTextColor(ink);
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i), ink);
    }
  }
}
