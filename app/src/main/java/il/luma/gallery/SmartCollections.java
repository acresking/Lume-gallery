package il.luma.gallery;

import java.util.*;
import java.util.regex.Pattern;

/** Collections inferred from the existing local content index, never from identity or GPS. */
final class SmartCollections {
  static final class Category {
    final String id, title;
    final Pattern words;

    Category(String id, String title, String keywords) {
      this.id = id;
      this.title = title;
      words =
          Pattern.compile(
              "(?iu)(?<![\\p{L}\\p{N}_])(?:"
                  + String.join(
                      "|",
                      Arrays.stream(keywords.split(" ")).map(Pattern::quote).toArray(String[]::new))
                  + ")(?![\\p{L}\\p{N}_])");
    }

    boolean matches(MainActivity.Media media, AiIndex.Entry entry) {
      return !media.video()
          && entry != null
          && AiIndex.fingerprint(media).equals(entry.fingerprint)
          && entry.tags != null
          && words.matcher(entry.tags).find();
    }
  }

  static final Category[] CATEGORIES = {
    new Category(
        "nature",
        "טבע ונוף",
        "mountain valley beach coast lakeside forest tree flower snow טבע נוף"),
    new Category(
        "animals",
        "בעלי חיים",
        "dog cat bird horse zebra elephant lion tiger bear animal חיות כלב חתול"),
    new Category(
        "food",
        "אוכל",
        "pizza sandwich cake food plate banana apple orange broccoli אוכל ארוחה מזון"),
    new Category(
        "travel",
        "כלי תחבורה",
        "car bus truck taxi jeep train boat ship aircraft bicycle רכב מכונית תחבורה"),
    new Category("documents", "מסמכים", "book paper notebook envelope menu מסמך נייר ספר"),
    new Category(
        "buildings", "מבנים ועיר", "castle palace church monastery building tower בניין מבנה עיר"),
    new Category(
        "technology", "טכנולוגיה", "computer laptop monitor keyboard screen טכנולוגיה מחשב"),
    new Category("portraits", "דיוקנאות", "person people portrait face דיוקן פנים אנשים")
  };

  static Category find(String id) {
    for (Category c : CATEGORIES) if (c.id.equals(id)) return c;
    return null;
  }

  static Map<Category, ArrayList<MainActivity.Media>> groups(
      java.util.List<MainActivity.Media> media,
      Map<String, AiIndex.Entry> index,
      Set<String> archived) {
    Map<Category, ArrayList<MainActivity.Media>> out = new LinkedHashMap<>();
    for (Category c : CATEGORIES) out.put(c, new ArrayList<>());
    for (var m : media) {
      if (archived.contains(m.uri.toString())) continue;
      AiIndex.Entry entry = index.get(m.uri.toString());
      if (entry == null) continue;
      for (Category c : CATEGORIES) if (c.matches(m, entry)) out.get(c).add(m);
    }
    for (var photos : out.values()) photos.sort((a, b) -> Long.compare(b.date, a.date));
    return out;
  }
}
