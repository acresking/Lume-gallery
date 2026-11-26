package il.luma.gallery;

import android.app.*;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import org.json.*;

public class CleanupJob extends JobService {
  private Future<?> task;
  private final ExecutorService pool = Executors.newSingleThreadExecutor();

  static void schedule(Context c) {
    JobScheduler scheduler = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
    if (scheduler.getPendingJob(41) != null) return;
    scheduler.schedule(
        new JobInfo.Builder(41, new ComponentName(c, CleanupJob.class))
            .setPeriodic(6 * 60 * 60 * 1000L)
            .setRequiresBatteryNotLow(true)
            .build());
  }

  @Override
  public boolean onStartJob(JobParameters p) {
    task =
        pool.submit(
            () -> {
              int n = 0;
              String error = "";
              try {
                JSONArray a = Store.rules(this);
                for (int i = 0; i < a.length(); i++) {
                  JSONObject r = a.getJSONObject(i);
                  if (r.optBoolean("enabled")) n += Store.clean(this, r);
                }
              } catch (Exception e) {
                error = e.getMessage();
              }
              getSharedPreferences("luma", 0)
                  .edit()
                  .putString(
                      "lastRun", new java.util.Date() + " · " + n + " קבצים הועברו לסל " + error)
                  .apply();
              jobFinished(p, false);
            });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters p) {
    if (task != null) task.cancel(true);
    return true;
  }

  @Override
  public void onDestroy() {
    pool.shutdownNow();
    super.onDestroy();
  }
}
