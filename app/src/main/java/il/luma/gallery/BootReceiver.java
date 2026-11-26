package il.luma.gallery;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      CleanupJob.schedule(context);
      AutoOrganizeJob.schedule(context);
    }
  }
}
