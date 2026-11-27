package il.luma.gallery;

import static org.junit.Assert.*;

import android.app.job.*;
import android.content.*;
import android.view.WindowManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class PrivacyAutomationTest {
  @Test
  public void encryptionRejectsTamperingAndUsesUniqueNonces() throws Exception {
    KeyGenerator g = KeyGenerator.getInstance("AES");
    g.init(256);
    SecretKey key = g.generateKey();
    byte[] plain = "נתוני פנים ומיקום".getBytes(StandardCharsets.UTF_8),
        a = PrivateData.seal(plain, key),
        b = PrivateData.seal(plain, key);
    assertFalse(Arrays.equals(a, b));
    assertArrayEquals(plain, PrivateData.open(a, key));
    assertArrayEquals(new byte[0], PrivateData.open(PrivateData.seal(new byte[0], key), key));
    a[a.length - 1] ^= 1;
    assertThrows(AEADBadTagException.class, () -> PrivateData.open(a, key));
    assertThrows(AEADBadTagException.class, () -> PrivateData.open(b, g.generateKey()));
    assertThrows(java.io.IOException.class, () -> PrivateData.open(new byte[8], key));
  }

  @Test
  public void localChargingJobsRespectAllToggles() {
    Context c = RuntimeEnvironment.getApplication();
    var p = c.getSharedPreferences("luma", 0);
    p.edit().clear().commit();
    var s = c.getSystemService(JobScheduler.class);
    AutoOrganizeJob.schedule(c);
    var j = s.getPendingJob(52);
    assertTrue(j.isRequireCharging());
    assertTrue(j.isRequireBatteryNotLow());
    assertTrue(j.isRequireStorageNotLow());
    assertTrue(j.isPersisted());
    assertEquals(JobInfo.NETWORK_TYPE_NONE, j.getNetworkType());
    assertNotNull(s.getPendingJob(53).getTriggerContentUris());
    p.edit().putBoolean("organizeCharging", false).commit();
    AutoOrganizeJob.schedule(c);
    for (int id : new int[] {52, 53, 54}) assertFalse(s.getPendingJob(id).isRequireCharging());
    p.edit().putBoolean("autoPeople", false).putBoolean("autoPlaces", false).commit();
    AutoOrganizeJob.schedule(c);
    for (int id : new int[] {52, 53, 54}) assertNull(s.getPendingJob(id));
  }

  @Test
  public void mainAndDialogWindowsAllowScreenshotsEvenWithLegacyFlags() {
    try (var controller = Robolectric.buildActivity(ModernActivity.class).setup()) {
      var a = controller.get();
      int flag = WindowManager.LayoutParams.FLAG_SECURE;
      assertEquals(0, a.getWindow().getAttributes().flags & flag);
      var d = new PrivacyDialogBuilder(a).setMessage("private").create();
      assertEquals(0, d.getWindow().getAttributes().flags & flag);
      a.prefs.edit().putBoolean("protectScreen", false).commit();
      AppPrivacy.apply(a.getWindow(), a);
      assertEquals(0, a.getWindow().getAttributes().flags & flag);
      a.prefs.edit().putBoolean("appLock", true).commit();
      AppPrivacy.apply(a.getWindow(), a);
      assertEquals(0, a.getWindow().getAttributes().flags & flag);
    }
  }

  @Test
  public void missingPermissionDoesNotLoadAi() throws Exception {
    var r =
        OrganizeEngine.run(
            RuntimeEnvironment.getApplication(),
            new java.util.concurrent.atomic.AtomicBoolean(),
            40,
            true,
            true);
    assertEquals(0, r.processed);
    assertFalse(OrganizeEngine.RUNNING.get());
    assertTrue(r.message.contains("הרשאת"));
  }

  @Test
  public void rescannedImagesReplaceMembershipWithoutRemovingManualEntries() throws Exception {
    var g =
        new org.json.JSONObject().put("items", new org.json.JSONArray().put("old").put("manual"));
    OrganizeEngine.removeMembers(g, Set.of("old"));
    OrganizeEngine.merge(g, Set.of("new"));
    assertEquals(2, g.getJSONArray("items").length());
    assertEquals("manual", g.getJSONArray("items").getString(0));
    assertEquals("new", g.getJSONArray("items").getString(1));
  }
}
