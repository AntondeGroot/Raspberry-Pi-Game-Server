package ADG.services;

import ADG.Lobby.Room;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RoomSseRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> rooms = new ConcurrentHashMap<>();

    SseEmitter subscribe(String roomId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CopyOnWriteArrayList<SseEmitter> emitters =
                rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(roomId, emitter));
        emitter.onTimeout(() -> remove(roomId, emitter));
        emitter.onError(e -> remove(roomId, emitter));
        return emitter;
    }

    /**
     * Whether the given room currently has at least one live SSE connection.
     * The 30s heartbeat prunes dead emitters, so an empty (or absent) list means
     * nobody is connected to this room right now.
     */
    public boolean hasSubscribers(String roomId) {
        List<SseEmitter> emitters = rooms.get(roomId);
        return emitters != null && !emitters.isEmpty();
    }

    public void emit(String roomId, Room room) {
        List<SseEmitter> emitters = rooms.get(roomId);
        if (emitters == null) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(room, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    public void emitClosed(String roomId) {
        List<SseEmitter> emitters = rooms.remove(roomId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("stream_closed").data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        for (List<SseEmitter> emitters : rooms.values()) {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        }
    }

    private void remove(String roomId, SseEmitter emitter) {
        List<SseEmitter> emitters = rooms.get(roomId);
        if (emitters != null) emitters.remove(emitter);
    }
}