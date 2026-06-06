package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rooms")
public class GameFinishedCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(GameFinishedCallbackController.class);

    @Autowired private RoomStore roomStore;
    @Autowired private RoomSseRegistry sseRegistry;
    @Autowired private LobbySseRegistry lobbySseRegistry;

    @PostMapping("/{roomId}/game-finished")
    public ResponseEntity<Void> gameFinished(@PathVariable("roomId") String roomId) {
        roomStore.rooms.stream()
                .filter(r -> roomId.equals(r.getId()))
                .findFirst()
                .ifPresent(room -> {
                    logger.info("Game-finished callback received for room {}, resetting to WAITING", roomId);
                    room.setStatus(GameStatus.WAITING);
                    room.setGameSessionId(null);
                    sseRegistry.emit(roomId, room);
                    emitLobbyUpdate();
                });
        return ResponseEntity.noContent().build();
    }

    private void emitLobbyUpdate() {
        List<Room> visible = roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .collect(Collectors.toList());
        lobbySseRegistry.emit(visible);
    }
}