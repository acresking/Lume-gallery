package il.luma.gallery;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import androidx.viewpager2.widget.ViewPager2;
import java.text.*;
import java.util.*;
import org.json.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class ModernActivity extends MainActivity {
  private final LinkedHashSet<String> selected = new LinkedHashSet<>();
  AiController ai;
  AppPrivacy privacy;
  PeoplePlaces peoplePlaces;
  private String lastRenderedTab = "", lastScreenKey = "";
  private final Map<String, Parcelable> timelinePositions = new HashMap<>();
  private final Map<String, Integer> pagePositions = new HashMap<>();
  private final Map<String, Double> similarScores = new HashMap<>();
  private Set<String> archived;
  private final Set<String> duplicates = new HashSet<>();
  private String collection = "", customAlbum = "", grouping = "days";
  private boolean selecting = false;
  private LinearLayout header, selectionBar, searchBox;
  private TextView title;
  private RecyclerView timeline;
  private TimelineAdapter timelineAdapter;
  private final ArrayList<Object> timelineItems = new ArrayList<>();
  private final Map<String, ArrayList<Media>> dateGroups = new LinkedHashMap<>();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private Runnable pendingSearch;
  private int muted;
  private ArrayList<Media> visible = new ArrayList<>();

  @Override
  public void onCreate(Bundle state) {
    android.content.SharedPreferences p = getSharedPreferences("luma", 0);
    if (!p.contains("designV2"))
      p.edit().putBoolean("light", true).putBoolean("designV2", true).apply();
    archived = new HashSet<>(p.getStringSet("archive", Collections.emptySet()));
    String theme = p.getString("themeMode", "");
    if (!theme.isEmpty())
      p.edit()
          .putBoolean(
              "light",
              theme.equals("light")
                  || (theme.equals("system")
                      && (getResources().getConfiguration().uiMode
                              & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                          != android.content.res.Configuration.UI_MODE_NIGHT_YES))
          .apply();
    if (state != null) {
      tab = state.getString("lume.tab", "media");
      album = state.getString("lume.album", "");
      collection = state.getString("lume.collection", "");
      customAlbum = state.getString("lume.custom", "");
      grouping = state.getString("lume.grouping", "days");
      filter = Math.max(0, Math.min(3, state.getInt("lume.filter", 0)));
      search = state.getString("lume.search", "");
      Parcelable position = state.getParcelable("lume.timeline");
      if (position != null) timelinePositions.put(screenKey(), position);
      pagePositions.put(screenKey(), state.getInt("lume.page", 0));
    }
    super.onCreate(state);
    if (android.os.Build.VERSION.SDK_INT >= 31)
      getSplashScreen()
          .setOnExitAnimationListener(
              splash -> {
                if (!motion()) {
                  splash.remove();
                  return;
                }
                splash.animate().alpha(0).setDuration(170).withEndAction(splash::remove).start();
              });
    ai = new AiController(this);
    peoplePlaces = new PeoplePlaces(this);
    privacy = new AppPrivacy(this);
    try {
      PrivateData.migrateMetadata(this);
    } catch (Exception error) {
      Toast.makeText(this, "הצפנת נתונים קיימים טרם הושלמה; בדוק אחסון", Toast.LENGTH_LONG).show();
    }
    ai.worker.execute(() -> PrivateData.migrateCovers(this));
    AutoOrganizeJob.schedule(this);
    render();
  }

  @Override
  void applyColors() {
    boolean light = prefs.getBoolean("light", true);
    boolean black = prefs.getBoolean("oledBlack", true);
    bg = light ? 0xfff7f7fc : black ? Color.BLACK : 0xff100f20;
    card = light ? Color.WHITE : black ? 0xff15151b : 0xff1c1b30;
    ink = light ? 0xff201d30 : 0xfff4f0ff;
    accent = light ? 0xff6d52c7 : 0xffbba6f7;
    muted = light ? 0xff777287 : 0xffa7a0bd;
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
  }

  GradientDrawable rounded(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  LinearLayout row() {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setBaselineAligned(false);
    r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    return r;
  }

  TextView label(String value, int size, int color, boolean bold) {
    TextView t = super.text(value, size);
    t.setTextColor(color);
    t.setTypeface(
        android.graphics.Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    t.setPadding(0, 0, 0, 0);
    t.setTextDirection(View.TEXT_DIRECTION_RTL);
    return t;
  }

  @Override
  TextView text(String value, int size) {
    TextView t = label(value, size, ink, false);
    t.setPadding(dp(16), dp(8), dp(16), dp(8));
    return t;
  }

  ImageView icon(String name, int color, int size) {
    ImageView v = new ImageView(this);
    v.setImageDrawable(new GalleryIcons(name, color));
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return v;
  }

  View iconButton(String name, String description, Runnable click) {
    FrameLayout box = new FrameLayout(this);
    box.setContentDescription(description);
    box.setFocusable(true);
    box.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x22405dde), rounded(Color.TRANSPARENT, 24), null));
    ImageView image = new ImageView(this);
    image.setImageDrawable(new GalleryIcons(name, ink));
    FrameLayout.LayoutParams im = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
    box.addView(image, im);
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    box.setOnClickListener(v -> click.run());
    return box;
  }

  @Override
  Button button(String s, Runnable click) {
    Button b = super.button(s, click);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(dp(48));
    b.setMaxLines(2);
    b.setEllipsize(TextUtils.TruncateAt.END);
    b.setTextSize(13);
    b.setPadding(dp(10), dp(8), dp(10), dp(8));
    GradientDrawable surface = rounded(card, 16);
    surface.setStroke(dp(1), prefs.getBoolean("light", true) ? 0xffe9e4f3 : 0xff343047);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226d52c7), surface, null));
    return b;
  }

  @Override
  void action(LinearLayout list, String s, Runnable click) {
    LinearLayout r = row();
    r.setPadding(dp(16), dp(12), dp(16), dp(12));
    r.setMinimumHeight(dp(58));
    GradientDrawable surface = rounded(card, 18);
    surface.setStroke(dp(1), prefs.getBoolean("light", true) ? 0xffeee9f7 : 0xff312d43);
    r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226d52c7), surface, null));
    String type =
        s.contains("סל")
            ? "trash"
            : s.contains("שמור") || s.contains("שמירת")
                ? "check"
                : s.contains("כלל")
                    ? "clean"
                    : s.contains("תיקיי")
                        ? "folder"
                        : s.contains("יצוא") || s.contains("המר")
                            ? "edit"
                            : s.contains("הרשא") ? "select" : "more";
    if (s.contains("AI") || s.contains("מודל") || s.contains("אינדקס")) type = "clean";
    else if (s.contains("מראה")) type = "settings";
    else if (s.contains("צפיפות")) type = "grid";
    else if (s.contains("כפול")) type = "photos";
    else if (s.contains("סדר")) type = "sort";
    else if (s.contains("רענון") || s.contains("מצגת")) type = "clock";
    r.addView(icon(type, accent, 23));
    TextView t = label(s, 15, ink, false);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(dp(12), 0, dp(12), 0);
    r.addView(t, lp);
    r.setOnClickListener(
        v -> {
          feedback(v);
          click.run();
        });
    pressAnimation(r);
    r.setFocusable(true);
    LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
    margin.setMargins(dp(16), dp(4), dp(16), dp(4));
    list.addView(r, margin);
  }

  @Override
  void shell() {
    root = column();
    root.setBackgroundColor(bg);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    root.requestApplyInsets();
    header = row();
    header.setPadding(dp(14), dp(12), dp(12), dp(12));
    root.addView(header);
    body = column();
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = row();
    LinearLayout.LayoutParams navigation = new LinearLayout.LayoutParams(-1, -2);
    navigation.setMargins(dp(12), dp(5), dp(12), dp(8));
    root.addView(nav, navigation);
    status = label("", 12, muted, false);
    render();
  }

  @Override
  void render() {
    if (body == null) return;
    rememberPosition();
    String currentKey = screenKey();
    body.removeAllViews();
    header.removeAllViews();
    String heading =
        tab.equals("albums")
            ? "אוספים"
            : tab.equals("rules")
                ? "ניקוי חכם"
                : tab.equals("settings") ? "הגדרות" : collectionTitle();
    boolean home =
        tab.equals("media") && collection.isEmpty() && album.isEmpty() && customAlbum.isEmpty();
    if (home) heading = "Lume Gallery";
    FrameLayout brand = new FrameLayout(this);
    ImageView mark = new ImageView(this);
    mark.setImageDrawable(new BrandMark(getResources()));
    brand.addView(mark, new FrameLayout.LayoutParams(dp(37), dp(37), Gravity.CENTER));
    brand.setContentDescription("אודות Lume Gallery");
    brand.setFocusable(true);
    brand.setOnClickListener(v -> aboutBrand());
    header.addView(brand, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout titles = column();
    title = label(selecting ? selected.size() + " נבחרו" : heading, home ? 23 : 27, ink, true);
    title.setMaxLines(1);
    title.setEllipsize(TextUtils.TruncateAt.END);
    titles.addView(title);
    status = label(media.size() + " פריטים · במכשיר", 12, muted, false);
    status.setMaxLines(1);
    status.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
    sub.topMargin = dp(5);
    titles.addView(status, sub);
    LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
    titleLp.setMargins(dp(8), 0, dp(6), 0);
    header.addView(titles, titleLp);
    if (selecting)
      header.addView(
          iconButton(
              "close",
              "סיום בחירה",
              () -> {
                selecting = false;
                selected.clear();
                render();
              }));
    else if (tab.equals("albums")) {
      header.addView(iconButton("search", "חיפוש בתמונות", this::searchDialog));
    } else if (tab.equals("media")) {
      header.addView(iconButton("search", "חיפוש", this::searchDialog));
      header.addView(iconButton("more", "תצוגה ומיון", this::displayMenu));
    } else header.addView(iconButton("info", "אודות ועזרה", this::aboutBrand));
    if (tab.equals("rules")) modernRules();
    else if (tab.equals("settings")) modernSettings();
    else if (tab.equals("albums")) albums();
    else gallery();
    drawNav();
    if (timeline != null && timeline.getParent() == body && timeline.getLayoutManager() != null) {
      Parcelable saved = timelinePositions.get(currentKey);
      if (saved != null) timeline.getLayoutManager().onRestoreInstanceState(saved);
    } else if (body.getChildCount() > 0 && body.getChildAt(0) instanceof ScrollView) {
      ScrollView page = (ScrollView) body.getChildAt(0);
      int y = pagePositions.getOrDefault(currentKey, 0);
      page.post(
          () -> {
            if (!destroyed) page.scrollTo(0, y);
          });
    }
    body.animate().cancel();
    body.setAlpha(1);
    body.setTranslationY(0);
    body.setTranslationX(0);
    if (motion() && !tab.equals(lastRenderedTab)) {
      body.setAlpha(0);
      int previous = tabOrder(lastRenderedTab), next = tabOrder(tab);
      body.setTranslationX(dp(next > previous ? -10 : 10));
      body.animate()
          .alpha(1)
          .translationX(0)
          .setDuration(210)
          .setInterpolator(new android.view.animation.DecelerateInterpolator())
          .start();
    }
    lastRenderedTab = tab;
    lastScreenKey = currentKey;
  }

  int tabOrder(String value) {
    return value.equals("albums")
        ? 1
        : value.equals("rules") ? 2 : value.equals("settings") ? 3 : 0;
  }

  String screenKey() {
    return tab
        + ":"
        + album
        + ":"
        + collection
        + ":"
        + customAlbum
        + ":"
        + grouping
        + ":"
        + search
        + ":"
        + filter;
  }

  void rememberPosition() {
    if (lastScreenKey.isEmpty() || body == null) return;
    if (timeline != null
        && timeline.getParent() == body
        && timeline.getLayoutManager() != null
        && !media.isEmpty()
        && timeline.isLaidOut())
      timelinePositions.put(lastScreenKey, timeline.getLayoutManager().onSaveInstanceState());
    else if (body.getChildCount() > 0
        && body.getChildAt(0) instanceof ScrollView
        && body.getChildAt(0).isLaidOut()
        && (!tab.equals("albums") || !media.isEmpty()))
      pagePositions.put(lastScreenKey, body.getChildAt(0).getScrollY());
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    rememberPosition();
    out.putString("lume.tab", tab);
    out.putString("lume.album", album);
    out.putString("lume.collection", collection);
    out.putString("lume.custom", customAlbum);
    out.putString("lume.grouping", grouping);
    out.putString("lume.search", search);
    out.putInt("lume.filter", filter);
    out.putParcelable("lume.timeline", timelinePositions.get(screenKey()));
    out.putInt("lume.page", pagePositions.getOrDefault(screenKey(), 0));
    super.onSaveInstanceState(out);
  }

  void aboutBrand() {
    final AlertDialog[] popup = {null};
    LinearLayout content = column();
    content.setPadding(dp(24), dp(24), dp(24), dp(24));
    content.setBackground(rounded(bg, 28));
    ImageView logo = new ImageView(this);
    logo.setImageDrawable(new BrandMark(getResources()));
    LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(78), dp(78));
    logoLp.gravity = Gravity.CENTER;
    content.addView(logo, logoLp);
    TextView name = label("Lume Gallery", 27, ink, true);
    name.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
    nameLp.topMargin = dp(16);
    content.addView(name, nameLp);
    content.addView(
        text("הגלריה שלך. האור שלך.\nגרסה 8.0.0 · עיבוד מקומי ועריכה ששומרת את המקור.", 14));
    action(
        content,
        "Lume AI · כלים חכמים",
        () -> {
          if (popup[0] != null) popup[0].dismiss();
          if (ai != null) ai.showManager();
        });
    action(
        content,
        "הגדרות ותצוגה",
        () -> {
          if (popup[0] != null) popup[0].dismiss();
          tab = "settings";
          render();
        });
    popup[0] =
        new PrivacyDialogBuilder(this).setView(content).setPositiveButton("סגירה", null).show();
  }

  String collectionTitle() {
    if (collection.startsWith("smart:")) {
      SmartCollections.Category category = SmartCollections.find(collection.substring(6));
      return category == null ? "קטגוריה חכמה" : category.title;
    }
    if (!customAlbum.isEmpty()) return customAlbum;
    if (!album.isEmpty()) {
      String[] p = album.split("/");
      return p[p.length - 1];
    }
    switch (collection) {
      case "favorites":
        return "מועדפים";
      case "videos":
        return "סרטונים";
      case "screenshots":
        return "צילומי מסך";
      case "whatsapp":
        return "WhatsApp";
      case "archive":
        return "ארכיון";
      case "duplicates":
        return "כפילויות זהות";
      case "similar":
        return "תמונות דומות";
      default:
        return "תמונות";
    }
  }

  void drawNav() {
    nav.removeAllViews();
    nav.setPadding(dp(8), dp(8), dp(8), dp(8));
    GradientDrawable shell = rounded(card, 26);
    shell.setStroke(dp(1), prefs.getBoolean("light", true) ? 0xffeae5f4 : 0xff332d47);
    nav.setBackground(shell);
    nav.setElevation(dp(5));
    String[][] items = {
      {"media", "תמונות", "photos"},
      {"albums", "אוספים", "albums"},
      {"rules", "ניקוי", "clean"},
      {"settings", "הגדרות", "settings"}
    };
    for (String[] item : items) {
      boolean active = tab.equals(item[0]);
      LinearLayout cell = column();
      cell.setGravity(Gravity.CENTER);
      FrameLayout pill = new FrameLayout(this);
      pill.setBackground(
          rounded(
              active
                  ? (prefs.getBoolean("light", true) ? 0xffeee7fc : 0xff38304e)
                  : Color.TRANSPARENT,
              24));
      ImageView image = new ImageView(this);
      image.setImageDrawable(new GalleryIcons(item[2], active ? accent : muted));
      pill.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
      cell.addView(pill, new LinearLayout.LayoutParams(dp(54), dp(32)));
      TextView caption = label(item[1], 11, active ? accent : muted, active);
      caption.setMaxLines(1);
      caption.setEllipsize(TextUtils.TruncateAt.END);
      if (active && motion() && !tab.equals(lastRenderedTab)) {
        pill.setScaleX(.90f);
        pill.setScaleY(.90f);
        pill.animate()
            .scaleX(1)
            .scaleY(1)
            .setDuration(200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
      }
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
      cp.topMargin = dp(4);
      cell.addView(caption, cp);
      cell.setFocusable(true);
      cell.setContentDescription(item[1]);
      cell.setOnClickListener(
          v -> {
            feedback(v);
            if (tab.equals(item[0])) {
              if (timeline != null && timeline.getParent() == body) {
                if (motion()) timeline.smoothScrollToPosition(0);
                else timeline.scrollToPosition(0);
              } else if (body.getChildCount() > 0 && body.getChildAt(0) instanceof ScrollView) {
                ScrollView page = (ScrollView) body.getChildAt(0);
                if (motion()) page.smoothScrollTo(0, 0);
                else page.scrollTo(0, 0);
              }
              return;
            }
            tab = item[0];
            collection = "";
            customAlbum = "";
            album = "";
            filter = 0;
            search = "";
            selected.clear();
            selecting = false;
            render();
          });
      nav.addView(cell, new LinearLayout.LayoutParams(0, dp(59), 1));
    }
  }

  void displayMenu() {
    new PrivacyDialogBuilder(this)
        .setTitle("תצוגה וארגון")
        .setItems(
            new String[] {
              "בחירת פריטים",
              "רשת מרווחת · 2 עמודות",
              "רשת רגילה · 3 עמודות",
              "רשת צפופה · 5 עמודות",
              sortOld ? "חדשים תחילה" : "ישנים תחילה",
              "רענון מדיה",
              "ניהול הרשאות",
              "AI מקומי · מודלים וכלים"
            },
            (d, which) -> {
              if (which == 0) {
                tab = "media";
                selecting = true;
              } else if (which <= 3)
                prefs.edit().putInt("columns", which == 1 ? 2 : which == 2 ? 3 : 5).apply();
              else if (which == 4) {
                sortOld = !sortOld;
                prefs.edit().putBoolean("old", sortOld).apply();
              } else if (which == 5) {
                load();
                return;
              } else if (which == 6) {
                permission();
                return;
              } else {
                if (ai != null) ai.showManager();
                return;
              }
              render();
            })
        .show();
  }

  void openCollection(String key) {
    filter = 0;
    collection = key;
    customAlbum = "";
    album = "";
    tab = "media";
    search = "";
    selected.clear();
    selecting = false;
    render();
  }

  boolean inCollection(Media m) {
    String key = m.uri.toString();
    if (collection.startsWith("smart:")) {
      SmartCollections.Category category = SmartCollections.find(collection.substring(6));
      return category != null
          && !archived.contains(key)
          && ai != null
          && category.matches(m, ai.cached.get(key));
    }
    if (collection.equals("similar")) return similarScores.containsKey(key);
    if (collection.equals("duplicates")) return duplicates.contains(key);
    boolean isArchived = archived.contains(key);
    if (collection.equals("archive")) return isArchived;
    if (isArchived
        && album.isEmpty()
        && customAlbum.isEmpty()
        && collection.isEmpty()
        && search.trim().isEmpty()) return false;
    switch (collection) {
      case "favorites":
        return favorites.contains(key);
      case "videos":
        return m.video();
      case "screenshots":
        return (m.path + " " + m.name).toLowerCase(Locale.ROOT).contains("screenshot");
      case "whatsapp":
        return (m.path + " " + m.name).toLowerCase(Locale.ROOT).contains("whatsapp");
    }
    return true;
  }

  JSONArray personalAlbums() {
    try {
      return new JSONArray(PrivateData.read(this, "personalAlbums", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  Set<String> albumMembers(String name) {
    JSONArray albums = personalAlbums();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < albums.length(); i++) {
      JSONObject a = albums.optJSONObject(i);
      if (a != null && a.optString("name").equals(name)) {
        JSONArray members = a.optJSONArray("items");
        if (members != null)
          for (int j = 0; j < members.length(); j++) result.add(members.optString(j));
      }
    }
    return result;
  }

  ArrayList<Media> filtered() {
    ArrayList<Media> out = new ArrayList<>();
    Set<String> members =
        customAlbum.isEmpty() ? Collections.emptySet() : albumMembers(customAlbum);
    String needle = search.trim().toLowerCase(Locale.ROOT);
    for (Media m : media) {
      if (!inCollection(m)) continue;
      if (!album.isEmpty() && !album.equals(m.path)) continue;
      if (!customAlbum.isEmpty() && !members.contains(m.uri.toString())) continue;
      if (filter == 1 && m.video()
          || filter == 2 && !m.video()
          || filter == 3 && !favorites.contains(m.uri.toString())) continue;
      if (!needle.isEmpty()) {
        String match =
            (m.name
                    + " "
                    + m.path
                    + " "
                    + new SimpleDateFormat("yyyy-MM-dd MMMM", new Locale("he"))
                        .format(new Date(m.date)))
                .toLowerCase(Locale.ROOT);
        if (!match.contains(needle) && (ai == null || !ai.search(m, needle))) continue;
      }
      out.add(m);
    }
    out.sort(
        (a, b) ->
            collection.equals("similar")
                ? Double.compare(
                    similarScores.getOrDefault(b.uri.toString(), 0d),
                    similarScores.getOrDefault(a.uri.toString(), 0d))
                : sortOld ? Long.compare(a.date, b.date) : Long.compare(b.date, a.date));
    return out;
  }

  @Override
  void gallery() {
    if (!album.isEmpty() || !collection.isEmpty() || !customAlbum.isEmpty()) {
      LinearLayout tools = row();
      tools.setPadding(dp(16), 0, dp(16), dp(6));
      tools.addView(
          iconButton(
              "back",
              "לכל האוספים",
              () -> {
                tab = "albums";
                collection = "";
                customAlbum = "";
                album = "";
                selected.clear();
                selecting = false;
                render();
              }));
      tools.addView(
          label(
              collection.equals("archive")
                  ? "מוסתר מהתצוגה הראשית · אינו נעול"
                  : !customAlbum.isEmpty() ? "אלבום אישי · הקבצים נשארים במיקומם" : "האוסף שלך",
              12,
              muted,
              false),
          new LinearLayout.LayoutParams(0, -2, 1));
      if (!customAlbum.isEmpty())
        tools.addView(iconButton("more", "ניהול אלבום", this::manageAlbum));
      body.addView(tools);
    }
    LinearLayout chips = row();
    chips.setPadding(dp(16), dp(2), dp(12), dp(6));
    HorizontalScrollView groupingScroll = new HorizontalScrollView(this);
    groupingScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout groupingRow = row();
    String[][] groups = {{"days", "ימים"}, {"months", "חודשים"}, {"years", "שנים"}};
    for (String[] group : groups) {
      TextView chip = galleryChip(group[1], grouping.equals(group[0]));
      chip.setOnClickListener(
          v -> {
            grouping = group[0];
            render();
          });
      groupingRow.addView(chip);
    }
    groupingScroll.addView(groupingRow);
    chips.addView(groupingScroll, new LinearLayout.LayoutParams(0, -2, 1));
    chips.addView(iconButton("clock", "קפיצה לתאריך", this::dateJumpDialog));
    chips.addView(
        iconButton(
            "select",
            "בחירה מרובה",
            () -> {
              selecting = !selecting;
              selected.clear();
              render();
            }));
    body.addView(chips);
    HorizontalScrollView quick = new HorizontalScrollView(this);
    quick.setHorizontalScrollBarEnabled(false);
    quick.setContentDescription("סינון בתוך האוסף הנוכחי");
    LinearLayout filters = row();
    filters.setPadding(dp(16), 0, dp(16), dp(8));
    String[] labels = {"הכול", "תמונות", "סרטונים", "מועדפים"};
    for (int i = 0; i < labels.length; i++) {
      final int value = i;
      TextView chip = galleryChip(labels[i], filter == i);
      chip.setOnClickListener(v -> setQuickFilter(value));
      filters.addView(chip);
    }
    quick.addView(filters);
    body.addView(quick);
    timeline = new RecyclerView(this);
    int cols = prefs.getInt("columns", 3);
    GridLayoutManager layout = new GridLayoutManager(this, cols);
    layout.setSpanSizeLookup(
        new GridLayoutManager.SpanSizeLookup() {
          public int getSpanSize(int position) {
            return timelineItems.get(position) instanceof String ? cols : 1;
          }
        });
    timeline.setLayoutManager(layout);
    timeline.setItemAnimator(null);
    timeline.setPadding(dp(2), 0, dp(2), dp(12));
    timeline.setClipToPadding(false);
    timelineAdapter = new TimelineAdapter();
    timeline.setAdapter(timelineAdapter);
    body.addView(timeline, new LinearLayout.LayoutParams(-1, 0, 1));
    selectionBar = row();
    selectionBar.setPadding(dp(14), dp(8), dp(14), dp(8));
    body.addView(selectionBar);
    updateTimeline();
    updateSelectionBar();
    if (media.isEmpty()) {
      LinearLayout empty = column();
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(dp(32), dp(20), dp(32), dp(28));
      empty.addView(icon("photos", accent, 48));
      TextView h = label("הזיכרונות שלך מתחילים כאן", 21, ink, true);
      h.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
      p.topMargin = dp(16);
      empty.addView(h, p);
      TextView hint = label("אפשר גישה לתמונות ולסרטונים כדי לראות אותם בגלריה.", 14, muted, false);
      hint.setGravity(Gravity.CENTER);
      hint.setPadding(0, dp(10), 0, dp(18));
      empty.addView(hint);
      empty.addView(button("בחירת תמונות וגישה לגלריה", this::permission));
      body.addView(empty, 0);
    }
  }

  TextView galleryChip(String label, boolean active) {
    TextView chip = this.label(label, 13, active ? accent : muted, active);
    chip.setGravity(Gravity.CENTER);
    chip.setPadding(dp(16), dp(8), dp(16), dp(8));
    chip.setMinHeight(dp(48));
    chip.setFocusable(true);
    chip.setSelected(active);
    chip.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x226d52c7),
            rounded(active ? card : Color.TRANSPARENT, 20),
            null));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.setMargins(0, 0, dp(4), 0);
    chip.setLayoutParams(lp);
    return chip;
  }

  void setQuickFilter(int value) {
    filter = Math.max(0, Math.min(3, value));
    selected.clear();
    selecting = false;
    render();
  }

  int toggleDateGroup(String heading) {
    java.util.List<Media> group = dateGroups.getOrDefault(heading, new ArrayList<>());
    boolean all =
        !group.isEmpty() && group.stream().allMatch(m -> selected.contains(m.uri.toString()));
    if (group.isEmpty()) return selected.size();
    for (Media m : group)
      if (all) selected.remove(m.uri.toString());
      else selected.add(m.uri.toString());
    selecting = true;
    render();
    return selected.size();
  }

  boolean dateGroupSelected(String heading) {
    java.util.List<Media> group = dateGroups.get(heading);
    return group != null
        && !group.isEmpty()
        && group.stream().allMatch(m -> selected.contains(m.uri.toString()));
  }

  int nearestDatePosition(long date) {
    int best = -1;
    long distance = Long.MAX_VALUE;
    for (int i = 0; i < timelineItems.size(); i++)
      if (timelineItems.get(i) instanceof Media) {
        Media m = (Media) timelineItems.get(i);
        long difference = Math.abs(m.date - date);
        if (difference < distance) {
          distance = difference;
          best = i;
        }
      }
    if (best < 0) return -1;
    while (best > 0 && !(timelineItems.get(best) instanceof String)) best--;
    return best;
  }

  void dateJumpDialog() {
    if (visible.isEmpty()) {
      message("קפיצה לתאריך", "אין תמונות באוסף או במסנן הנוכחי");
      return;
    }
    Calendar initial = Calendar.getInstance();
    initial.setTimeInMillis(visible.get(0).date);
    DatePickerDialog dialog =
        new DatePickerDialog(
            this,
            (picker, year, month, day) -> {
              Calendar target = Calendar.getInstance();
              target.clear();
              target.set(year, month, day, 12, 0);
              int position = nearestDatePosition(target.getTimeInMillis());
              if (position >= 0 && timeline != null)
                ((GridLayoutManager) timeline.getLayoutManager())
                    .scrollToPositionWithOffset(position, 0);
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH));
    dialog.setTitle("קפיצה לתמונה הקרובה לתאריך");
    AppPrivacy.apply(dialog.getWindow(), this);
    dialog.show();
  }

  String dateHeading(long time) {
    Date date = new Date(time);
    if (grouping.equals("years")) return new SimpleDateFormat("yyyy", Locale.ROOT).format(date);
    if (grouping.equals("months"))
      return new SimpleDateFormat("MMMM yyyy", new Locale("he")).format(date);
    Calendar today = Calendar.getInstance(), then = Calendar.getInstance();
    then.setTimeInMillis(time);
    if (today.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        && today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "היום";
    today.add(Calendar.DAY_OF_YEAR, -1);
    if (today.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        && today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "אתמול";
    return new SimpleDateFormat("d בMMMM yyyy", new Locale("he")).format(date);
  }

  void updateTimeline() {
    visible = filtered();
    timelineItems.clear();
    dateGroups.clear();
    String last = "";
    for (Media m : visible) {
      String h = dateHeading(m.date);
      dateGroups.computeIfAbsent(h, k -> new ArrayList<>()).add(m);
      if (!h.equals(last)) {
        timelineItems.add(h);
        last = h;
      }
      timelineItems.add(m);
    }
    if (visible.isEmpty() && !media.isEmpty()) timelineItems.add("לא נמצאו פריטים באוסף הזה");
    if (timelineAdapter != null) timelineAdapter.notifyDataSetChanged();
    if (status != null) status.setText(visible.size() + " פריטים · במכשיר");
  }

  void thumbnail(ImageView image, Media m, int size) {
    String key = m.uri.toString();
    image.setTag(key);
    image.setImageDrawable(null);
    image.setBackgroundColor(card);
    Bitmap b = cache.get(key);
    if (b != null) {
      image.setImageBitmap(b);
      return;
    }
    thumbs.execute(
        () -> {
          try {
            Bitmap bitmap =
                getContentResolver().loadThumbnail(m.uri, new android.util.Size(size, size), null);
            cache.put(key, bitmap);
            runOnUiThread(
                () -> {
                  if (!destroyed && key.equals(image.getTag())) image.setImageBitmap(bitmap);
                });
          } catch (Exception ignored) {
          }
        });
  }

  final class TileHolder extends RecyclerView.ViewHolder {
    TileHolder(View v) {
      super(v);
    }
  }

  final class TimelineAdapter extends RecyclerView.Adapter<TileHolder> {
    @Override
    public int getItemViewType(int p) {
      return timelineItems.get(p) instanceof String ? 0 : 1;
    }

    @Override
    public TileHolder onCreateViewHolder(android.view.ViewGroup p, int type) {
      if (type == 0) {
        TextView text = label("", 16, ink, true);
        text.setPadding(dp(22), dp(22), dp(22), dp(11));
        text.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return new TileHolder(text);
      }
      FrameLayout tile = new FrameLayout(ModernActivity.this);
      int side =
          (getResources().getDisplayMetrics().widthPixels - dp(4)) / prefs.getInt("columns", 3);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, side);
      lp.setMargins(dp(1), dp(1), dp(1), dp(1));
      tile.setLayoutParams(lp);
      return new TileHolder(tile);
    }

    @Override
    public void onBindViewHolder(TileHolder holder, int p) {
      Object item = timelineItems.get(p);
      if (item instanceof String) {
        String heading = (String) item;
        TextView label = (TextView) holder.itemView;
        label.setText(
            heading
                + (selecting
                    ? (dateGroupSelected(heading) ? " · בטל בחירה" : " · בחר קבוצה")
                    : ""));
        label.setMinHeight(dp(48));
        label.setFocusable(true);
        label.setTextColor(selecting ? accent : ink);
        label.setContentDescription(heading + " · בחירת כל הפריטים בקבוצה או ביטול בחירתם");
        label.setOnClickListener(v -> toggleDateGroup(heading));
        label.setOnLongClickListener(
            v -> {
              toggleDateGroup(heading);
              return true;
            });
        return;
      }
      Media m = (Media) item;
      FrameLayout tile = (FrameLayout) holder.itemView;
      tile.removeAllViews();
      boolean checked = selected.contains(m.uri.toString());
      ImageView photo = new ImageView(ModernActivity.this);
      photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
      FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(-1, -1);
      if (checked) imageLp.setMargins(dp(5), dp(5), dp(5), dp(5));
      tile.setBackground(rounded(accent, 6));
      tile.addView(photo, imageLp);
      thumbnail(photo, m, 360);
      if (m.video()) {
        TextView duration =
            label(
                m.duration > 0
                    ? String.format(
                        Locale.ROOT, "%d:%02d", m.duration / 60000, (m.duration / 1000) % 60)
                    : "וידאו",
                11,
                Color.WHITE,
                true);
        duration.setPadding(dp(6), dp(3), dp(6), dp(3));
        duration.setShadowLayer(3, 0, 1, Color.BLACK);
        FrameLayout.LayoutParams durationLp =
            new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        durationLp.setMargins(dp(5), dp(5), dp(5), dp(5));
        tile.addView(duration, durationLp);
      }
      if (favorites.contains(m.uri.toString()) && !selecting) {
        ImageView heart = new ImageView(ModernActivity.this);
        heart.setImageDrawable(new GalleryIcons("heart", Color.WHITE));
        FrameLayout.LayoutParams h =
            new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.BOTTOM | Gravity.START);
        h.setMargins(dp(7), dp(7), dp(7), dp(7));
        tile.addView(heart, h);
      }
      if (selecting) {
        ImageView tick = new ImageView(ModernActivity.this);
        tick.setPadding(dp(4), dp(4), dp(4), dp(4));
        tick.setImageDrawable(new GalleryIcons(checked ? "check" : "select", Color.WHITE));
        tick.setBackground(rounded(checked ? accent : 0x55000000, 30));
        FrameLayout.LayoutParams pos =
            new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.TOP | Gravity.END);
        pos.setMargins(dp(7), dp(7), dp(7), dp(7));
        tile.addView(tick, pos);
      }
      pressAnimation(tile);
      tile.setContentDescription(m.name + (checked ? " נבחר" : ""));
      tile.setOnClickListener(
          v -> {
            if (selecting) {
              feedback(v);
              toggle(m);
            } else viewer(m);
          });
      tile.setOnLongClickListener(
          v -> {
            feedback(v);
            selecting = true;
            toggle(m);
            render();
            return true;
          });
    }

    @Override
    public int getItemCount() {
      return timelineItems.size();
    }
  }

  void toggle(Media m) {
    String key = m.uri.toString();
    if (!selected.add(key)) selected.remove(key);
    if (title != null) title.setText(selected.size() + " נבחרו");
    if (timelineAdapter != null) timelineAdapter.notifyDataSetChanged();
    updateSelectionBar();
  }

  void updateSelectionBar() {
    if (selectionBar == null) return;
    selectionBar.removeAllViews();
    selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
    if (!selecting) return;
    addTool(
        selectionBar,
        "select",
        !visible.isEmpty() && visible.stream().allMatch(m -> selected.contains(m.uri.toString()))
            ? "בטל"
            : "הכול",
        this::toggleVisibleSelection);
    addTool(selectionBar, "share", "שיתוף", () -> share(new ArrayList<>(selected)));
    addTool(selectionBar, "plus", "לאלבום", () -> chooseAlbum(new ArrayList<>(selected)));
    addTool(selectionBar, "trash", "מחיקה", () -> trash(new ArrayList<>(selected)));
    addTool(selectionBar, "more", "עוד", this::selectionMore);
  }

  int toggleVisibleSelection() {
    boolean all =
        !visible.isEmpty() && visible.stream().allMatch(m -> selected.contains(m.uri.toString()));
    for (Media m : visible)
      if (all) selected.remove(m.uri.toString());
      else selected.add(m.uri.toString());
    title.setText(selected.size() + " נבחרו");
    timelineAdapter.notifyDataSetChanged();
    updateSelectionBar();
    return selected.size();
  }

  void addTool(LinearLayout parent, String icon, String caption, Runnable click) {
    LinearLayout tool = column();
    tool.setGravity(Gravity.CENTER);
    tool.addView(icon(icon, ink, 23));
    TextView label = label(caption, 11, ink, false);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.topMargin = dp(6);
    tool.addView(label, lp);
    tool.setPadding(0, dp(8), 0, dp(8));
    tool.setMinimumHeight(dp(52));
    tool.setContentDescription(caption);
    tool.setFocusable(true);
    tool.setOnClickListener(v -> click.run());
    parent.addView(tool, new LinearLayout.LayoutParams(0, -2, 1));
  }

  void selectionMore() {
    new PrivacyDialogBuilder(this)
        .setItems(
            new String[] {
              "הוסף למועדפים",
              collection.equals("archive") ? "הוצא מארכיון" : "העבר לארכיון",
              "הסר מהאלבום האישי"
            },
            (d, n) -> {
              if (selected.isEmpty()) return;
              if (n == 0) {
                favorites.addAll(selected);
                prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
              } else if (n == 1) {
                if (collection.equals("archive")) archived.removeAll(selected);
                else archived.addAll(selected);
                prefs.edit().putStringSet("archive", new HashSet<>(archived)).apply();
              } else {
                if (customAlbum.isEmpty()) {
                  message("אלבום אישי", "פתח אלבום אישי כדי להסיר ממנו פריטים");
                  return;
                }
                removeFromAlbum(customAlbum, selected);
              }
              selected.clear();
              selecting = false;
              render();
            })
        .show();
  }

  void share(ArrayList<String> ids) {
    if (ids.isEmpty()) {
      message("בחירת פריטים", "בחר לפחות פריט אחד");
      return;
    }
    ArrayList<Uri> uris = new ArrayList<>();
    for (String id : ids) uris.add(Uri.parse(id));
    Intent send =
        new Intent(ids.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ClipData clip = ClipData.newRawUri("Lume", uris.get(0));
    for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
    send.setClipData(clip);
    if (ids.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
    else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    startActivity(Intent.createChooser(send, "שיתוף מדיה"));
  }

  void trash(ArrayList<String> ids) {
    if (ids.isEmpty()) return;
    if (ids.size() > 2000) {
      message("בחירה גדולה", "אפשר להעביר עד 2000 פריטים בכל פעולה");
      return;
    }
    ArrayList<Uri> uris = new ArrayList<>();
    for (String id : ids) uris.add(Uri.parse(id));
    try {
      startIntentSenderForResult(
          MediaStore.createTrashRequest(getContentResolver(), uris, true).getIntentSender(),
          32,
          null,
          0,
          0,
          0);
    } catch (Exception e) {
      message("לא ניתן למחוק", e.getMessage());
    }
  }

  @Override
  LinearLayout scroll() {
    ScrollView scroll = new ScrollView(this);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout content = column();
    scroll.addView(content);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    return content;
  }

  @Override
  void albums() {
    int columns = albumColumns();
    LinearLayout c = scroll();
    c.setPadding(dp(18), dp(2), dp(18), dp(22));
    LinearLayout pinned = row();
    compactShortcut(pinned, "heart", "מועדפים", () -> openCollection("favorites"));
    compactShortcut(pinned, "people", "אנשים", () -> peoplePlaces.open("person"));
    compactShortcut(pinned, "place", "מקומות", () -> peoplePlaces.open("place"));
    c.addView(pinned);
    LinearLayout smart = row();
    smart.setPadding(dp(16), dp(14), dp(16), dp(14));
    GradientDrawable glow =
        new GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            prefs.getBoolean("light", true)
                ? new int[] {0xffeee8fb, 0xffe6f6f8}
                : new int[] {0xff302743, 0xff18313b});
    glow.setCornerRadius(dp(22));
    smart.setBackground(glow);
    ImageView emblem = new ImageView(this);
    emblem.setImageDrawable(new BrandMark(getResources()));
    smart.addView(emblem, new LinearLayout.LayoutParams(dp(40), dp(40)));
    LinearLayout words = column();
    words.addView(label("Lume AI", 18, ink, true));
    TextView supporting =
        label(
            ai == null
                ? "כלים חכמים במכשיר"
                : ai.installedSummary().replace("Lume AI · ", "").replace("Lume AI ", ""),
            12,
            muted,
            false);
    supporting.setMaxLines(2);
    LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1);
    wordsLp.setMargins(dp(12), 0, dp(12), 0);
    words.addView(supporting);
    smart.addView(words, wordsLp);
    smart.addView(icon("clean", accent, 23));
    smart.setOnClickListener(
        v -> {
          feedback(v);
          if (ai != null) ai.showManager();
        });
    pressAnimation(smart);
    LinearLayout.LayoutParams smartLp = new LinearLayout.LayoutParams(-1, -2);
    smartLp.setMargins(dp(4), dp(12), dp(4), dp(4));
    c.addView(smart, smartLp);
    LinearLayout heading = row();
    TextView personal = label("האלבומים שלי", 22, ink, true);
    heading.addView(personal, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(iconButton("plus", "צור אלבום אישי", () -> newAlbum(Collections.emptyList())));
    LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
    headingLp.topMargin = dp(18);
    c.addView(heading, headingLp);
    JSONArray albums = personalAlbums();
    LinearLayout pair = null;
    int cells = 0;
    for (int i = 0; i < albums.length(); i++) {
      JSONObject item = albums.optJSONObject(i);
      if (item == null || item.has("kind")) continue;
      if (cells % columns == 0) {
        pair = row();
        pair.setGravity(Gravity.TOP);
        c.addView(pair);
      }
      String name = item.optString("name");
      Set<String> ids = albumMembers(name);
      Media cover = null;
      int count = 0;
      for (Media m : media)
        if (ids.contains(m.uri.toString())) {
          count++;
          if (cover == null || m.uri.toString().equals(item.optString("cover"))) cover = m;
        }
      albumCard(pair, name, count, cover, () -> openNamedAlbum(name));
      cells++;
    }
    while (cells % columns != 0) {
      pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      cells++;
    }
    if (cells == 0) {
      TextView empty = label("אלבומים אישיים שתיצור יופיעו כאן.", 13, muted, false);
      empty.setPadding(0, dp(2), 0, dp(14));
      c.addView(empty);
    }
    TextView folders = label("במכשיר הזה", 22, ink, true);
    folders.setPadding(0, dp(18), 0, dp(12));
    c.addView(folders);
    LinkedHashMap<String, ArrayList<Media>> paths = new LinkedHashMap<>();
    for (Media m : media)
      paths.computeIfAbsent(m.path == null ? "" : m.path, k -> new ArrayList<>()).add(m);
    cells = 0;
    pair = null;
    for (var entry : paths.entrySet()) {
      if (cells % columns == 0) {
        pair = row();
        pair.setGravity(Gravity.TOP);
        c.addView(pair);
      }
      String path = entry.getKey();
      String[] pieces = path.split("/");
      String name = pieces.length == 0 ? "מדיה" : pieces[pieces.length - 1];
      if (name.equalsIgnoreCase("Camera")) name = "מצלמה";
      else if (name.equalsIgnoreCase("Screenshots")) name = "צילומי מסך";
      else if (name.equalsIgnoreCase("Luma") || name.equalsIgnoreCase("Lume"))
        name = "Lume Gallery";
      albumCard(
          pair,
          name,
          entry.getValue().size(),
          entry.getValue().get(0),
          () -> {
            filter = 0;
            album = path;
            customAlbum = "";
            collection = "";
            tab = "media";
            search = "";
            render();
          });
      cells++;
    }
    while (cells % columns != 0) {
      pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      cells++;
    }
    if (cells == 0) c.addView(text("אפשר גישה לתמונות כדי להציג את תיקיות המכשיר.", 13));
    drawSmartCollections(c);
    TextView types = label("סוגי מדיה וארגון", 22, ink, true);
    types.setPadding(0, dp(22), 0, dp(12));
    c.addView(types);
    LinearLayout first = row();
    shortcut(first, "video", "סרטונים", countKind("video"), () -> openCollection("videos"));
    shortcut(
        first,
        "screenshot",
        "צילומי מסך",
        countKind("screenshot"),
        () -> openCollection("screenshots"));
    c.addView(first);
    LinearLayout second = row();
    shortcut(second, "folder", "WhatsApp", countKind("whatsapp"), () -> openCollection("whatsapp"));
    shortcut(second, "archive", "ארכיון", archived.size(), () -> openCollection("archive"));
    c.addView(second);
    LinearLayout utilities = row();
    compactShortcut(utilities, "trash", "סל שחזור", this::trashScreen);
    compactShortcut(
        utilities,
        "clean",
        "ניקוי חכם",
        () -> {
          tab = "rules";
          render();
        });
    c.addView(utilities);
    Calendar now = Calendar.getInstance(), date = Calendar.getInstance();
    for (Media m : media) {
      date.setTimeInMillis(m.date);
      if (!archived.contains(m.uri.toString())
          && date.get(Calendar.YEAR) < now.get(Calendar.YEAR)
          && date.get(Calendar.MONTH) == now.get(Calendar.MONTH)
          && date.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)) {
        LinearLayout memory = row();
        memory.setPadding(dp(14), dp(14), dp(14), dp(14));
        memory.setBackground(rounded(card, 20));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        memory.addView(image, new LinearLayout.LayoutParams(dp(70), dp(70)));
        thumbnail(image, m, 200);
        TextView copy = label("ביום הזה\nרגע מהשנים הקודמות", 16, ink, true);
        copy.setPadding(dp(14), 0, dp(14), 0);
        memory.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        memory.setOnClickListener(v -> viewer(m));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(18);
        c.addView(memory, lp);
        break;
      }
    }
  }

  int albumColumns() {
    int requested = prefs.getInt("albumColumns", 3) == 2 ? 2 : 3;
    return getResources().getConfiguration().screenWidthDp < 360
            && getResources().getConfiguration().fontScale > 1.3f
        ? 2
        : requested;
  }

  void drawSmartCollections(LinearLayout c) {
    TextView heading = label("קטגוריות חכמות", 22, ink, true);
    heading.setPadding(0, dp(22), 0, dp(6));
    c.addView(heading);
    TextView hint = label("קיבוץ משוער לפי תוכן · עיבוד מקומי", 12, muted, false);
    hint.setPadding(0, 0, 0, dp(10));
    c.addView(hint);
    Map<SmartCollections.Category, ArrayList<Media>> groups =
        SmartCollections.groups(media, ai == null ? Collections.emptyMap() : ai.cached, archived);
    int cells = 0, columns = albumColumns();
    LinearLayout row = null;
    for (var entry : groups.entrySet()) {
      if (entry.getValue().isEmpty()) continue;
      if (cells % columns == 0) {
        row = row();
        row.setGravity(Gravity.TOP);
        c.addView(row);
      }
      SmartCollections.Category category = entry.getKey();
      albumCard(
          row,
          category.title,
          entry.getValue().size(),
          entry.getValue().get(0),
          () -> openCollection("smart:" + category.id));
      cells++;
    }
    while (cells % columns != 0) {
      row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      cells++;
    }
    if (cells == 0)
      c.addView(
          text(
              "לאחר סריקת Lume AI יופיעו כאן תמונות לפי תוכן. הזיהוי עשוי לטעות ואפשר לצפות תמיד"
                  + " בכל הקבצים המקוריים.",
              13));
    action(
        c,
        "סרוק קטגוריות במכשיר",
        () -> {
          if (ai != null && ai.require("classify")) ai.startIndex();
        });
  }

  void compactShortcut(LinearLayout parent, String glyph, String caption, Runnable click) {
    LinearLayout cell = column();
    cell.setGravity(Gravity.CENTER);
    cell.setPadding(dp(6), dp(13), dp(6), dp(13));
    cell.setBackground(
        new RippleDrawable(ColorStateList.valueOf(0x226d52c7), rounded(card, 20), null));
    FrameLayout symbol = new FrameLayout(this);
    symbol.setBackground(rounded(prefs.getBoolean("light", true) ? 0xfff1ecfb : 0xff302a44, 16));
    ImageView image = new ImageView(this);
    image.setImageDrawable(new GalleryIcons(glyph, accent));
    symbol.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
    cell.addView(symbol, new LinearLayout.LayoutParams(dp(44), dp(40)));
    TextView label = label(caption, 13, ink, true);
    label.setMaxLines(2);
    label.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
    labelLp.topMargin = dp(9);
    cell.addView(label, labelLp);
    cell.setContentDescription(caption);
    cell.setFocusable(true);
    cell.setOnClickListener(
        v -> {
          feedback(v);
          click.run();
        });
    pressAnimation(cell);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(dp(4), 0, dp(4), dp(4));
    parent.addView(cell, lp);
  }

  int countKind(String kind) {
    int count = 0;
    for (Media m : media) {
      if (kind.equals("video") && m.video()
          || kind.equals("screenshot")
              && (m.path + " " + m.name).toLowerCase(Locale.ROOT).contains("screenshot")
          || kind.equals("whatsapp")
              && (m.path + " " + m.name).toLowerCase(Locale.ROOT).contains("whatsapp")) count++;
    }
    return count;
  }

  void shortcut(LinearLayout parent, String icon, String name, int count, Runnable click) {
    LinearLayout box = column();
    box.setPadding(dp(16), dp(16), dp(16), dp(16));
    box.setBackground(rounded(card, 20));
    box.addView(icon(icon, accent, 24));
    TextView caption = label(name, 15, ink, true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(12);
    box.addView(caption, lp);
    if (count >= 0) {
      TextView sub = label(count + " פריטים", 12, muted, false);
      LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
      subLp.topMargin = dp(3);
      box.addView(sub, subLp);
    }
    box.setOnClickListener(v -> click.run());
    box.setFocusable(true);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
    bp.setMargins(dp(4), dp(5), dp(4), dp(5));
    pressAnimation(box);
    parent.addView(box, bp);
  }

  void albumCard(LinearLayout parent, String name, int count, Media cover, Runnable click) {
    LinearLayout cell = column();
    FrameLayout square = new FrameLayout(this);
    square.setBackground(rounded(card, 22));
    square.setClipToOutline(true);
    int columns = albumColumns();
    int height =
        Math.max(
            dp(64),
            (getResources().getDisplayMetrics().widthPixels - dp(36 + 10 * columns)) / columns);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    square.addView(image, new FrameLayout.LayoutParams(-1, -1));
    if (cover != null) thumbnail(image, cover, 500);
    else {
      ImageView folder = new ImageView(this);
      folder.setImageDrawable(new GalleryIcons("albums", muted));
      square.addView(folder, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
    }
    cell.addView(square, new LinearLayout.LayoutParams(-1, height));
    TextView label = label(name, 15, ink, true);
    label.setMaxLines(2);
    label.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(10);
    cell.addView(label, lp);
    TextView sub = label(count + " פריטים", 12, muted, false);
    LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-1, -2);
    countLp.topMargin = dp(3);
    cell.addView(sub, countLp);
    cell.setOnClickListener(v -> click.run());
    cell.setFocusable(true);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
    cp.setMargins(dp(5), dp(6), dp(5), dp(12));
    pressAnimation(cell);
    parent.addView(cell, cp);
  }

  void searchDialog() {
    tab = "media";
    render();
    searchBox = row();
    searchBox.setPadding(dp(12), dp(3), dp(8), dp(3));
    searchBox.setBackground(rounded(card, 24));
    searchBox.addView(icon("search", muted, 22));
    EditText query = new EditText(this);
    query.setSingleLine(true);
    query.setTextColor(ink);
    query.setTextSize(16);
    query.setHintTextColor(muted);
    query.setBackgroundColor(Color.TRANSPARENT);
    query.setHint("תאריך, שם או תגית AI שאונדקסה");
    query.setText(search);
    query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchBox.addView(query, new LinearLayout.LayoutParams(0, dp(48), 1));
    searchBox.addView(
        iconButton(
            "close",
            "סגירת חיפוש",
            () -> {
              search = "";
              ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(query.getWindowToken(), 0);
              render();
            }));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(22), 0, dp(22), dp(8));
    body.addView(searchBox, 0, lp);
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int n) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            rememberPosition();
            search = s.toString();
            lastScreenKey = screenKey();
            if (pendingSearch != null) ui.removeCallbacks(pendingSearch);
            pendingSearch =
                () -> {
                  if (tab.equals("media")) updateTimeline();
                };
            ui.postDelayed(pendingSearch, 180);
          }

          public void afterTextChanged(Editable e) {}
        });
    query.setOnEditorActionListener(
        (v, a, event) -> {
          ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(query.getWindowToken(), 0);
          query.clearFocus();
          return true;
        });
    query.requestFocus();
    ui.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(query, InputMethodManager.SHOW_IMPLICIT),
        150);
  }

  void chooseAlbum(ArrayList<String> ids) {
    if (ids.isEmpty()) {
      message("בחירת תמונות", "בחר תמונות או סרטונים להוספה לאלבום");
      return;
    }
    JSONArray albums = personalAlbums();
    String[] names = new String[albums.length() + 1];
    names[0] = "אלבום חדש";
    for (int i = 0; i < albums.length(); i++)
      names[i + 1] = albums.optJSONObject(i).optString("name");
    new PrivacyDialogBuilder(this)
        .setTitle("הוספה לאלבום")
        .setItems(
            names,
            (d, n) -> {
              if (n == 0) newAlbum(ids);
              else addToAlbum(names[n], ids);
            })
        .show();
  }

  void newAlbum(java.util.List<String> ids) {
    EditText name = new EditText(this);
    name.setSingleLine();
    name.setHint("שם האלבום");
    name.setPadding(dp(24), dp(16), dp(24), dp(16));
    AlertDialog d =
        new PrivacyDialogBuilder(this)
            .setTitle("אלבום חדש")
            .setView(name)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("יצירה", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(
                    w -> {
                      String value = name.getText().toString().trim();
                      if (value.isEmpty()) {
                        name.setError("הכנס שם לאלבום");
                        return;
                      }
                      JSONArray albums = personalAlbums();
                      for (int i = 0; i < albums.length(); i++)
                        if (albums.optJSONObject(i).optString("name").equals(value)) {
                          name.setError("קיים אלבום בשם הזה");
                          return;
                        }
                      try {
                        albums.put(
                            new JSONObject().put("name", value).put("items", new JSONArray(ids)));
                        prefs
                            .edit()
                            .putString("personalAlbums", PrivateData.encrypt(albums.toString()))
                            .apply();
                        d.dismiss();
                        selected.clear();
                        selecting = false;
                        tab = "albums";
                        render();
                      } catch (Exception e) {
                        message("לא ניתן ליצור אלבום", e.getMessage());
                      }
                    }));
    d.show();
  }

  void addToAlbum(String name, java.util.List<String> ids) {
    try {
      JSONArray albums = personalAlbums();
      for (int i = 0; i < albums.length(); i++) {
        JSONObject a = albums.getJSONObject(i);
        if (a.optString("name").equals(name)) {
          Set<String> members = albumMembers(name);
          members.addAll(ids);
          a.put("items", new JSONArray(members));
        }
      }
      prefs.edit().putString("personalAlbums", PrivateData.encrypt(albums.toString())).apply();
      selected.clear();
      selecting = false;
      render();
      Toast.makeText(this, "הפריטים נוספו לאלבום", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      message("אלבום", e.getMessage());
    }
  }

  void removeFromAlbum(String name, Set<String> ids) {
    try {
      JSONArray albums = personalAlbums();
      for (int i = 0; i < albums.length(); i++) {
        JSONObject a = albums.getJSONObject(i);
        if (a.optString("name").equals(name)) {
          Set<String> members = albumMembers(name);
          members.removeAll(ids);
          a.put("items", new JSONArray(members));
        }
      }
      prefs.edit().putString("personalAlbums", PrivateData.encrypt(albums.toString())).apply();
    } catch (Exception e) {
      message("אלבום", e.getMessage());
    }
  }

  void manageAlbum() {
    String name = customAlbum;
    new PrivacyDialogBuilder(this)
        .setTitle(name)
        .setItems(
            new String[] {"הוספת תמונות", "שינוי שם", "מחיקת האלבום בלבד"},
            (d, n) -> {
              if (n == 0) {
                customAlbum = "";
                collection = "";
                album = "";
                selecting = true;
                render();
                message("הוספה לאלבום", "בחר פריטים, לחץ על לאלבום ובחר את " + name);
              } else if (n == 1) {
                EditText value = new EditText(this);
                value.setText(name);
                new PrivacyDialogBuilder(this)
                    .setTitle("שם האלבום")
                    .setView(value)
                    .setPositiveButton(
                        "שמירה",
                        (a, b) -> {
                          String updated = value.getText().toString().trim();
                          if (updated.isEmpty()) return;
                          try {
                            JSONArray albums = personalAlbums();
                            for (int i = 0; i < albums.length(); i++)
                              if (albums.getJSONObject(i).optString("name").equals(updated)
                                  && !updated.equals(name)) {
                                message("שם תפוס", "קיים כבר אלבום בשם הזה");
                                return;
                              }
                            for (int i = 0; i < albums.length(); i++)
                              if (albums.getJSONObject(i).optString("name").equals(name))
                                albums.getJSONObject(i).put("name", updated);
                            prefs
                                .edit()
                                .putString("personalAlbums", PrivateData.encrypt(albums.toString()))
                                .apply();
                            customAlbum = updated;
                            render();
                          } catch (Exception e) {
                            message("אלבום", e.getMessage());
                          }
                        })
                    .setNegativeButton("ביטול", null)
                    .show();
              } else
                new PrivacyDialogBuilder(this)
                    .setMessage("למחוק את האלבום? התמונות והסרטונים יישארו בגלריה.")
                    .setPositiveButton(
                        "מחק אלבום",
                        (a, b) -> {
                          JSONArray albums = personalAlbums();
                          for (int i = albums.length() - 1; i >= 0; i--)
                            if (albums.optJSONObject(i).optString("name").equals(name))
                              albums.remove(i);
                          prefs
                              .edit()
                              .putString("personalAlbums", PrivateData.encrypt(albums.toString()))
                              .apply();
                          customAlbum = "";
                          tab = "albums";
                          render();
                        })
                    .setNegativeButton("ביטול", null)
                    .show();
            })
        .show();
  }

  void removeRepeatedHeading(int count) {
    if (body.getChildCount() > 0 && body.getChildAt(0) instanceof ScrollView) {
      LinearLayout content = (LinearLayout) ((ScrollView) body.getChildAt(0)).getChildAt(0);
      for (int i = 0; i < count && content.getChildCount() > 0; i++)
        if (content.getChildAt(0) instanceof TextView) content.removeViewAt(0);
    }
  }

  void modernRules() {
    super.rules();
    removeRepeatedHeading(2);
  }

  void modernSettings() {
    LinearLayout c = scroll();
    c.setPadding(0, dp(4), 0, dp(28));
    section(c, "תצוגה ותנועה");
    action(
        c,
        "מראה · " + (prefs.getBoolean("light", true) ? "בהיר" : "כהה"),
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("מראה האפליקציה")
                .setItems(
                    new String[] {"לפי המכשיר", "בהיר", "כהה"},
                    (d, n) -> {
                      prefs
                          .edit()
                          .putString("themeMode", n == 0 ? "system" : n == 1 ? "light" : "dark")
                          .apply();
                      recreate();
                    })
                .show());
    action(
        c,
        "צפיפות רשת · " + prefs.getInt("columns", 3) + " עמודות",
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("צפיפות תמונות")
                .setItems(
                    new String[] {"2 · גדולות", "3 · מאוזן", "4 · קומפקטי", "5 · צפוף"},
                    (d, n) -> {
                      prefs.edit().putInt("columns", n + 2).apply();
                      render();
                    })
                .show());
    action(
        c,
        "צפיפות אלבומים · " + prefs.getInt("albumColumns", 3) + " עמודות",
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("תצוגת אלבומים")
                .setItems(
                    new String[] {"2 · שערים גדולים", "3 · קומפקטי, מותאם לגודל הטקסט"},
                    (d, n) -> {
                      prefs.edit().putInt("albumColumns", n == 0 ? 2 : 3).apply();
                      render();
                    })
                .show());
    preference(c, "רקע שחור במראה כהה", "oledBlack", true);
    preference(c, "אנימציות ומעברים", "motion", true);
    preference(c, "משוב רטט עדין בבחירה", "haptics", true);
    preference(c, "השאר מסך דולק בצפייה", "viewerAwake", true);
    action(
        c,
        "קצב מצגת · " + prefs.getInt("slideshowSeconds", 4) + " שניות",
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("זמן לכל תמונה")
                .setItems(
                    new String[] {"2 שניות", "4 שניות", "6 שניות", "10 שניות"},
                    (d, n) -> {
                      prefs.edit().putInt("slideshowSeconds", new int[] {2, 4, 6, 10}[n]).apply();
                      render();
                    })
                .show());
    section(c, "הספרייה שלך");
    action(
        c,
        "AI מקומי · מודלים, תגיות וכלי דיוקן",
        () -> {
          if (ai != null) ai.showManager();
        });
    action(c, "איתור קבצים כפולים זהים", this::findDuplicates);
    action(
        c,
        "סדר תמונות · " + (sortOld ? "ישנים תחילה" : "חדשים תחילה"),
        () -> {
          sortOld = !sortOld;
          prefs.edit().putBoolean("old", sortOld).apply();
          render();
        });
    action(c, "בחירת מדיה והרשאות", this::permission);
    action(c, "רענון הספרייה", this::load);
    section(c, "אחסון ושחזור");
    long used = 0;
    java.io.File[] files = Store.trash(this).listFiles();
    if (files != null) for (java.io.File f : files) used += f.length();
    action(
        c,
        "סל Lume · " + android.text.format.Formatter.formatFileSize(this, used),
        this::trashScreen);
    action(
        c,
        "מגבלת סל · " + prefs.getInt("trashLimit", 2048) + "MB",
        () ->
            new PrivacyDialogBuilder(this)
                .setTitle("הניקוי ייעצר כשהסל מלא")
                .setItems(
                    new String[] {"512MB", "2GB", "5GB", "10GB"},
                    (d, n) -> {
                      prefs
                          .edit()
                          .putInt("trashLimit", new int[] {512, 2048, 5120, 10240}[n])
                          .apply();
                      render();
                    })
                .show());
    action(
        c,
        "כללי ניקוי אוטומטי",
        () -> {
          tab = "rules";
          render();
        });
    if (transformer != null)
      action(
          c,
          "ביטול יצוא סרטון פעיל",
          () -> {
            transformer.cancel();
            transformer = null;
            if (exportFile != null) exportFile.delete();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            render();
          });
    section(c, "סידור אוטומטי");
    preference(c, "אנשים — סריקה אוטומטית במכשיר", "autoPeople", true);
    preference(c, "מקומות — קריאת GPS אוטומטית", "autoPlaces", true);
    preference(c, "עיבוד רק בזמן טעינה", "organizeCharging", true);
    action(
        c,
        "הרשאת מיקום בתמונות",
        () ->
            requestPermissions(
                new String[] {android.Manifest.permission.ACCESS_MEDIA_LOCATION}, 44));
    action(c, "מצב סידור אוטומטי", () -> message("סידור אוטומטי", AutoOrganizeJob.status(this)));
    section(c, "אבטחה ופרטיות");
    preference(c, "חסום צילום מסך ותצוגה במעבר אפליקציות", "protectScreen", true);
    action(
        c,
        prefs.getBoolean("appLock", false) ? "נעילת מכשיר פעילה · שינוי" : "הפעל נעילת אפליקציה",
        () -> privacy.toggleLock());
    action(
        c,
        "פרטי הגנות הפרטיות",
        () ->
            message(
                "הפרטיות שלך",
                "אין העלאת מדיה או עיבוד בענן. נתוני הפנים והאלבומים מוצפנים בנוסף; אינדקס החיפוש"
                    + " וסל השחזור נשמרים באחסון הפרטי של Android. נעילת האפליקציה מגינה על הממשק;"
                    + " התמונות המקוריות נשארות בגלריית המכשיר. שיתוף המקור עשוי לכלול מיקום. אפשר"
                    + " לאפס קבוצות ולכבות סידור אוטומטי."));
    action(
        c,
        "קוד פתוח ותרומה לפרויקט",
        () ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://github.com/acresking/Lume-gallery"))));
    section(c, "פרטיות ואודות");
    c.addView(
        text(
            "Lume Gallery 8.0.0 · המדיה נשארת במכשיר\n"
                + "חיבור לאינטרנט משמש להורדת מודלים בלבד. אין העלאת תמונות, חשבון או מודעות.\n"
                + "סל השחזור הפרטי יימחק בהסרת האפליקציה. יכולות AI הן הערכה; בדוק תוצאה לפני שמירת"
                + " עותק.",
            13));
  }

  void section(LinearLayout c, String name) {
    TextView heading = label(name, 17, ink, true);
    heading.setPadding(dp(24), dp(24), dp(24), dp(10));
    c.addView(heading);
  }

  void preference(LinearLayout c, String label, String key, boolean fallback) {
    Switch setting = new Switch(this);
    setting.setText(label);
    setting.setTextSize(15);
    setting.setTextColor(ink);
    setting.setPadding(dp(24), dp(13), dp(24), dp(13));
    setting.setChecked(prefs.getBoolean(key, fallback));
    setting.setOnCheckedChangeListener(
        (v, on) -> {
          prefs.edit().putBoolean(key, on).apply();
          if (key.equals("protectScreen")) AppPrivacy.apply(getWindow(), this);
          if (key.equals("oledBlack")) {
            applyColors();
            root.setBackgroundColor(bg);
            render();
          }
          if (key.equals("autoPeople")
              || key.equals("autoPlaces")
              || key.equals("organizeCharging")) AutoOrganizeJob.schedule(this);
        });
    c.addView(setting);
  }

  boolean motion() {
    return prefs.getBoolean("motion", true)
        && android.animation.ValueAnimator.areAnimatorsEnabled();
  }

  void pressAnimation(View view) {
    if (!motion()) return;
    android.animation.StateListAnimator states = new android.animation.StateListAnimator();
    android.animation.AnimatorSet down = new android.animation.AnimatorSet();
    down.playTogether(
        android.animation.ObjectAnimator.ofFloat(view, "scaleX", .975f),
        android.animation.ObjectAnimator.ofFloat(view, "scaleY", .975f));
    down.setDuration(100);
    android.animation.AnimatorSet up = new android.animation.AnimatorSet();
    up.playTogether(
        android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f),
        android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f));
    up.setDuration(160);
    up.setInterpolator(new android.view.animation.DecelerateInterpolator());
    states.addState(new int[] {android.R.attr.state_pressed}, down);
    states.addState(new int[] {}, up);
    view.setStateListAnimator(states);
  }

  void feedback(View view) {
    if (prefs.getBoolean("haptics", true))
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
  }

  void openNamedAlbum(String name) {
    filter = 0;
    if (ai != null && ai.manager != null && ai.manager.isShowing()) ai.manager.dismiss();
    customAlbum = name;
    collection = "";
    album = "";
    search = "";
    tab = "media";
    selected.clear();
    selecting = false;
    render();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(code, permissions, grants);
    AutoOrganizeJob.schedule(this);
    if (code == 11) maybeRequestMediaLocation();
    if (code == 44
        && grants.length > 0
        && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        && peoplePlaces != null) peoplePlaces.scanPlaces();
  }

  void maybeRequestMediaLocation() {
    if (prefs.getBoolean("autoPlaces", true)
        && MediaAccess.canRead(this)
        && !prefs.getBoolean("locationAsked", false)
        && !prefs.getBoolean("appLock", false)) {
      prefs.edit().putBoolean("locationAsked", true).apply();
      if (checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
          != android.content.pm.PackageManager.PERMISSION_GRANTED)
        requestPermissions(new String[] {android.Manifest.permission.ACCESS_MEDIA_LOCATION}, 44);
    }
  }

  void showSimilar(Map<String, Double> values) {
    similarScores.clear();
    similarScores.putAll(values);
    openCollection("similar");
  }

  void findDuplicates() {
    status.setText("סורק קבצים זהים…");
    ArrayList<Media> snapshot = new ArrayList<>(media);
    work(
        () -> {
          Map<Long, ArrayList<Media>> sizes = new HashMap<>();
          for (Media m : snapshot)
            if (m.size > 0) sizes.computeIfAbsent(m.size, k -> new ArrayList<>()).add(m);
          Map<String, ArrayList<Media>> hashes = new HashMap<>();
          int skipped = 0;
          for (ArrayList<Media> group : sizes.values())
            if (group.size() > 1)
              for (Media m : group) {
                try {
                  String hash =
                      android.util.Base64.encodeToString(
                          Store.digest(getContentResolver().openInputStream(m.uri)),
                          android.util.Base64.NO_WRAP);
                  hashes.computeIfAbsent(hash, k -> new ArrayList<>()).add(m);
                } catch (Exception e) {
                  skipped++;
                }
              }
          Set<String> found = new HashSet<>();
          for (ArrayList<Media> group : hashes.values())
            if (group.size() > 1) for (Media m : group) found.add(m.uri.toString());
          final int failed = skipped;
          runOnUiThread(
              () -> {
                duplicates.clear();
                duplicates.addAll(found);
                openCollection("duplicates");
                message(
                    "בדיקת כפילויות",
                    found.size()
                        + " קבצים עם תוכן זהה נמצאו. מוצגים גם המקור וגם העותקים. בחר ידנית מה"
                        + " לשמור ומה למחוק.\n"
                        + failed
                        + " קבצים לא נבדקו עקב שגיאת גישה.");
              });
        });
  }

  @Override
  void videoEditor(Media media) {
    new VideoStudio(this, media).show();
  }

  @Override
  void photoEditor(Media media) {
    new PhotoStudio(this, media).show();
  }

  @Override
  void viewer(Media m) {
    new Viewer(m).show();
  }

  final class Viewer extends Dialog {
    final ArrayList<Media> pages;
    int current;
    ViewPager2 pager;
    LinearLayout top, tools, film;
    HorizontalScrollView strip;
    VideoView activeVideo;
    boolean controls = true;
    final Handler slideshowHandler = new Handler(Looper.getMainLooper());
    boolean slideshow = false;
    final Runnable advance =
        new Runnable() {
          public void run() {
            if (!isShowing() || !slideshow) return;
            pager.setCurrentItem((current + 1) % pages.size(), motion());
            slideshowHandler.postDelayed(this, prefs.getInt("slideshowSeconds", 4) * 1000L);
          }
        };

    Viewer(Media first) {
      super(ModernActivity.this, android.R.style.Theme_Material_NoActionBar);
      pages = filtered();
      if (!pages.contains(first)) {
        pages.clear();
        pages.add(first);
      }
      current = pages.indexOf(first);
    }

    @Override
    protected void onCreate(Bundle b) {
      super.onCreate(b);
      if (prefs.getBoolean("viewerAwake", true))
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      LinearLayout content = column();
      content.setBackgroundColor(Color.BLACK);
      content.setOnApplyWindowInsetsListener(
          (v, i) -> {
            android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars());
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return i;
          });
      top = row();
      top.setPadding(dp(8), dp(4), dp(8), dp(4));
      content.addView(top);
      pager = new ViewPager2(ModernActivity.this);
      pager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      pager.setOffscreenPageLimit(1);
      pager.setAdapter(
          new RecyclerView.Adapter<TileHolder>() {
            public TileHolder onCreateViewHolder(android.view.ViewGroup p, int type) {
              FrameLayout frame = new FrameLayout(ModernActivity.this);
              frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
              return new TileHolder(frame);
            }

            public void onBindViewHolder(TileHolder h, int pos) {
              FrameLayout frame = (FrameLayout) h.itemView;
              frame.removeAllViews();
              Media m = pages.get(pos);
              if (!m.video()) {
                ZoomPhotoView image = new ZoomPhotoView(ModernActivity.this);
                image.setContentDescription(m.name + " · הקש להצגת פקדים או הסתרתם");
                image.setOnClickListener(v -> toggleControls());
                frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
                thumbs.execute(
                    () -> {
                      try {
                        Bitmap bitmap = decode(m.uri, 2200);
                        runOnUiThread(
                            () -> {
                              if (isShowing()) image.setBitmap(bitmap);
                            });
                      } catch (Exception ignored) {
                      }
                    });
              } else {
                ImageView cover = new ImageView(ModernActivity.this);
                cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                frame.addView(cover, new FrameLayout.LayoutParams(-1, -1));
                thumbnail(cover, m, 1200);
                ImageView play = new ImageView(ModernActivity.this);
                play.setImageDrawable(new GalleryIcons("play", Color.WHITE));
                play.setPadding(dp(18), dp(18), dp(18), dp(18));
                play.setBackground(rounded(0x99000000, 60));
                FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER);
                frame.addView(play, lp);
                play.setContentDescription("נגן סרטון");
                play.setOnClickListener(
                    v -> {
                      if (activeVideo != null) activeVideo.stopPlayback();
                      VideoView video = new VideoView(ModernActivity.this);
                      activeVideo = video;
                      frame.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                      MediaController controller = new MediaController(ModernActivity.this);
                      controller.setAnchorView(video);
                      video.setMediaController(controller);
                      video.setVideoURI(m.uri);
                      video.setOnPreparedListener(mp -> video.start());
                      video.setOnErrorListener(
                          (mp, w, e) -> {
                            message("ניגון", "לא ניתן לנגן את הסרטון בקידוד הזה");
                            return true;
                          });
                    });
              }
            }

            public int getItemCount() {
              return pages.size();
            }
          });
      content.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
      strip = new HorizontalScrollView(ModernActivity.this);
      strip.setHorizontalScrollBarEnabled(false);
      film = row();
      film.setGravity(Gravity.CENTER);
      strip.addView(film);
      content.addView(strip, new LinearLayout.LayoutParams(-1, dp(64)));
      tools = row();
      tools.setPadding(dp(8), dp(12), dp(8), dp(16));
      content.addView(tools);
      setContentView(content);
      pager.registerOnPageChangeCallback(
          new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int p) {
              if (current == p && top.getChildCount() > 0) return;
              if (activeVideo != null) {
                activeVideo.stopPlayback();
                activeVideo = null;
              }
              current = p;
              refresh();
            }
          });
      pager.setCurrentItem(current, false);
      refresh();
      getWindow().setStatusBarColor(Color.BLACK);
      getWindow().setNavigationBarColor(Color.BLACK);
      getWindow().getDecorView().setSystemUiVisibility(0);
    }

    @Override
    public void show() {
      AppPrivacy.apply(getWindow(), ModernActivity.this);
      getWindow().setWindowAnimations(motion() ? R.style.GalleryViewerMotion : 0);
      super.show();
      getWindow().setLayout(-1, -1);
    }

    void toggleControls() {
      controls = !controls;
      for (View view : new View[] {top, tools, strip}) {
        view.animate().cancel();
        if (controls) {
          view.setVisibility(View.VISIBLE);
          view.setAlpha(motion() ? 0 : 1);
          if (motion()) view.animate().alpha(1).setDuration(160).start();
        } else if (motion())
          view.animate()
              .alpha(0)
              .setDuration(140)
              .withEndAction(
                  () -> {
                    if (!controls) view.setVisibility(View.GONE);
                  })
              .start();
        else view.setVisibility(View.GONE);
      }
      WindowInsetsController insets = getWindow().getInsetsController();
      if (insets != null) {
        insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (controls) insets.show(WindowInsets.Type.systemBars());
        else insets.hide(WindowInsets.Type.systemBars());
      }
    }

    void whiteTool(LinearLayout row, String icon, String caption, Runnable click) {
      LinearLayout t = column();
      t.setGravity(Gravity.CENTER);
      t.setMinimumHeight(dp(56));
      t.setPadding(dp(3), dp(4), dp(3), dp(4));
      ImageView image = icon(icon, Color.WHITE, 23);
      t.addView(image);
      TextView name = label(caption, 11, Color.WHITE, false);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.topMargin = dp(8);
      t.addView(name, lp);
      t.setContentDescription(caption);
      t.setFocusable(true);
      t.setOnClickListener(v -> click.run());
      row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    }

    void refresh() {
      Media m = pages.get(current);
      top.removeAllViews();
      View close = iconButton("close", "סגירת תמונה", this::dismiss);
      ((ImageView) ((FrameLayout) close).getChildAt(0))
          .setImageDrawable(new GalleryIcons("close", Color.WHITE));
      top.addView(close);
      LinearLayout date = column();
      TextView day =
          label(
              new SimpleDateFormat("d בMMMM yyyy", new Locale("he")).format(new Date(m.date)),
              15,
              Color.WHITE,
              true);
      day.setGravity(Gravity.CENTER);
      date.addView(day);
      TextView count = label((current + 1) + " מתוך " + pages.size(), 11, 0xffa4a4aa, false);
      count.setGravity(Gravity.CENTER);
      date.addView(count);
      top.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
      View more = iconButton("more", "אפשרויות תמונה", () -> photoMenu(m));
      ((ImageView) ((FrameLayout) more).getChildAt(0))
          .setImageDrawable(new GalleryIcons("more", Color.WHITE));
      top.addView(more);
      tools.removeAllViews();
      whiteTool(
          tools,
          "share",
          "שיתוף",
          () -> share(new ArrayList<>(Collections.singletonList(m.uri.toString()))));
      whiteTool(
          tools,
          "heart",
          favorites.contains(m.uri.toString()) ? "במועדפים" : "מועדפים",
          () -> {
            if (!favorites.add(m.uri.toString())) favorites.remove(m.uri.toString());
            prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
            refresh();
          });
      whiteTool(
          tools,
          "edit",
          "עריכה",
          () -> {
            dismiss();
            if (m.video()) videoEditor(m);
            else photoEditor(m);
          });
      whiteTool(
          tools,
          "trash",
          "מחיקה",
          () -> {
            dismiss();
            trash(new ArrayList<>(Collections.singletonList(m.uri.toString())));
          });
      whiteTool(tools, "info", "פרטים", () -> details(m));
      film.removeAllViews();
      for (int i = Math.max(0, current - 4); i < Math.min(pages.size(), current + 5); i++) {
        final int position = i;
        FrameLayout frame = new FrameLayout(ModernActivity.this);
        frame.setPadding(dp(2), dp(2), dp(2), dp(2));
        frame.setBackground(rounded(i == current ? 0xffa9b9ff : Color.TRANSPARENT, 6));
        ImageView image = new ImageView(ModernActivity.this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        thumbnail(image, pages.get(i), 120);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(54));
        lp.setMargins(dp(2), dp(4), dp(2), dp(4));
        film.addView(frame, lp);
        frame.setOnClickListener(v -> pager.setCurrentItem(position, motion()));
      }
    }

    void photoMenu(Media m) {
      ArrayList<String> options =
          new ArrayList<>(
              Arrays.asList(
                  "הוסף לאלבום",
                  archived.contains(m.uri.toString()) ? "הוצא מארכיון" : "העבר לארכיון",
                  "הגדר כטפט",
                  "פתח באפליקציה אחרת"));
      options.add(slideshow ? "עצור מצגת" : "הפעל מצגת");
      options.add("AI מקומי · תגיות, דמיון ורקע");
      if (!customAlbum.isEmpty()) options.add("הגדר כשער האלבום");
      new PrivacyDialogBuilder(ModernActivity.this)
          .setTitle(m.name)
          .setItems(
              options.toArray(new String[0]),
              (d, n) -> {
                if (n == 0)
                  chooseAlbum(new ArrayList<>(Collections.singletonList(m.uri.toString())));
                else if (n == 1) {
                  if (!archived.add(m.uri.toString())) archived.remove(m.uri.toString());
                  prefs.edit().putStringSet("archive", new HashSet<>(archived)).apply();
                  dismiss();
                  render();
                } else if (n == 2) {
                  if (m.video()) {
                    message("טפט", "אפשר להגדיר תמונה כטפט");
                    return;
                  }
                  try {
                    Intent crop =
                        WallpaperManager.getInstance(ModernActivity.this)
                            .getCropAndSetWallpaperIntent(m.uri);
                    crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(crop);
                  } catch (Exception e) {
                    message("טפט", e.getMessage());
                  }
                } else if (n == 3)
                  startActivity(
                      Intent.createChooser(
                          new Intent(Intent.ACTION_VIEW)
                              .setDataAndType(m.uri, m.mime)
                              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                          "פתיחה באמצעות"));
                else if (n == 4) {
                  slideshow = !slideshow;
                  slideshowHandler.removeCallbacks(advance);
                  if (slideshow)
                    slideshowHandler.postDelayed(
                        advance, prefs.getInt("slideshowSeconds", 4) * 1000L);
                } else if (n == 5) {
                  dismiss();
                  if (ai != null) ai.tools(m);
                } else {
                  try {
                    JSONArray albums = personalAlbums();
                    for (int i = 0; i < albums.length(); i++)
                      if (albums.getJSONObject(i).optString("name").equals(customAlbum))
                        albums.getJSONObject(i).put("cover", m.uri.toString());
                    prefs
                        .edit()
                        .putString("personalAlbums", PrivateData.encrypt(albums.toString()))
                        .apply();
                    Toast.makeText(ModernActivity.this, "שער האלבום עודכן", Toast.LENGTH_SHORT)
                        .show();
                  } catch (Exception e) {
                    message("שער אלבום", e.getMessage());
                  }
                }
              })
          .show();
    }

    @Override
    public void dismiss() {
      slideshow = false;
      slideshowHandler.removeCallbacksAndMessages(null);
      if (activeVideo != null) activeVideo.stopPlayback();
      super.dismiss();
      if (!destroyed) render();
    }
  }

  void detailEntry(LinearLayout content, String key, String value) {
    TextView label = label(key, 12, muted, false);
    label.setPadding(0, dp(18), 0, dp(4));
    content.addView(label);
    TextView text = label(value == null ? "לא זמין" : value, 15, ink, false);
    text.setTextIsSelectable(true);
    content.addView(text);
  }

  void details(Media m) {
    Dialog sheet = new Dialog(this);
    AppPrivacy.apply(sheet.getWindow(), this);
    LinearLayout shell = column();
    shell.setPadding(dp(22), dp(14), dp(22), dp(16));
    shell.setBackground(rounded(bg, 28));
    LinearLayout heading = row();
    heading.addView(label("פרטי המדיה", 23, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(iconButton("close", "סגירת פרטי מדיה", sheet::dismiss));
    shell.addView(heading);
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = column();
    content.setPadding(dp(2), 0, dp(2), dp(16));
    scroll.addView(content);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    detailEntry(content, "שם", m.name);
    detailEntry(
        content,
        "תאריך בספרייה",
        new SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", new Locale("he"))
            .format(new Date(m.date)));
    detailEntry(content, "סוג", m.mime);
    detailEntry(content, "גודל", android.text.format.Formatter.formatFileSize(this, m.size));
    detailEntry(
        content, "מידות", m.width > 0 && m.height > 0 ? m.width + " × " + m.height : "לא זמינות");
    detailEntry(content, "תיקייה", m.path == null ? "לא ידועה" : m.path);
    LinearLayout extra = column();
    extra.addView(text("קורא נתוני צילום מהמכשיר…", 13));
    content.addView(extra);
    sheet.setContentView(shell);
    sheet.getWindow().setWindowAnimations(motion() ? R.style.GallerySheetMotion : 0);
    sheet.show();
    sheet.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    sheet.getWindow().setGravity(Gravity.BOTTOM);
    sheet.getWindow().setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .85f));
    io.execute(
        () -> {
          Map<String, String> data = MediaDetails.read(this, m);
          runOnUiThread(
              () -> {
                if (destroyed || !sheet.isShowing()) return;
                extra.removeAllViews();
                for (var entry : data.entrySet())
                  detailEntry(extra, entry.getKey(), entry.getValue());
                extra.addView(
                    text(
                        "הנתונים נקראים מקומית; קובץ המקור אינו משתנה. בשיתוף המקור עשויים להיכלל"
                            + " פרטי צילום ומיקום.",
                        12));
              });
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    if (request == 32 && result == RESULT_OK) {
      selected.clear();
      selecting = false;
    }
    super.onActivityResult(request, result, data);
  }

  @Override
  public void onBackPressed() {
    if (selecting) {
      selected.clear();
      selecting = false;
      render();
    } else if (!album.isEmpty() || !customAlbum.isEmpty() || !collection.isEmpty()) {
      album = "";
      collection = "";
      customAlbum = "";
      tab = "albums";
      render();
    } else if (!tab.equals("media")) {
      tab = "media";
      render();
    } else super.onBackPressed();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (privacy != null) privacy.resume();
    AutoOrganizeJob.schedule(this);
    maybeRequestMediaLocation();
  }

  @Override
  protected void onStop() {
    if (privacy != null) privacy.background();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (privacy != null) privacy.close();
    ui.removeCallbacksAndMessages(null);
    if (ai != null) ai.close();
    super.onDestroy();
  }
}
