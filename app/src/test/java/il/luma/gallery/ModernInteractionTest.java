package il.luma.gallery;

import static org.junit.Assert.*;

import android.app.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ModernInteractionTest {
  MainActivity.Media photo(int id, int year, int month) {
    MainActivity.Media m = new MainActivity.Media();
    m.uri = Uri.parse("content://media/external/images/media/" + id);
    m.name = "sample" + id + ".jpg";
    m.mime = "image/jpeg";
    m.path = "DCIM/Camera/";
    Calendar c = Calendar.getInstance();
    c.clear();
    c.set(year, month, 15, 12, 0);
    m.date = c.getTimeInMillis();
    m.width = 100;
    m.height = 100;
    return m;
  }

  Bitmap people() {
    Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint();
    p.setColor(Color.CYAN);
    c.drawRect(10, 20, 30, 80, p);
    p.setColor(Color.MAGENTA);
    c.drawRect(70, 20, 90, 80, p);
    return b;
  }

  @Test
  public void favoriteButtonChangesSelectionAndPersistsBothDirections() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      var m = photo(1, 2024, 0);
      a.media.add(m);
      ModernActivity.Viewer v = a.new Viewer(m);
      v.show();
      assertFalse(v.tools.getChildAt(1).isSelected());
      v.tools.getChildAt(1).performClick();
      assertTrue(v.tools.getChildAt(1).isSelected());
      assertTrue(a.prefs.getStringSet("favorites", Set.of()).contains(m.uri.toString()));
      assertEquals("במועדפים", v.tools.getChildAt(1).getContentDescription());
      v.tools.getChildAt(1).performClick();
      assertFalse(v.tools.getChildAt(1).isSelected());
      assertFalse(a.prefs.getStringSet("favorites", Set.of()).contains(m.uri.toString()));
      v.dismiss();
    }
  }

  @Test
  public void filledFavoriteHasFilledPixelsInsteadOfOnlyAnOutline() {
    Bitmap outline = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888),
        filled = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
    GalleryIcons first = new GalleryIcons("heart", Color.WHITE),
        second = new GalleryIcons("heartFilled", Color.WHITE);
    first.setBounds(0, 0, 48, 48);
    second.setBounds(0, 0, 48, 48);
    first.draw(new Canvas(outline));
    second.draw(new Canvas(filled));
    assertEquals(0, Color.alpha(outline.getPixel(24, 24)));
    assertTrue(Color.alpha(filled.getPixel(24, 24)) > 200);
  }

  @Test
  public void pinchChangesRealPresentationAndCardsOpenCloserView() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      a.media.add(photo(1, 2024, 0));
      a.media.add(photo(2, 2024, 1));
      a.media.add(photo(3, 2023, 0));
      a.prefs.edit().putInt("columns", 5).apply();
      a.render();
      RecyclerView grid = findGrid(a.root);
      PinchGrid pinch = new PinchGrid(a, grid);
      pinch.change(1);
      assertEquals("months", a.viewMode);
      assertTrue(grid.getAdapter() instanceof CalendarOverview);
      assertEquals(3, grid.getAdapter().getItemCount());
      pinch.change(1);
      assertEquals("years", a.viewMode);
      assertEquals(2, grid.getAdapter().getItemCount());
      a.openOverviewDate(a.media.get(0).date);
      assertEquals("months", a.viewMode);
      a.openOverviewDate(a.media.get(0).date);
      assertEquals("photos", a.viewMode);
      assertFalse(findGrid(a.root).getAdapter() instanceof CalendarOverview);
    }
  }

  @Test
  public void calendarCountsUseRealDatesAndCurrentFilteredScope() {
    var first = photo(1, 2024, 0);
    var second = photo(2, 2024, 0);
    var third = photo(3, 2023, 1);
    var groups = CalendarOverview.groups(List.of(first, second, third), false);
    assertEquals(2, groups.size());
    assertEquals(2, groups.get(0).count);
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.media.clear();
      a.media.addAll(List.of(first, second, third));
      a.favorites.add(third.uri.toString());
      a.openCollection("favorites");
      assertEquals(1, CalendarOverview.groups(a.filtered(), true).size());
      assertEquals(1, CalendarOverview.groups(a.filtered(), true).get(0).count);
    }
  }

  @Test
  public void cutoutSelectsOnlyDisconnectedPersonAndPreservesSource() throws Exception {
    Bitmap source = people();
    Bitmap output = SubjectCutout.select(source, .2f, .5f);
    assertEquals(255, Color.alpha(output.getPixel(20, 50)));
    assertEquals(0, Color.alpha(output.getPixel(80, 50)));
    assertEquals(255, Color.alpha(source.getPixel(80, 50)));
    assertThrows(java.io.IOException.class, () -> SubjectCutout.select(source, .5f, .05f));
  }

  @Test
  public void touchingMasksAreKeptForExplicitManualCorrection() throws Exception {
    Bitmap source = people();
    Paint p = new Paint();
    p.setColor(Color.WHITE);
    new Canvas(source).drawRect(25, 45, 75, 55, p);
    Bitmap output = SubjectCutout.select(source, .2f, .5f);
    assertEquals(255, Color.alpha(output.getPixel(80, 50)));
  }

  @Test
  public void stickerIsTransparent512SquareAndTightCropExcludesOtherPerson() throws Exception {
    Bitmap selected = SubjectCutout.select(people(), .2f, .5f);
    Bitmap tight = SubjectCutout.tight(selected), sticker = SubjectCutout.sticker(selected);
    assertTrue(tight.getWidth() < 40);
    assertEquals(512, sticker.getWidth());
    assertEquals(512, sticker.getHeight());
    assertEquals(0, Color.alpha(sticker.getPixel(0, 0)));
    assertEquals(255, Color.alpha(sticker.getPixel(256, 256)));
  }

  @Test
  public void heldPointAccountsForLetterboxing() {
    ZoomPhotoView image = new ZoomPhotoView(RuntimeEnvironment.getApplication());
    image.setBitmap(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888));
    image.layout(0, 0, 400, 400);
    assertNull(image.imagePoint(200, 50));
    PointF center = image.imagePoint(200, 200);
    assertEquals(.5f, center.x, .001f);
    assertEquals(.5f, center.y, .001f);
    PointF corner = image.imagePoint(100, 150);
    assertEquals(.25f, corner.x, .001f);
    assertEquals(.25f, corner.y, .001f);
  }

  @Test
  public void eraseRestoreModifiesOnlyEditableMask() throws Exception {
    Bitmap original = people(), selected = SubjectCutout.select(original, .2f, .5f);
    SubjectBoard board = new SubjectBoard(RuntimeEnvironment.getApplication(), selected, original);
    board.layout(0, 0, 200, 200);
    stroke(board);
    assertEquals(0, Color.alpha(board.image.getPixel(21, 50)));
    board.erase = false;
    stroke(board);
    assertTrue(Color.alpha(board.image.getPixel(21, 50)) > 200);
    assertEquals(255, Color.alpha(original.getPixel(21, 50)));
  }

  void stroke(SubjectBoard b) {
    for (int i = 0; i < 3; i++) {
      MotionEvent e =
          MotionEvent.obtain(
              0,
              i * 20,
              i == 0
                  ? MotionEvent.ACTION_DOWN
                  : i == 1 ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_UP,
              40 + i * 2,
              100,
              0);
      b.onTouchEvent(e);
      e.recycle();
    }
  }

  @Test
  public void mapOpensInsideAppWithExactPointAndClosesResources() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.prefs.edit().putBoolean("mapNetwork", false).apply();
      a.openMap(12.345, 23.456);
      assertTrue(a.mapSheet.isShowing());
      ViewGroup mapContent =
          a.mapSheet.getWindow().getDecorView().findViewById(android.R.id.content);
      View mapRoot = mapContent.getChildAt(0);
      mapRoot.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(570, View.MeasureSpec.EXACTLY));
      mapRoot.layout(0, 0, 320, 570);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(12.345, a.mapSheet.map.getMapCenter().getLatitude(), .001);
      assertEquals(23.456, a.mapSheet.map.getMapCenter().getLongitude(), .001);
      assertNull(Shadows.shadowOf(a).getNextStartedActivity());
      a.mapSheet.dismiss();
      assertTrue(a.mapSheet.canceled.get());
    }
  }

  @Test
  public void styledDialogPreservesCallerShowAndChoiceCallbacks() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      int[] shows = {0}, chosen = {-1};
      AlertDialog d =
          new PrivacyDialogBuilder(a)
              .setTitle("אפשרויות")
              .setItems(new String[] {"ראשון", "שני"}, (v, n) -> chosen[0] = n)
              .setPositiveButton("סגירה", null)
              .create();
      d.setOnShowListener(v -> shows[0]++);
      d.show();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, shows[0]);
      assertEquals(Gravity.BOTTOM, d.getWindow().getAttributes().gravity);
      d.getListView().performItemClick(null, 1, 1);
      assertEquals(1, chosen[0]);
      d.dismiss();
    }
  }

  @Test
  public void peopleSetupExplainsOneTimeModelRequirementAndAutomaticSettings() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      assertTrue(a.prefs.getBoolean("autoPeople", true));
      assertTrue(a.prefs.getBoolean("autoOrganizeForeground", true));
      a.peoplePlaces.open("person");
      var d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(
          findText(d.getWindow().getDecorView(), "להפעלת זיהוי אנשים אוטומטי · התקן Lume AI"));
      d.dismiss();
      a.automaticPeople.pause();
      a.automaticPeople.resume();
      assertFalse(OrganizeEngine.RUNNING.get());
    }
  }

  @Test
  public void longPopupActionsStackInsteadOfOverlapping() {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      AlertDialog d =
          new PrivacyDialogBuilder(a)
              .setTitle("בחירה")
              .setMessage("בדיקת שורות ארוכות")
              .setPositiveButton("שמור את כל האפשרויות שבחרתי", null)
              .setNegativeButton("בטל את השינויים וחזור", null)
              .setNeutralButton("הצג מידע נוסף על הפעולה", null)
              .create();
      d.show();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertTrue(d.getButton(-1).getParent() instanceof LinearLayout);
      assertEquals(
          LinearLayout.VERTICAL, ((LinearLayout) d.getButton(-1).getParent()).getOrientation());
      d.dismiss();
    }
  }

  @Test
  public void subjectBrushButtonsAndCompactLayoutReflectSelection() throws Exception {
    try (var c = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = c.get();
      a.getResources().getDisplayMetrics().scaledDensity = 1.5f;
      Bitmap foreground = people();
      SubjectStudio d =
          new SubjectStudio(a, SubjectCutout.select(foreground, .2f, .5f), foreground);
      d.show();
      ViewGroup frame = d.getWindow().getDecorView().findViewById(android.R.id.content);
      View root = frame.getChildAt(0);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 320, 560);
      assertTrue(d.board.getHeight() > 60);
      View restore = findText(root, "שחזר"), erase = findText(root, "מחק");
      restore.performClick();
      assertFalse(d.board.erase);
      assertTrue(restore.isSelected());
      assertFalse(erase.isSelected());
      erase.performClick();
      assertTrue(d.board.erase);
      assertTrue(erase.isSelected());
      d.dismiss();
    }
  }

  View findText(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View r = findText(g.getChildAt(i), text);
        if (r != null) return r;
      }
    }
    return null;
  }

  RecyclerView findGrid(View v) {
    if (v instanceof RecyclerView) return (RecyclerView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        RecyclerView r = findGrid(g.getChildAt(i));
        if (r != null) return r;
      }
    }
    return null;
  }
}
