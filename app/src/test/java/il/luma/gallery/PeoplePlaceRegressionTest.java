package il.luma.gallery;
import static org.junit.Assert.*;
import android.graphics.*;
import android.net.Uri;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PeoplePlaceRegressionTest {
  @Test public void multipleExamplesMatchAnAngleThatTheFirstExampleMisses() throws Exception {
    Map<String,float[]> legacy=new LinkedHashMap<>();legacy.put("person",new float[]{1,0,0});
    FaceSamples bank=new FaceSamples(new JSONObject(),legacy);
    bank.add("person",new float[]{.8f,.6f,0});
    float[] angle=FaceSamples.normalize(new float[]{.4f,.9165f,0});
    assertTrue(AiIndex.cosine(angle,legacy.get("person"))<.55);
    assertEquals("person",bank.match(angle,.55,Collections.emptySet()));
    assertNull(bank.match(angle,.55,Collections.singleton("person")));
  }
  @Test public void ambiguousDifferentPeopleAreNotForcedTogether() throws Exception {
    Map<String,float[]> legacy=new LinkedHashMap<>();legacy.put("a",new float[]{1,0});legacy.put("b",new float[]{.999f,.001f});
    FaceSamples bank=new FaceSamples(new JSONObject(),legacy);
    assertNull(bank.match(new float[]{1,0},.55,Collections.emptySet()));
  }
  @Test public void examplesAreBoundedPersistedAndPruned() throws Exception {
    Map<String,float[]> legacy=new LinkedHashMap<>();legacy.put("a",new float[]{1,0,0});legacy.put("deleted",new float[]{0,1,0});
    FaceSamples bank=new FaceSamples(new JSONObject(),legacy);
    for(int i=0;i<40;i++)bank.add("a",new float[]{1,(float)Math.sin(i),i*.02f});
    assertTrue(bank.samples.get("a").size()<=FaceSamples.LIMIT);
    JSONObject saved=bank.json(Collections.singleton("a"));assertFalse(saved.has("deleted"));
    FaceSamples restored=new FaceSamples(saved,Collections.singletonMap("a",new float[]{1,0,0}));
    assertEquals(bank.samples.get("a").size(),restored.samples.get("a").size());
    assertNull(FaceSamples.normalize(new float[]{Float.NaN,1}));
  }
  MainActivity.Media media(int id){MainActivity.Media m=new MainActivity.Media();m.uri=Uri.parse("content://media/external/images/media/"+id);m.name="fixture-"+id+".jpg";m.mime="image/jpeg";m.path="Camera";return m;}
  @Test public void placeUsesActualGpsDeduplicatesPhotosAndRespectsVisibleScope() {
    PlaceCluster group=new PlaceCluster(32.08123,34.78123);MainActivity.Media a=media(1),b=media(2);
    group.add(a);group.add(a);group.add(b);assertEquals(2,group.members.size());assertEquals(32.08123,group.latitude,0);
    assertEquals(PlaceCluster.key(32.081,34.781),PlaceCluster.key(32.082,34.782));
    assertEquals(Collections.singletonList(b),group.visible(Collections.singletonList(b)));
  }
  @Test public void mapGroupsNearbyPhotosAndScopedViewerContainsOnlyThatPlace() throws Exception {
    try(var controller=Robolectric.buildActivity(ModernActivity.class).setup()){
      ModernActivity a=controller.get();a.prefs.edit().putBoolean("mapNetwork",false).commit();
      PlacesMap map=new PlacesMap(a,32.081,34.781);map.show();MainActivity.Media first=media(1),second=media(2);
      map.add(32.081,34.781,"",first);map.add(32.082,34.782,"",second);
      assertEquals(1,map.clusters.size());PlaceCluster group=map.clusters.values().iterator().next();
      assertEquals(2,group.members.size());assertNotNull(group.marker.getIcon());
      ModernActivity.Viewer viewer=a.new Viewer(first,Arrays.asList(first,second));assertEquals(2,viewer.pages.size());
      assertFalse(viewer.pages.contains(media(3)));map.dismiss();
    }
  }
  @Test public void successfulPlaceNameSurvivesRestartAndPrivacyOffStillWins() throws Exception {
    var context=RuntimeEnvironment.getApplication();String locale=context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
    String key=String.format(Locale.ROOT,"%s:%.3f:%.3f",locale,12.345,45.678);
    JSONObject cached=new JSONObject().put(key,new JSONObject().put("name","Fixture City, Fixture Country").put("time",System.currentTimeMillis()));
    context.getSharedPreferences("luma",0).edit().putBoolean("placeNames",true).putString("placeNameCache",cached.toString()).commit();
    assertEquals("Fixture City, Fixture Country",LocationNames.resolve(context,12.345,45.678));
    context.getSharedPreferences("luma",0).edit().putBoolean("placeNames",false).commit();assertEquals("",LocationNames.resolve(context,12.345,45.678));
  }
  @Test public void resettingPlacesClearsPersistentAndMemoryNames() throws Exception {
    var context=RuntimeEnvironment.getApplication();String locale=context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
    String key=String.format(Locale.ROOT,"%s:%.3f:%.3f",locale,21.234,67.890);
    JSONObject data=new JSONObject().put(key,new JSONObject().put("name","Another Fixture City").put("time",System.currentTimeMillis()));
    var prefs=context.getSharedPreferences("luma",0);prefs.edit().putBoolean("placeNames",true).putString("placeNameCache",data.toString()).commit();
    assertEquals("Another Fixture City",LocationNames.resolve(context,21.234,67.890));
    LocationNames.clear(context);assertFalse(prefs.contains("placeNameCache"));
    assertNotEquals("Another Fixture City",LocationNames.resolve(context,21.234,67.890));
    prefs.edit().putBoolean("placeNames",false).commit();assertEquals("",LocationNames.resolve(context,21.234,67.890));
  }

}
