package ADG.services;

import ADG.config.SpriteSheetsConfig;
import ADG.config.SpriteSheetsConfig.SheetDef;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// Allow any origin — same reason as ChatController (random tunnel URL).
@CrossOrigin(origins = "*")
public class SpriteSheetsController {

    @Autowired
    private SpriteSheetsConfig config;

    @GetMapping("/sprite-sheets")
    public List<SheetDef> getSheets() {
        return config.getSheets();
    }

    /**
     * Returns the global (cross-sheet) profile-pic indices that belong to bot-only sheets
     * and are not excluded. Qwixx uses this to assign robot avatars to computer players.
     */
    @GetMapping("/bot-profile-indices")
    public List<Integer> getBotProfileIndices() {
        return config.botProfileIndices();
    }
}