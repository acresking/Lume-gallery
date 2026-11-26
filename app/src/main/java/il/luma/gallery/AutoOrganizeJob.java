package il.luma.gallery;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local batches scheduled by Android. No network or live location. */
public final class AutoOrganizeJob extends JobService {
  static final int PERIODIC = 52, CHANGES = 53, FOLLOW_UP = 54;
  final ExecutorService pool = Executors.newSingleThreadExecutor();
  final ConcurrentMap<Integer, AtomicBoolean> tokens = new ConcurrentHashMap<>();

  static boolean enabled(Context c) {
    var p = c.getSharedPreferences("luma", 0);
    return p.getBoolean("autoPeople", true) || p.getBoolean("autoPlaces", true);
  }

  static JobInfo.Builder base(Context c, int id) {
    return new JobInfo.Builder(id, new ComponentName(c, AutoOrganizeJob.class))
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .setRequiresCharging(
            c.getSharedPreferences("luma", 0).getBoolean("organizeCharging", true));
  }

  static JobInfo.Builder content(Context c) {
    return base(c, CHANGES)
        .addTriggerContentUri(
            new JobInfo.TriggerContentUri(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
        .setTriggerContentUpdateDelay(60_000)
        .setTriggerContentMaxDelay(5 * 60_000);
  }

  static void schedule(Context c) {
    JobScheduler s = c.getSystemService(JobScheduler.class);
    if (!enabled(c)) {
      for (int id : new int[] {52, 53, 54}) s.cancel(id);
      return;
    }
    boolean charging = c.getSharedPreferences("luma", 0).getBoolean("organizeCharging", true);
    for (int id : new int[] {PERIODIC, CHANGES, FOLLOW_UP}) {
      JobInfo old = s.getPendingJob(id);
      if (old != null && old.isRequireCharging() == charging) continue;
      if (id == PERIODIC)
        s.schedule(base(c, id).setPeriodic(6 * 60 * 60_000L).setPersisted(true).build());
      else if (id == CHANGES) s.schedule(content(c).build());
      else s.schedule(base(c, id).setMinimumLatency(15_000).build());
    }
  }

  static String status(Context c) {
    var p = c.getSharedPreferences("luma", 0);
    return "אנשים: "
        + (p.getBoolean("autoPeople", true) ? "אוטומטי" : "כבוי")
        + "\nמקומות: "
        + (p.getBoolean("autoPlaces", true) ? "אוטומטי" : "כבוי")
        + "\n"
        + (p.getBoolean("organizeCharging", true) ? "ברקע בזמן טעינה" : "ברקע כשהסוללה אינה נמוכה")
        + "\n"
        + "נדרשות הרשאות תמונות; לאנשים גם חבילת AI, ולמקומות הרשאת מיקום במדיה. Android קובע את"
        + " מועד הריצה.\n"
        + p.getString("organizeStatus", "ממתין לסריקה ראשונה");
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    AtomicBoolean cancel = new AtomicBoolean();
    tokens.put(params.getJobId(), cancel);
    pool.execute(
        () -> {
          boolean again = false;
          try {
            if (cancel.get() || !enabled(this)) return;
            var p = getSharedPreferences("luma", 0);
            var r =
                OrganizeEngine.run(
                    this,
                    cancel,
                    40,
                    p.getBoolean("autoPeople", true),
                    p.getBoolean("autoPlaces", true));
            again = r.pending;
            p.edit()
                .putString(
                    "organizeStatus",
                    java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())
                        + " · "
                        + r.message)
                .apply();
          } catch (Exception error) {
            getSharedPreferences("luma", 0)
                .edit()
                .putString("organizeStatus", "הסריקה נעצרה; בדוק הרשאות ואחסון")
                .apply();
          } finally {
            final boolean pending = again;
            new Handler(getMainLooper())
                .post(
                    () -> {
                      if (tokens.remove(params.getJobId(), cancel) && !cancel.get()) {
                        jobFinished(params, false);
                        JobScheduler s = getSystemService(JobScheduler.class);
                        if (enabled(this)) {
                          if (params.getJobId() == CHANGES) s.schedule(content(this).build());
                          if (pending)
                            s.schedule(base(this, FOLLOW_UP).setMinimumLatency(60_000).build());
                        }
                      }
                    });
          }
        });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    AtomicBoolean cancel = tokens.remove(params.getJobId());
    if (cancel != null) cancel.set(true);
    return enabled(this);
  }

  @Override
  public void onDestroy() {
    for (var cancel : tokens.values()) cancel.set(true);
    tokens.clear();
    pool.shutdown();
    super.onDestroy();
  }
}
