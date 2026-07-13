package ADG.audio;

import ADG.Utils.Notify;
import com.google.gwt.core.client.GWT;

public class AudioPlayer {

  public static final String BUTTON_CLICK =
      "zapsplat_multimedia_button_click_bright_001_92098.mp3";
  public static final String ERROR =
      "zapsplat_multimedia_game_sound_error_drop_item_negative_113980.mp3";
  public static final String PLAYER_ENTER =
      "zapsplat_multimedia_game_sound_simple_chime_114648.mp3";

  public static void play(String filename) {
    playUrl(GWT.getHostPageBaseURL() + "assets/audio/" + filename, 1.0);
  }

  public static void play(String filename, double volume) {
    playUrl(GWT.getHostPageBaseURL() + "assets/audio/" + filename, volume);
  }

  /** Play the error sound and show a styled error toast. */
  public static void errorAlert(String message) {
    play(ERROR);
    Notify.error(message);
  }

  private static native void playUrl(String url, double volume) /*-{
    var audio = new Audio(url);
    audio.volume = volume;
    var promise = audio.play();
    if (promise !== undefined) {
      promise["catch"](function(e) {}); // autoplay blocked — ignore
    }
  }-*/;
}