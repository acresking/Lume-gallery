package il.luma.gallery;

import java.util.*;

/** Date-only, local memories. No inferred people, events or fabricated story titles. */
final class MemoryCollections {
  static final class Memory {
    final String id, title;
    final ArrayList<MainActivity.Media> items = new ArrayList<>();

    Memory(String id, String title) {
      this.id = id;
      this.title = title;
    }
  }

  static String title(String id) {
    return id.equals("week")
        ? "השבוע האחרון"
        : id.matches("year:[0-9]{4}") ? "ביום הזה · " + id.substring(5) : "זיכרונות";
  }

  static long[] range(String id, long now) {
    Calendar today = Calendar.getInstance();
    today.setTimeInMillis(now);
    Calendar start = (Calendar) today.clone();
    start.set(Calendar.HOUR_OF_DAY, 0);
    start.set(Calendar.MINUTE, 0);
    start.set(Calendar.SECOND, 0);
    start.set(Calendar.MILLISECOND, 0);
    if (id.equals("week")) {
      start.add(Calendar.DAY_OF_YEAR, -6);
      return new long[] {start.getTimeInMillis(), now + 1};
    }
    if (!id.matches("year:[0-9]{4}")) return new long[] {1, 0};
    int year = Integer.parseInt(id.substring(5));
    if (year >= today.get(Calendar.YEAR) || year < 1900) return new long[] {1, 0};
    start.set(Calendar.YEAR, year);
    if (start.get(Calendar.MONTH) != today.get(Calendar.MONTH)
        || start.get(Calendar.DAY_OF_MONTH) != today.get(Calendar.DAY_OF_MONTH))
      return new long[] {1, 0};
    Calendar end = (Calendar) start.clone();
    end.add(Calendar.DAY_OF_YEAR, 1);
    return new long[] {start.getTimeInMillis(), end.getTimeInMillis()};
  }

  static boolean matches(MainActivity.Media media, long[] range) {
    return media.date > 0 && media.date >= range[0] && media.date < range[1];
  }

  static boolean matches(String id, MainActivity.Media media, long now) {
    return matches(media, range(id, now));
  }

  static ArrayList<Memory> build(
      java.util.List<MainActivity.Media> photos, Set<String> archived, long now) {
    Memory recent = new Memory("week", title("week"));
    TreeMap<Integer, Memory> years = new TreeMap<>(Collections.reverseOrder());
    Calendar today = Calendar.getInstance(), then = Calendar.getInstance();
    today.setTimeInMillis(now);
    long weekStart = range("week", now)[0];
    for (var media : photos) {
      if (archived.contains(media.uri.toString()) || media.date <= 0 || media.date > now) continue;
      if (media.date >= weekStart) recent.items.add(media);
      then.setTimeInMillis(media.date);
      int year = then.get(Calendar.YEAR);
      if (year < today.get(Calendar.YEAR)
          && then.get(Calendar.MONTH) == today.get(Calendar.MONTH)
          && then.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH))
        years
            .computeIfAbsent(year, y -> new Memory("year:" + y, title("year:" + y)))
            .items
            .add(media);
    }
    ArrayList<Memory> result = new ArrayList<>();
    if (!recent.items.isEmpty()) result.add(recent);
    for (Memory m : years.values()) {
      if (result.size() >= 4) break;
      result.add(m);
    }
    for (Memory m : result) m.items.sort((a, b) -> Long.compare(b.date, a.date));
    return result;
  }
}
