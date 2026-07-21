package ADG.services;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomNameController {

    private List<String> names;
    private final Random random = new Random();

    @PostConstruct
    public void loadNames() {
        Path piPath = Path.of("/opt/gameroom/room-names.txt");
        if (Files.exists(piPath)) {
            try {
                names = Files.readAllLines(piPath).stream()
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.startsWith("#"))
                        .collect(Collectors.toList());
                return;
            } catch (IOException ignored) {}
        }
        try (InputStream is = getClass().getResourceAsStream("/room-names.txt")) {
            if (is != null) {
                names = new BufferedReader(new InputStreamReader(is)).lines()
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.startsWith("#"))
                        .collect(Collectors.toList());
            }
        } catch (IOException ignored) {}

        if (names == null || names.isEmpty()) {
            names = List.of("Game Room");
        }
    }

    @GetMapping("/random-room-name")
    public ResponseEntity<Map<String, String>> getRandomName() {
        return ResponseEntity.ok(Map.of("name", names.get(random.nextInt(names.size()))));
    }

    /** Returns the full pool of candidate room names so the client can pick one
     * that isn't already taken (retrying until it finds a free name). */
    @GetMapping("/room-names")
    public ResponseEntity<List<String>> getAllNames() {
        return ResponseEntity.ok(names);
    }
}