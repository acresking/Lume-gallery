package il.luma.gallery;
import java.util.*;
import org.json.*;
/** Explicit corrections preserve names and combine bounded encrypted face exemplars. */
final class PeopleMerge {
  static void choose(ModernActivity a,String source,Runnable refreshed) {
    ArrayList<String> ids=new ArrayList<>(), labels=new ArrayList<>();
    JSONArray all=a.personalAlbums();
    for(int i=0;i<all.length();i++){JSONObject g=all.optJSONObject(i);if(g!=null && "person".equals(g.optString("kind")) && !source.equals(g.optString("id"))){ids.add(g.optString("id"));labels.add(g.optString("name")+" · "+(g.optJSONArray("items")==null?0:g.optJSONArray("items").length())+" תמונות");}}
    if(ids.isEmpty()){a.message("איחוד אנשים","אין קבוצה נוספת לאיחוד");return;}
    new PrivacyDialogBuilder(a).setTitle("זה אותו אדם? בחר קבוצה לאיחוד")
      .setItems(labels.toArray(new String[0]),(d,n)->new PrivacyDialogBuilder(a)
        .setTitle("איחוד קבוצות של אותו אדם")
        .setMessage("התמונות והדוגמאות יאוחדו לקבוצה שבחרת. התמונות המקוריות לא ישתנו. אין ביטול אוטומטי לאיחוד.")
        .setPositiveButton("אחד קבוצות",(dialog,which)->merge(a,source,ids.get(n),refreshed))
        .setNegativeButton("ביטול",null).show()).setNegativeButton("ביטול",null).show();
  }
  static void merge(ModernActivity a,String source,String destination,Runnable refreshed) {
    if(a.ai.busy || !OrganizeEngine.RUNNING.compareAndSet(false,true)){a.message("סריקה פעילה","המתן לסיום הסריקה או עצור אותה לפני איחוד");return;}
    try {
      JSONArray all=a.personalAlbums();JSONObject from=OrganizeEngine.find(all,source),to=OrganizeEngine.find(all,destination);
      if(from==null || to==null || !"person".equals(from.optString("kind")) || !"person".equals(to.optString("kind")))throw new java.io.IOException("הקבוצות השתנו. פתח שוב את מסך האנשים");
      Set<String> members=new LinkedHashSet<>();JSONArray items=from.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++)members.add(items.optString(i));OrganizeEngine.merge(to,members);
      JSONObject profiles=new JSONObject(PrivateData.read(a,"faceProfiles","{}"));Map<String,float[]> legacy=new LinkedHashMap<>();
      for(var it=profiles.keys();it.hasNext();){String id=it.next();JSONArray row=profiles.optJSONArray(id);if(row==null)continue;float[] v=new float[row.length()];for(int j=0;j<v.length;j++)v[j]=(float)row.optDouble(j);legacy.put(id,v);}
      FaceSamples bank=new FaceSamples(new JSONObject(PrivateData.read(a,"faceSamples","{}")),legacy);
      ArrayList<float[]> samples=bank.samples.get(source);
      if(samples!=null)for(float[] sample:new ArrayList<>(samples))bank.add(destination,sample);
      if(!profiles.has(destination) && profiles.has(source))profiles.put(destination,profiles.get(source));
      profiles.remove(source);
      for(int i=all.length()-1;i>=0;i--)if(source.equals(all.optJSONObject(i).optString("id")))all.remove(i);
      Set<String> keep=new HashSet<>();for(var it=profiles.keys();it.hasNext();)keep.add(it.next());
      JSONObject quality=new JSONObject(PrivateData.read(a,"faceCoverQuality","{}"));quality.remove(source);
      if(!a.prefs.edit().putString("personalAlbums",PrivateData.encrypt(all.toString()))
        .putString("faceProfiles",PrivateData.encrypt(profiles.toString()))
        .putString("faceSamples",PrivateData.encrypt(bank.json(keep).toString()))
        .putString("faceCoverQuality",PrivateData.encrypt(quality.toString())).commit())throw new java.io.IOException("האיחוד לא נשמר");
      if(source.matches("[a-zA-Z0-9-]{1,80}"))new java.io.File(new java.io.File(a.getFilesDir(),"people-covers"),source+".enc").delete();
      refreshed.run();
    }catch(Exception e){a.message("איחוד אנשים",e.getMessage());}finally{OrganizeEngine.RUNNING.set(false);}
  }
}
