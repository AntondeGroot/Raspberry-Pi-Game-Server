package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class RejoinController {

    @Autowired
    private RoomStore roomStore;

    @GetMapping("/rejoin")
    public void rejoin(
            @RequestParam("pid") String playerId,
            @RequestParam(value = "rid", required = false) String roomId,
            HttpServletResponse response) throws IOException {

        Cookie cookie = new Cookie("playerid", playerId);
        cookie.setPath("/");
        cookie.setMaxAge(365 * 24 * 60 * 60);
        response.addCookie(cookie);

        if (roomId != null) {
            Room room = roomStore.rooms.stream()
                    .filter(r -> r.getId().equals(roomId))
                    .findFirst()
                    .orElse(null);

            if (room != null && GameStatus.PLAYING.equals(room.getStatus())
                    && room.getGameId() != null && room.getGameSessionId() != null) {
                // Send the player straight back into the active game
                response.sendRedirect("/" + room.getGameId() + "/?sessionid="
                        + room.getGameSessionId() + "&playerid=" + playerId);
                return;
            }

            if (room != null) {
                // WAITING / FULL — let the lobby handle auto-navigation
                response.sendRedirect("/?rejoin=" + roomId);
                return;
            }
        }

        response.sendRedirect("/");
    }
}