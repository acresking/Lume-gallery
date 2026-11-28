package il.luma.gallery;
import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Read-only scoped gallery. Opening a photo preserves this location's swipe sequence. */
final class MapAlbumDialog extends Dialog {
  final ModernActivity host;
  final ArrayList<MainActivity.Media> items;
  final String title;
  MapAlbumDialog(ModernActivity host,String title,ArrayList<MainActivity.Media> items) {
    super(host,android.R.style.Theme_Material_NoActionBar);this.host=host;this.title=title;this.items=new ArrayList<>(items);
  }
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root=host.column();root.setBackgroundColor(host.bg);
    root.setOnApplyWindowInsetsListener((view,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
    LinearLayout top=host.row();top.setPadding(host.dp(12),host.dp(8),host.dp(12),host.dp(8));
    top.addView(host.iconButton("back","חזרה למפה",this::dismiss));
    TextView heading=host.label(title+" · "+items.size()+" תמונות",19,host.ink,true);
    heading.setMaxLines(2);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
    GridView grid=new GridView(host);grid.setNumColumns(3);grid.setHorizontalSpacing(host.dp(3));grid.setVerticalSpacing(host.dp(3));
    grid.setAdapter(new BaseAdapter(){
      public int getCount(){return items.size();}
      public Object getItem(int n){return items.get(n);}
      public long getItemId(int n){return n;}
      public View getView(int n,View recycled,android.view.ViewGroup parent){
        ImageView image=recycled instanceof ImageView?(ImageView)recycled:new ImageView(host);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int side=Math.max(host.dp(64),(parent.getWidth()>0?parent.getWidth():host.getResources().getDisplayMetrics().widthPixels)/3);
        image.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,side));image.setContentDescription(items.get(n).name);
        host.thumbnail(image,items.get(n),Math.min(320,side));return image;
      }
    });
    grid.setOnItemClickListener((parent,view,n,id)->host.new Viewer(items.get(n), items).show());
    root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  }
  @Override public void show(){AppPrivacy.apply(getWindow(),host);super.show();getWindow().setLayout(-1,-1);getWindow().setStatusBarColor(host.bg);getWindow().setNavigationBarColor(host.bg);}
}
