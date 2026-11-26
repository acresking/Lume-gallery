package il.luma.gallery;

public final class RulePolicyTest {
  static int count;

  static void expect(boolean value) {
    count++;
    if (!value) throw new AssertionError("Case " + count);
  }

  public static void main(String[] args) {
    long now = 1_800_000_000_000L, day = 86_400_000L;
    expect(RulePolicy.matches("image/jpeg", now - 31 * day, now, 30, "all"));
    expect(!RulePolicy.matches("image/jpeg", now - 30 * day, now, 30, "all"));
    expect(!RulePolicy.matches("image/jpeg", 0, now, 30, "all"));
    expect(!RulePolicy.matches("video/mp4", now + day, now, 30, "all"));
    expect(!RulePolicy.matches("text/plain", now - 40 * day, now, 30, "all"));
    expect(!RulePolicy.matches(null, now - 40 * day, now, 30, "all"));
    expect(!RulePolicy.matches("image/png", now - 40 * day, now, 30, "video"));
    expect(RulePolicy.matches("video/mp4", now - 40 * day, now, 30, "video"));
    expect(!RulePolicy.matches("image/jpeg", now - day, now, 0, "all"));
    expect(!RulePolicy.matches("image/jpeg", now - 40 * day, now, 30, "unknown"));
    expect(!RulePolicy.matches("image/jpeg", now - 40 * day, now, Integer.MAX_VALUE, "all"));
    expect(RulePolicy.matches("image/webp", now - day - 1, now, 1, "image"));
    System.out.println(count + " deletion policy checks passed");
  }
}
