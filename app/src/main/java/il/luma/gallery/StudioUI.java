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
    b.setMinHeight(a.dp(48));
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x44ffffff),
            a.rounded(primary ? ACCENT : 0xff25232e, 16),
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
    t.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10));
    t.setMinHeight(a.dp(48));
    t.setSelected(selected);
    t.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x33ffffff), a.rounded(selected ? ACCENT : SURFACE, 16), null));
    t.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.setMargins(a.dp(4), 0, a.dp(4), 0);
    t.setLayoutParams(lp);
    return t;
  }

  static void panel(ModernActivity a, View panel) {
    panel.setBackground(a.rounded(SURFACE, 28));
  }

  static LinearLayout setting(ModernActivity a, LinearLayout parent, String title, TextView value) {
    LinearLayout box = a.column();
    box.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(2));
    box.setBackground(a.rounded(0xff24212e, 18));
    LinearLayout row = a.row();
    TextView name = a.label(title, 14, 0xfff4efff, false);
    row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    value.setGravity(Gravity.CENTER);
    value.setMinWidth(a.dp(48));
    value.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6));
    value.setBackground(a.rounded(0xff363040, 10));
    row.addView(value);
    box.addView(row);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = a.dp(8);
    parent.addView(box, lp);
    return box;
  }

  static int choiceColumns(ModernActivity a) {
    return a.getResources().getConfiguration().screenWidthDp < 380
            || a.getResources().getConfiguration().fontScale > 1.2f
        ? 2
        : 3;
  }

  static void slider(SeekBar s) {
    s.setProgressTintList(ColorStateList.valueOf(ACCENT));
    s.setThumbTintList(ColorStateList.valueOf(ACCENT));
    s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff484054));
  }
}
