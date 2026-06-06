package ADG.services;

import ADG.Lobby.Room;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Lets a player remove themselves from a room (called by games when the player
 * clicks "leave / exit to lobby").  Only the player identified by their own
 * {@code playerid} cookie may remove themselves — no admin role required.
 */
@RestController
@RequestMapping("/rooms")
public class RoomPlayerController {

    @Autowired
    private RoomStore roomStore;

    @Autowired
    private RoomSseRegistry sseRegistry;

    @DeleteMapping("/{roomId}/players/{playerId}")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable("roomId") String roomId,
            @PathVariable("playerId") String playerId,
            HttpServletRequest request) {

        String cookiePlayerId = playerIdFromCookie(request);
        if (cookiePlayerId == null || !cookiePlayerId.equals(playerId)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Room> found = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst();

        if (found.isEmpty()) return ResponseEntity.notFound().build();

        Room room = found.get();
        synchronized (room) {
            room.removePlayer(playerId);
        }
        sseRegistry.emit(roomId, room);
        return ResponseEntity.noContent().build();
    }

    private static String playerIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("playerid".equals(c.getName())) return c.getValue();
        }
        return null;
    }
}