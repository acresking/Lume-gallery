package il.luma.gallery;

import android.widget.FrameLayout;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.*;

/** Actual Media3 effects in preview, using the same video effect builder as export. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class VideoPreview extends FrameLayout {
  final ExoPlayer player;
  final PlayerView view;
  VideoRecipe recipe;
  boolean released;
  java.util.function.Consumer<String> error = s -> {};

  VideoPreview(android.content.Context c) {
    super(c);
    view = new PlayerView(c);
    view.setUseController(false);
    player = new ExoPlayer.Builder(c).build();
    view.setPlayer(player);
    addView(view, new LayoutParams(-1, -1));
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlayerError(PlaybackException e) {
            error.accept("תצוגה מקדימה אינה זמינה לקובץ או לאפקט הזה");
          }

          @Override
          public void onMediaItemTransition(MediaItem item, int reason) {
            if (recipe != null && player.getCurrentMediaItemIndex() < recipe.clips.size())
              player.setVideoEffects(
                  recipe.videoEffects(recipe.clips.get(player.getCurrentMediaItemIndex()), false));
          }
        });
  }

  void apply(VideoRecipe r) {
    if (released || r.clips.isEmpty()) return;
    long position = player.getCurrentPosition();
    int index = Math.max(0, player.getCurrentMediaItemIndex());
    boolean play = player.getPlayWhenReady();
    boolean rebuild =
        recipe == null
            || recipe.start != r.start
            || recipe.end != r.end
            || recipe.clips.size() != r.clips.size();
    if (!rebuild)
      for (int i = 0; i < r.clips.size(); i++)
        if (!recipe.clips.get(i).uri.equals(r.clips.get(i).uri)) rebuild = true;
    recipe = r.copy();
    ArrayList<MediaItem> items = new ArrayList<>();
    for (int i = 0; i < r.clips.size(); i++) items.add(r.mediaItem(i));
    if (rebuild)
      player.setMediaItems(items, Math.min(index, items.size() - 1), Math.max(0, position));
    player.setVideoEffects(r.videoEffects(r.clips.get(Math.min(index, r.clips.size() - 1)), false));
    player.setPlaybackParameters(new PlaybackParameters(r.speed, r.pitch));
    player.setVolume(r.mute ? 0 : r.volume);
    if (rebuild) player.prepare();
    player.setPlayWhenReady(play);
  }

  void updateVolume() {
    if (released || recipe == null || recipe.clips.isEmpty()) return;
    int i = Math.min(currentClip(), recipe.clips.size() - 1);
    long ms = player.getCurrentPosition();
    float factor = 1;
    if (recipe.fadeIn > 0) factor = Math.min(factor, ms / (recipe.fadeIn * 1000 * recipe.speed));
    if (recipe.fadeOut > 0)
      factor =
          Math.min(
              factor,
              Math.max(0, recipe.sourceDuration(i) - ms) / (recipe.fadeOut * 1000 * recipe.speed));
    player.setVolume(recipe.mute ? 0 : recipe.volume * Math.max(0, Math.min(1, factor)));
  }

  boolean isPlaying() {
    return !released && player.isPlaying();
  }

  void start() {
    if (!released) {
      if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0, 0);
      player.play();
    }
  }

  void pause() {
    if (!released) player.pause();
  }

  void seekTo(int sourceMs) {
    if (!released) player.seekTo(0, Math.max(0, sourceMs - (recipe == null ? 0 : recipe.start)));
  }

  int getCurrentPosition() {
    return released
        ? 0
        : (int)
            (player.getCurrentPosition()
                + (recipe != null && player.getCurrentMediaItemIndex() == 0 ? recipe.start : 0));
  }

  int currentClip() {
    return released ? 0 : Math.max(0, player.getCurrentMediaItemIndex());
  }

  void stopPlayback() {
    if (released) return;
    released = true;
    view.setPlayer(null);
    player.release();
  }
}
