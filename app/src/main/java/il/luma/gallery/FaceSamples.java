package il.luma.gallery;
import java.util.*;
import org.json.*;
/** Bounded, encrypted-by-caller exemplars; thresholds are not relaxed to force merges. */
final class FaceSamples {
  static final int LIMIT = 8;
  final LinkedHashMap<String,ArrayList<float[]>> samples = new LinkedHashMap<>();
  FaceSamples(JSONObject saved, Map<String,float[]> legacy) {
    for (var entry : legacy.entrySet()) {
      ArrayList<float[]> bank = new ArrayList<>();
      JSONArray list = saved.optJSONArray(entry.getKey());
      if (list != null) for(int i=0;i<Math.min(LIMIT,list.length());i++) {
        JSONArray row=list.optJSONArray(i);
        if(row==null || row.length()!=entry.getValue().length) continue;
        float[] v=new float[row.length()];
        for(int j=0;j<v.length;j++) v[j]=(float)row.optDouble(j);
        float[] normalized=normalize(v); if(normalized!=null) bank.add(normalized);
      }
      if(bank.isEmpty()) { float[] v=normalize(entry.getValue()); if(v!=null) bank.add(v); }
      samples.put(entry.getKey(),bank);
    }
  }
  static float[] normalize(float[] input) {
    double sum=0;for(float x:input){if(!Float.isFinite(x))return null;sum+=x*x;}
    if(sum<1e-12)return null;
    float[] out=input.clone();float n=(float)Math.sqrt(sum);
    for(int i=0;i<out.length;i++)out[i]/=n;return out;
  }
  double score(String id,float[] vector) {
    double best=-1;for(float[] sample:samples.getOrDefault(id,new ArrayList<>()))
      if(sample.length==vector.length) best=Math.max(best,AiIndex.cosine(vector,sample));
    return best;
  }
  String match(float[] vector,double threshold,Set<String> excluded) {
    String id=null;double best=threshold,runner=-1;
    for(String key:samples.keySet()) {
      if(excluded.contains(key))continue;double score=score(key,vector);
      if(score>best){runner=best;best=score;id=key;}else runner=Math.max(runner,score);
    }
    // Ambiguous identities stay separate for explicit correction.
    return id!=null && (runner<=threshold || best-runner>=.035) ? id : null;
  }
  void add(String id,float[] vector) {
    float[] v=normalize(vector);if(v==null)return;
    ArrayList<float[]> bank=samples.computeIfAbsent(id,k->new ArrayList<>());
    for(float[] previous:bank)if(previous.length!=v.length)return;
    if(bank.isEmpty()){bank.add(v);return;}
    double best=-1;for(float[] previous:bank)best=Math.max(best,AiIndex.cosine(v,previous));
    if(best>.98)return;
    // Keep the original anchor and replace only the most redundant non-anchor sample.
    if(bank.size()<LIMIT){bank.add(v);return;}
    int redundant=1;double similarity=-2;
    for(int i=1;i<bank.size();i++){double nearest=-1;for(int j=0;j<bank.size();j++)if(i!=j)nearest=Math.max(nearest,AiIndex.cosine(bank.get(i),bank.get(j)));if(nearest>similarity){similarity=nearest;redundant=i;}}
    if(best<similarity)bank.set(redundant,v);
  }
  JSONObject json(Set<String> retained) throws JSONException {
    JSONObject out=new JSONObject();
    for(var entry:samples.entrySet())if(retained.contains(entry.getKey())){
      JSONArray bank=new JSONArray();for(float[] v:entry.getValue()){JSONArray row=new JSONArray();for(float x:v)row.put(x);bank.put(row);}out.put(entry.getKey(),bank);
    }return out;
  }
}
