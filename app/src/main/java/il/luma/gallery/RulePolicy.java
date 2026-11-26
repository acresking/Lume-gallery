package il.luma.gallery;

/** Pure, fail-closed policy shared by preview and background deletion. */
final class RulePolicy {
  static boolean matches(String mime, long modified, long now, int days, String type) {
    if (days < 1 || days > 36500 || modified <= 0 || modified >= now || mime == null) return false;
    if (!mime.startsWith("image/") && !mime.startsWith("video/")) return false;
    if (!type.equals("all") && !type.equals("image") && !type.equals("video")) return false;
    return now - modified > days * 86_400_000L
        && (type.equals("all") || mime.startsWith(type + "/"));
  }
}
