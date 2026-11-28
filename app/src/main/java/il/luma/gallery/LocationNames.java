package il.luma.gallery;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import java.util.*;
import java.util.concurrent.*;

/** Best-effort Android geocoder. The platform provider may use a network service. */
final class LocationNames {
  private static final Map<String, String> cache = new ConcurrentHashMap<>();
  private static final ThreadPoolExecutor worker =
      new ThreadPoolExecutor(
          2,
          2,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(64),
          r -> {
            Thread t = new Thread(r, "Lume-place-name");
            t.setDaemon(true);
            return t;
          });

  static String format(Address address) {
    LinkedHashSet<String> parts = new LinkedHashSet<>();
    String city = address.getLocality();
    if (city == null || city.isBlank()) city = address.getSubAdminArea();
    if (city == null || city.isBlank()) city = address.getAdminArea();
    if (city != null && !city.isBlank()) parts.add(city);
    if (address.getCountryName() != null && !address.getCountryName().isBlank())
      parts.add(address.getCountryName());
    return String.join(", ", parts);
  }

  static String resolve(Context context, double lat, double lon) {
    if (!context.getSharedPreferences("luma", 0).getBoolean("placeNames", true) || !valid(lat, lon)) return "";
    Locale locale = context.getResources().getConfiguration().getLocales().get(0);
    String key = String.format(Locale.ROOT, "%s:%.3f:%.3f", locale.toLanguageTag(), lat, lon);
    String previous = cache.get(key);
    if (previous != null) return previous;
    try {
      org.json.JSONObject saved = new org.json.JSONObject(PrivateData.read(context,"placeNameCache","{}"));
      org.json.JSONObject entry = saved.optJSONObject(key);
      if(entry!=null && System.currentTimeMillis()-entry.optLong("time")<90L*24*60*60*1000) {
        String name=entry.optString("name");
        if(!name.isBlank()){cache.put(key,name);return name;}
      }
    } catch (Exception ignored) {}
    if (!Geocoder.isPresent()) return "";
    Future<String> task = null;
    try {
      Context app = context.getApplicationContext();
      task =
          worker.submit(
              () -> {
                List<Address> found = new Geocoder(app, locale).getFromLocation(lat, lon, 1);
                return found == null || found.isEmpty() ? "" : format(found.get(0));
              });
      String result = task.get(8, TimeUnit.SECONDS);
      if (!result.isBlank()) {
        if (cache.size() > 200) cache.clear();
        cache.put(key, result);
        persist(context,key,result);
      }
      return result;
    } catch (Exception error) {
      if (task != null) task.cancel(true);
      return "";
    }
  }

  private static synchronized void persist(Context c,String key,String name) {
    try {
      org.json.JSONObject values=new org.json.JSONObject(PrivateData.read(c,"placeNameCache","{}"));
      values.put(key,new org.json.JSONObject().put("name",name).put("time",System.currentTimeMillis()));
      while(values.length()>200){String oldest=null;long time=Long.MAX_VALUE;for(var it=values.keys();it.hasNext();){String k=it.next();long t=values.optJSONObject(k)==null?0:values.optJSONObject(k).optLong("time");if(t<time){time=t;oldest=k;}}if(oldest==null)break;values.remove(oldest);}
      PrivateData.write(c,"placeNameCache",values.toString());
    } catch (Exception ignored) {}
  }

  static boolean valid(double lat, double lon) {
    return Double.isFinite(lat)
        && Double.isFinite(lon)
        && Math.abs(lat) <= 90
        && Math.abs(lon) <= 180;
  }

  static double[] parseIso6709(String value) {
    if (value == null) return null;
    java.util.regex.Matcher match =
        java.util.regex.Pattern.compile(
                "^([+-]\\d{1,2}(?:\\.\\d+)?)([+-]\\d{1,3}(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?$")
            .matcher(value.trim());
    if (!match.matches()) return null;
    try {
      double lat = Double.parseDouble(match.group(1)), lon = Double.parseDouble(match.group(2));
      return valid(lat, lon) ? new double[] {lat, lon} : null;
    } catch (NumberFormatException error) {
      return null;
    }
  }

  static android.net.Uri mapUri(double lat, double lon) {
    if (!valid(lat, lon)) throw new IllegalArgumentException("Invalid location");
    String point = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon);
    return android.net.Uri.parse("geo:" + point + "?q=" + point);
  }
}
