package il.luma.gallery;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

/** Real month/year cover cards, distinct from merely changing the grid column count. */
final class CalendarOverview extends RecyclerView.Adapter<CalendarOverview.Holder> {
  static final class Group {
    final String title;
    final MainActivity.Media cover;
    int count;

    Group(String name, MainActivity.Media item) {
      title = name;
      cover = item;
    }
  }

  final ModernActivity host;
  final ArrayList<Group> groups;

  static ArrayList<Group> groups(List<MainActivity.Media> media, boolean years) {
    LinkedHashMap<String, Group> byDate = new LinkedHashMap<>();
    SimpleDateFormat key = new SimpleDateFormat(years ? "yyyy" : "yyyy-MM", Locale.ROOT),
        label = new SimpleDateFormat(years ? "yyyy" : "MMMM yyyy", new Locale("he"));
    for (MainActivity.Media m : media) {
      String id = m.date > 0 ? key.format(new Date(m.date)) : "unknown";
      Group group =
          byDate.computeIfAbsent(
              id, k -> new Group(m.date > 0 ? label.format(new Date(m.date)) : "תאריך לא זמין", m));
      group.count++;
    }
    return new ArrayList<>(byDate.values());
  }

  CalendarOverview(ModernActivity a, List<MainActivity.Media> media, boolean years) {
    host = a;
    groups = groups(media, years);
  }

  static final class Holder extends RecyclerView.ViewHolder {
    Holder(View v) {
      super(v);
    }
  }

  @Override
  public int getItemViewType(int p) {
    return host.galleryChrome != null && p == 0 ? 1 : 0;
  }

  @Override
  public Holder onCreateViewHolder(ViewGroup parent, int type) {
    if (type == 1) {
      FrameLayout frame = new FrameLayout(host);
      frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      return new Holder(frame);
    }
    SquareTile tile = new SquareTile(host);
    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
    lp.setMargins(host.dp(5), host.dp(5), host.dp(5), host.dp(5));
    tile.setLayoutParams(lp);
    tile.setBackground(host.rounded(host.card, 24));
    tile.setClipToOutline(true);
    return new Holder(tile);
  }

  @Override
  public void onBindViewHolder(Holder h, int p) {
    GalleryGrid.resetCell(h.itemView);
    if (host.galleryChrome != null) {
      if (p == 0) {
        FrameLayout frame = (FrameLayout) h.itemView;
        frame.removeAllViews();
        if (host.galleryChrome.getParent() instanceof ViewGroup)
          ((ViewGroup) host.galleryChrome.getParent()).removeView(host.galleryChrome);
        frame.addView(host.galleryChrome, new FrameLayout.LayoutParams(-1, -2));
        return;
      }
      p--;
    }
    Group g = groups.get(p);
    FrameLayout tile = (FrameLayout) h.itemView;
    host.cancelThumbnails(tile);
    tile.removeAllViews();
    ImageView image = new ImageView(host);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
    host.thumbnail(image, g.cover, 400);
    View shade = new View(host);
    GradientDrawable gradient =
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x08000000, 0xd9000000});
    shade.setBackground(gradient);
    tile.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout words = host.column();
    words.setPadding(host.dp(14), host.dp(10), host.dp(14), host.dp(16));
    TextView title = host.label(g.title, 22, Color.WHITE, true);
    title.setMaxLines(2);
    words.addView(title);
    words.addView(host.label(g.count + " פריטים", 13, 0xffe2dbeb, false));
    tile.addView(words, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    tile.setContentDescription(g.title + " · " + g.count + " פריטים · הקש להתקרבות");
    tile.setFocusable(true);
    tile.setOnClickListener(v -> host.openOverviewDate(g.cover.date));
  }

  @Override
  public int getItemCount() {
    return groups.size() + (host.galleryChrome == null ? 0 : 1);
  }

  int nearest(long date) {
    int best = 0;
    long gap = Long.MAX_VALUE;
    for (int i = 0; i < groups.size(); i++) {
      long d = Math.abs(groups.get(i).cover.date - date);
      if (d < gap) {
        gap = d;
        best = i;
      }
    }
    return best + (host.galleryChrome == null ? 0 : 1);
  }
}
