package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser-level E2E tests that verify game-room behaviour as seen by players.
 *
 * Prerequisites:
 *   - Google Chrome must be installed (Selenium Manager downloads ChromeDriver automatically).
 *   - The GWT client must be compiled before running: {@code mvn package -pl GameRoom-client,GameRoom-server}
 *
 * Run selectively: {@code mvn test -Dgroups=e2e -pl GameRoom-server}
 * Exclude from regular runs: {@code mvn test -DexcludedGroups=e2e -pl GameRoom-server}
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameRoomE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomStore roomStore;

    private WebDriver driver;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1400,900",
                "--mute-audio"
        );
        driver = new ChromeDriver(opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
        roomStore.rooms.removeIf(r -> !"Test Room".equals(r.getName()));
        roomStore.inactiveSince.clear();
    }

    // ── LOBBY VISIBILITY ─────────────────────────────────────────────────────

    @Test
    void seededTestRoomAppearsInLobbyOnLoad() {
        openLobbyAs(UUID.randomUUID().toString());
        assertEventuallyInLobby("Test Room");
    }

    @Test
    void roomCreatedByAnotherPlayerIsVisibleInLobby() {
        roomStore.rooms.add(waitingRoom("Foreign Room", "other-player-id"));

        openLobbyAs(UUID.randomUUID().toString());
        assertEventuallyInLobby("Foreign Room");
    }

    @Test
    void pendingRoomIsNotShownInLobby() {
        Room pending = new Room();
        pending.setId(UUID.randomUUID().toString());
        pending.setName("Hidden Room");
        pending.setCreatedByUserId("some-player");
        pending.setStatus(GameStatus.PENDING);
        pending.setGameId("keezen");
        roomStore.rooms.add(pending);

        openLobbyAs(UUID.randomUUID().toString());
        pause(600);
        assertNotInLobby("Hidden Room");
    }

    @Test
    void fullRoomShowsFullStatusBadge() {
        Room room = waitingRoom("Full Room", "creator");
        room.addPlayer("creator");
        room.addPlayerName("creator", "Alice");
        room.setMaxPlayers(1);
        room.setStatus(GameStatus.FULL);
        roomStore.rooms.add(room);

        openLobbyAs(UUID.randomUUID().toString());
        WebElement row = waitForRoomRow("Full Room");
        assertThat(row.findElement(By.className("room-cell-status")).getText())
                .containsIgnoringCase("full");
    }

    @Test
    void roomShowsCorrectPlayerCount() {
        Room room = waitingRoom("Counting Room", "creator");
        room.setMaxPlayers(6);
        room.addPlayer("p1"); room.addPlayerName("p1", "Alice");
        room.addPlayer("p2"); room.addPlayerName("p2", "Bob");
        roomStore.rooms.add(room);

        openLobbyAs(UUID.randomUUID().toString());
        WebElement row = waitForRoomRow("Counting Room");
        assertThat(row.findElement(By.className("room-cell-players")).getText())
                .contains("2").contains("6");
    }

    @Test
    void twoRoomsAreIndependentlyVisible() {
        roomStore.rooms.add(waitingRoom("Room Alpha", "creator-a"));
        roomStore.rooms.add(waitingRoom("Room Beta",  "creator-b"));

        openLobbyAs(UUID.randomUUID().toString());
        assertEventuallyInLobby("Room Alpha");
        assertEventuallyInLobby("Room Beta");
    }

    // ── ROOM VIEW: CREATOR ───────────────────────────────────────────────────

    @Test
    void creatorSeesOwnNameInPlayerSimulation() {
        String creatorId = UUID.randomUUID().toString();
        Room room = waitingRoom("Creator's Room", creatorId);
        roomStore.rooms.add(room);

        // Navigate directly to the room — the GWT #room= hash token bypasses
        // character selection so we avoid clicking canvas elements entirely.
        enterRoomAs(room, creatorId, "VisibleCreator");

        assertThat(waitForPlayerNames()).contains("VisibleCreator");
    }

    @Test
    void creatorSeesStartGameButton() {
        String creatorId = UUID.randomUUID().toString();
        Room room = waitingRoom("Start Button Room", creatorId);
        roomStore.rooms.add(room);

        enterRoomAs(room, creatorId, "TheCreator");

        WebElement startBtn = waitFor(By.className("confirmButton"));
        assertThat(startBtn.isDisplayed())
                .as("Creator must see the Start Game button")
                .isTrue();
    }

    @Test
    void creatorDoesNotSeeWillStartTheGameLabel() {
        String creatorId = UUID.randomUUID().toString();
        Room room = waitingRoom("No Will Start Room", creatorId);
        roomStore.rooms.add(room);

        enterRoomAs(room, creatorId, "Owner");

        WebElement label = waitFor(By.className("startInfoLabel"));
        if (label.isDisplayed()) {
            assertThat(label.getText())
                    .as("Creator must not see the 'will start the game' message")
                    .doesNotContain("will start the game");
        }
    }

    // ── ROOM VIEW: NON-CREATOR ────────────────────────────────────────────────

    @Test
    void nonCreatorDoesNotSeeStartGameButton() {
        String creatorId = UUID.randomUUID().toString();
        String joinerId  = UUID.randomUUID().toString();

        Room room = waitingRoom("Two Player Room", creatorId);
        room.addPlayer(creatorId);
        room.addPlayerName(creatorId, "TheHost");
        room.addPlayerProfile(creatorId, "0");
        roomStore.rooms.add(room);

        enterRoomAs(room, joinerId, "TheGuest");

        WebElement startBtn = waitFor(By.className("confirmButton"));
        assertThat(startBtn.isDisplayed())
                .as("Non-creator must not see the Start Game button")
                .isFalse();
    }

    @Test
    void nonCreatorSeesWillStartTheGameLabel() {
        String creatorId = UUID.randomUUID().toString();
        String joinerId  = UUID.randomUUID().toString();

        Room room = waitingRoom("Host Room", creatorId);
        room.addPlayer(creatorId);
        room.addPlayerName(creatorId, "TheHost");
        room.addPlayerProfile(creatorId, "0");
        roomStore.rooms.add(room);

        enterRoomAs(room, joinerId, "Guest");

        WebElement label = waitFor(By.className("startInfoLabel"));
        assertThat(label.isDisplayed())
                .as("Non-creator must see the 'will start the game' label")
                .isTrue();
        assertThat(label.getText()).contains("will start the game");
    }

    @Test
    void bothPlayersAppearInSimulationAfterSecondPlayerJoins() {
        String creatorId = UUID.randomUUID().toString();
        String joinerId  = UUID.randomUUID().toString();

        Room room = waitingRoom("Shared Room", creatorId);
        room.addPlayer(creatorId);
        room.addPlayerName(creatorId, "FirstPlayer");
        room.addPlayerProfile(creatorId, "0");
        roomStore.rooms.add(room);

        // Add the second player server-side, then navigate directly
        enterRoomAs(room, joinerId, "SecondPlayer");

        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> svgPlayerNames().size() >= 2);
        assertThat(svgPlayerNames()).contains("SecondPlayer", "FirstPlayer");
    }

    // ── ROOM LIFECYCLE ────────────────────────────────────────────────────────

    @Test
    void leavingRoomReturnsToLobby() {
        String creatorId = UUID.randomUUID().toString();
        Room room = waitingRoom("Leave Room", creatorId);
        roomStore.rooms.add(room);

        enterRoomAs(room, creatorId, "Leaver");

        driver.findElement(By.className("cancelButton")).click();

        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("room-table-row")));
        assertThat(driver.findElements(By.className("room-table-row"))).isNotEmpty();
    }

    // ── CHARACTER SELECTION ───────────────────────────────────────────────────

    @Test
    void characterSelectionShowsProfilePicsAndUsernameInput() {
        String creatorId = UUID.randomUUID().toString();
        Room room = waitingRoom("Profile Test Room", creatorId);
        roomStore.rooms.add(room);

        openLobbyAs(creatorId);

        // Click Join to enter character selection
        WebElement joinBtn = new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
            for (WebElement row : d.findElements(By.className("room-table-row"))) {
                if (row.getText().contains("Profile Test Room")) {
                    List<WebElement> btns = row.findElements(By.className("joinRoomButton"));
                    return btns.isEmpty() ? null : btns.get(0);
                }
            }
            return null;
        });
        joinBtn.click();

        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("characterPanel")));

        // Username input must be present and editable
        WebElement usernameInput = driver.findElement(By.cssSelector(".characterPanel .gwt-TextBox"));
        assertThat(usernameInput.isDisplayed()).isTrue();

        // Profile pictures must be available
        List<WebElement> pics = driver.findElements(By.className("profile-pic"));
        assertThat(pics).isNotEmpty();

        // At least one profile must be available (not taken) in a fresh room
        long available = pics.stream()
                .filter(p -> !p.getAttribute("class").contains("profile-pic-taken"))
                .count();
        assertThat(available).isGreaterThan(0);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Adds the player to the room server-side, sets the playerid cookie, and
     * navigates directly to the room view via the GWT {@code #room=} hash token.
     * This bypasses the canvas-based character selection entirely.
     */
    private void enterRoomAs(Room room, String playerId, String displayName) {
        room.addPlayer(playerId);
        room.addPlayerName(playerId, displayName);
        room.addPlayerProfile(playerId, "1");

        // Set cookie on the base domain first, then navigate to the room hash
        driver.get(baseUrl);
        driver.manage().addCookie(new Cookie("playerid", playerId));
        driver.get(baseUrl + "/#room=" + room.getId());

        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("playerSimulation")));
    }

    private void openLobbyAs(String playerId) {
        driver.get(baseUrl);
        driver.manage().addCookie(new Cookie("playerid", playerId));
        driver.navigate().refresh();
    }

    private void assertEventuallyInLobby(String roomName) {
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> d.findElements(By.className("room-table-row")).stream()
                        .anyMatch(r -> r.getText().contains(roomName)));
    }

    private void assertNotInLobby(String roomName) {
        assertThat(driver.findElements(By.className("room-table-row")).stream()
                .anyMatch(r -> r.getText().contains(roomName)))
                .as("Room '%s' must not be visible in lobby", roomName)
                .isFalse();
    }

    private WebElement waitForRoomRow(String roomName) {
        return new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> d.findElements(By.className("room-table-row")).stream()
                        .filter(r -> r.getText().contains(roomName))
                        .findFirst().orElse(null));
    }

    private WebElement waitFor(By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    private List<String> waitForPlayerNames() {
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector(".playerSimulation svg text")));
        return svgPlayerNames();
    }

    private List<String> svgPlayerNames() {
        return driver.findElements(By.cssSelector(".playerSimulation svg text"))
                .stream().map(WebElement::getText)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Room waitingRoom(String name, String creatorId) {
        Room room = new Room();
        room.setId(UUID.randomUUID().toString());
        room.setName(name);
        room.setCreatedByUserId(creatorId);
        room.setStatus(GameStatus.WAITING);
        room.setGameId("keezen");
        room.setMinPlayers(2);
        room.setMaxPlayers(8);
        return room;
    }
}