package il.luma.gallery;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Consistent controls across photo/video/subject studios. */
final class StudioUI {
  static final int BACKGROUND = 0xff0c0c12, SURFACE = 0xff1b1924, ACCENT = 0xffcfb5ff;

  static Button control(ModernActivity a, String text, Runnable action, boolean primary) {
    Button b = a.button(text, action);
    b.setTextColor(primary ? 0xff241536 : 0xfff4efff);
    b.setTextSize(14);
    b.setMinHeight(a.dp(50));
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x44ffffff),
            a.rounded(primary ? ACCENT : 0xff302b40, 25),
            null));
    return b;
  }

  static View close(ModernActivity a, Runnable action) {
    View v = a.iconButton("close", "סגירת העורך", action);
    ((ImageView) ((FrameLayout) v).getChildAt(0))
        .setImageDrawable(new GalleryIcons("close", Color.WHITE));
    return v;
  }

  static TextView tab(ModernActivity a, String value, boolean selected, Runnable action) {
    TextView t = a.label(value, 14, selected ? 0xff241536 : 0xffd1c9de, selected);
    t.setGravity(Gravity.CENTER);
    t.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(12));
    t.setMinHeight(a.dp(50));
    t.setSelected(selected);
    t.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x33ffffff), a.rounded(selected ? ACCENT : SURFACE, 24), null));
    t.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.setMargins(a.dp(4), 0, a.dp(4), 0);
    t.setLayoutParams(lp);
    return t;
  }

  static void panel(ModernActivity a, View panel) {
    panel.setBackground(a.rounded(SURFACE, 28));
  }

  static void slider(SeekBar s) {
    s.setProgressTintList(ColorStateList.valueOf(ACCENT));
    s.setThumbTintList(ColorStateList.valueOf(ACCENT));
    s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff484054));
  }
}
