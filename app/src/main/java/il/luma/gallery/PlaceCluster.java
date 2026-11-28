package il.luma.gallery;
import java.util.*;
/** Roughly kilometre-scale cells, with real representative GPS rather than cell-center geocoding. */
final class PlaceCluster {
  final double latitude,longitude;
  final LinkedHashMap<String,MainActivity.Media> members=new LinkedHashMap<>();
  String name="";
  android.graphics.Bitmap thumbnail;
  org.osmdroid.views.overlay.Marker marker;
  boolean thumbnailRequested;
  PlaceCluster(double lat,double lon){latitude=lat;longitude=lon;}
  static String key(double lat,double lon){
    if(!LocationNames.valid(lat,lon))throw new IllegalArgumentException("Invalid point");
    return String.format(Locale.ROOT,"%.2f, %.2f",Math.round(lat*100)/100d,Math.round(lon*100)/100d);
  }
  void add(MainActivity.Media item){members.put(item.uri.toString(),item);}
  ArrayList<MainActivity.Media> visible(java.util.List<MainActivity.Media> scope){
    ArrayList<MainActivity.Media> out=new ArrayList<>();
    for(MainActivity.Media item:scope)if(members.containsKey(item.uri.toString()))out.add(item);
    return out;
  }
}
