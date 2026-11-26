package il.luma.gallery;

import android.app.*;
import android.content.Context;

final class PrivacyDialogBuilder extends AlertDialog.Builder {
  private final Context context;

  PrivacyDialogBuilder(Context c) {
    super(c);
    context = c;
  }

  @Override
  public AlertDialog create() {
    AlertDialog dialog = super.create();
    AppPrivacy.apply(dialog.getWindow(), context);
    return dialog;
  }
}
