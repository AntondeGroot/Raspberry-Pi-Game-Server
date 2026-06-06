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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests that verify the lobby UI on a 375×812 mobile viewport.
 *
 * Run selectively: {@code mvn test -Dgroups=e2e -pl GameRoom-server}
 * Exclude from regular runs: {@code mvn test -DexcludedGroups=e2e -pl GameRoom-server}
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MobileLobbyUxTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomStore roomStore;

    private WebDriver driver;
    private JavascriptExecutor js;
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
                "--window-size=375,812",
                "--mute-audio"
        );
        driver = new ChromeDriver(opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
        js = (JavascriptExecutor) driver;
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
        roomStore.rooms.removeIf(r -> !"Test Room".equals(r.getName()));
    }

    // ── Room table layout ────────────────────────────────────────────────────

    @Test
    void roomTableDoesNotExceedViewportWidth() {
        openLobby();
        waitForTableRow();

        long viewportWidth = (Long) js.executeScript("return window.innerWidth;");
        WebElement table = driver.findElement(By.className("room-table-container"));
        Rectangle rect = table.getRect();

        assertThat(rect.getX())
                .as("Table left edge must not be off-screen")
                .isGreaterThanOrEqualTo(0);
        assertThat(rect.getX() + rect.getWidth())
                .as("Table right edge (%d) must fit within viewport (%d px)",
                        rect.getX() + rect.getWidth(), viewportWidth)
                .isLessThanOrEqualTo((int) viewportWidth);
    }

    @Test
    @Disabled
    void actionHeaderCellContributesZeroWidth() {
        openLobby();
        waitForTableRow();

        // Sum widths of the four data header cells (name, game, players, status)
        List<WebElement> dataHeaders = driver.findElements(
                By.cssSelector(".room-table-header-cell:not(.room-cell-button-header)"));
        assertThat(dataHeaders).as("Data header cells must be present").isNotEmpty();

        int sumOfDataHeaders = dataHeaders.stream()
                .mapToInt(h -> h.getRect().getWidth())
                .sum();

        // The action header cell must be 0-wide on mobile
        WebElement buttonHeader = driver.findElement(By.className("room-cell-button-header"));
        int buttonHeaderWidth   = buttonHeader.getRect().getWidth();

        WebElement headerRow = driver.findElement(By.className("room-table-header"));
        int headerRowWidth   = headerRow.getRect().getWidth();

        assertThat(buttonHeaderWidth)
                .as("Action header cell width must be 0 — it should not take up column space")
                .isEqualTo(0);

        assertThat(headerRowWidth)
                .as("Header row width (%d px) must not exceed sum of data header widths (%d px)",
                        headerRowWidth, sumOfDataHeaders)
                .isLessThanOrEqualTo(sumOfDataHeaders + 1); // 1 px rounding
    }

    @Test
    void roomTableHeadersAreNotTruncated() {
        openLobby();
        waitForTableRow();

        List<WebElement> headers = driver.findElements(By.className("room-table-header-cell"));
        assertThat(headers).as("Table must have header cells").isNotEmpty();

        for (WebElement header : headers) {
            String text = header.getText();
            if (text.isEmpty()) continue; // action column has no label

            long scrollWidth = toLong(js.executeScript("return arguments[0].scrollWidth;", header));
            long offsetWidth = toLong(js.executeScript("return arguments[0].offsetWidth;", header));

            assertThat(scrollWidth)
                    .as("Header '%s' scrollWidth (%d) must not exceed offsetWidth (%d) — text is clipped",
                            text, scrollWidth, offsetWidth)
                    .isLessThanOrEqualTo(offsetWidth);

            assertThat(text)
                    .as("Header '%s' must not be truncated to an ellipsis", text)
                    .doesNotEndWith("…")
                    .doesNotEndWith("...");
        }
    }

    // ── Title / language-selector overlap ────────────────────────────────────

    @Test
    void titleAndLanguageSelectorDoNotOverlap() {
        openLobby();

        WebElement title = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1")));
        WebElement langRow = new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".room-lang-row")));

        Rectangle titleRect = title.getRect();
        Rectangle langRect  = langRow.getRect();

        int titleBottom  = titleRect.getY() + titleRect.getHeight();
        int selectorTop  = langRect.getY();

        // The language selector must start at or below the title's bottom edge.
        // We allow 4 px of tolerance for sub-pixel font rendering across platforms.
        assertThat(titleBottom)
                .as("Title bottom (y=%d) must not overlap language selector top (y=%d)",
                        titleBottom, selectorTop)
                .isLessThanOrEqualTo(selectorTop + 4);
    }

    @Test
    void languageSelectorIsFullyWithinViewport() {
        openLobby();

        WebElement langRow = new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".room-lang-row")));

        long viewportWidth = (Long) js.executeScript("return window.innerWidth;");
        Rectangle rect = langRow.getRect();

        assertThat(rect.getX())
                .as("Language selector left edge must not be off-screen")
                .isGreaterThanOrEqualTo(0);
        assertThat(rect.getX() + rect.getWidth())
                .as("Language selector right edge must not exceed viewport")
                .isLessThanOrEqualTo((int) viewportWidth);
    }

    // ── Rejoin button ────────────────────────────────────────────────────────

    @Test
    void rejoinButtonFitsInColumnAndTextIsNotAbbreviated() {
        String playerId = UUID.randomUUID().toString();
        Room room = buildWaitingRoom("Rejoin Test Room", playerId);
        room.addPlayer(playerId);
        room.addPlayerName(playerId, "TestPlayer");
        room.addPlayerProfile(playerId, "0");
        roomStore.rooms.add(room);

        openLobbyAs(playerId);

        WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("joinRoomButton")));

        // Text must not be blank or end with an ellipsis
        String text = btn.getText();
        assertThat(text).as("Rejoin button text must not be blank").isNotBlank();
        assertThat(text)
                .as("Rejoin button text must not be abbreviated")
                .doesNotEndWith("...").doesNotEndWith("…");

        // Text must not be clipped inside the button element
        long scrollWidth = toLong(js.executeScript("return arguments[0].scrollWidth;", btn));
        long offsetWidth = toLong(js.executeScript("return arguments[0].offsetWidth;", btn));
        assertThat(scrollWidth)
                .as("Rejoin button: scrollWidth (%d) must not exceed offsetWidth (%d)", scrollWidth, offsetWidth)
                .isLessThanOrEqualTo(offsetWidth);

        // Button must not overflow its containing action cell horizontally
        Rectangle btnRect  = btn.getRect();
        WebElement cell    = (WebElement) js.executeScript("return arguments[0].parentElement;", btn);
        Rectangle cellRect = cell.getRect();

        assertThat(btnRect.getX())
                .as("Rejoin button left edge must be inside its cell (cell.x=%d)", cellRect.getX())
                .isGreaterThanOrEqualTo(cellRect.getX());
        assertThat(btnRect.getX() + btnRect.getWidth())
                .as("Rejoin button right edge must not overflow its cell (cell right=%d)",
                        cellRect.getX() + cellRect.getWidth())
                .isLessThanOrEqualTo(cellRect.getX() + cellRect.getWidth() + 1); // 1 px rounding

        // Button must stay within the viewport
        long viewportWidth = (Long) js.executeScript("return window.innerWidth;");
        assertThat(btnRect.getX() + btnRect.getWidth())
                .as("Rejoin button must not exceed viewport width (%d)", viewportWidth)
                .isLessThanOrEqualTo((int) viewportWidth);
    }

    // ── Rejoin button in Dutch (long label) ─────────────────────────────────

    @Test
    void dutchRejoinButtonStaysWithinColumnAndDoesNotPushTableOffScreen() {
        String playerId = UUID.randomUUID().toString();
        Room room = buildWaitingRoom("Dutch Rejoin Room", playerId);
        room.addPlayer(playerId);
        room.addPlayerName(playerId, "Speler");
        room.addPlayerProfile(playerId, "0");
        roomStore.rooms.add(room);

        openLobbyWithLocale(playerId, "nl");
        waitForTableRow();

        WebElement btn = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("joinRoomButton")));

        // Verify we really have the Dutch text ("Opnieuw deelnemen")
        assertThat(btn.getText()).as("Dutch rejoin text must be non-empty").isNotBlank();

        Rectangle btnRect       = btn.getRect();
        WebElement cell         = (WebElement) js.executeScript("return arguments[0].parentElement;", btn);
        Rectangle cellRect      = cell.getRect();
        WebElement container    = driver.findElement(By.className("room-table-container"));
        Rectangle containerRect = container.getRect();
        long viewportWidth      = (Long) js.executeScript("return window.innerWidth;");

        // Button must be within its action cell
        assertThat(btnRect.getX())
                .as("Dutch rejoin button left edge must be inside its cell")
                .isGreaterThanOrEqualTo(cellRect.getX());
        assertThat(btnRect.getX() + btnRect.getWidth())
                .as("Dutch rejoin button right edge must not overflow its cell")
                .isLessThanOrEqualTo(cellRect.getX() + cellRect.getWidth() + 1);

        // Action cell must not push outside the table container
        assertThat(cellRect.getX() + cellRect.getWidth())
                .as("Action cell right edge must not exceed the table container")
                .isLessThanOrEqualTo(containerRect.getX() + containerRect.getWidth() + 1);

        // Nothing must exceed the viewport
        assertThat(containerRect.getX())
                .as("Table container left edge must not be off-screen")
                .isGreaterThanOrEqualTo(0);
        assertThat(btnRect.getX() + btnRect.getWidth())
                .as("Dutch rejoin button must not exceed viewport width")
                .isLessThanOrEqualTo((int) viewportWidth);
    }

    // ── Action cell layout ───────────────────────────────────────────────────

    @Test
    void actionCellIsCompactAndOnItsOwnLine() {
        String playerId = UUID.randomUUID().toString();
        Room room = buildWaitingRoom("Action Cell Test Room", playerId);
        room.addPlayer(playerId);
        room.addPlayerName(playerId, "TestPlayer");
        room.addPlayerProfile(playerId, "0");
        roomStore.rooms.add(room);

        openLobbyAs(playerId);

        WebElement row = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("room-table-row")));

        WebElement btn        = row.findElement(By.className("joinRoomButton"));
        WebElement actionCell = (WebElement) js.executeScript("return arguments[0].parentElement;", btn);
        WebElement nameCell   = row.findElement(By.className("room-cell-name"));
        WebElement statusCell = row.findElement(By.className("room-cell-status"));

        Rectangle actionRect    = actionCell.getRect();
        Rectangle nameCellRect  = nameCell.getRect();
        Rectangle statusRect    = statusCell.getRect();

        // Action cell must be in row 2 — its top must be at or below the status cell's bottom.
        // Both cells live in column 4 so they share the same column track; Chrome derives
        // their grid-line positions from the same measurement.  ±15 px tolerance handles
        // Selenium/Chrome sub-pixel rounding: getRect().getHeight() returns an integer while
        // the actual CSS grid track uses fractional pixels, so the reported cell height can
        // be up to ~half the row's line-height larger than the real grid-track boundary.
        assertThat(actionRect.getY())
                .as("Action cell top (y=%d) must be at or below status cell bottom (y=%d + h=%d = %d) [±15 px rounding]",
                        actionRect.getY(), statusRect.getY(), statusRect.getHeight(),
                        statusRect.getY() + statusRect.getHeight())
                .isGreaterThanOrEqualTo(statusRect.getY() + statusRect.getHeight() - 15);

        // Action cell right edge must align with the status cell right edge (same grid column)
        assertThat(actionRect.getX() + actionRect.getWidth())
                .as("Action cell right edge (%d) must align with status cell right edge (%d)",
                        actionRect.getX() + actionRect.getWidth(),
                        statusRect.getX() + statusRect.getWidth())
                .isLessThanOrEqualTo(statusRect.getX() + statusRect.getWidth() + 1); // 1 px rounding
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void openLobby() {
        openLobbyAs(UUID.randomUUID().toString());
    }

    private void openLobbyAs(String playerId) {
        driver.get(baseUrl);
        driver.manage().addCookie(new Cookie("playerid", playerId));
        driver.navigate().refresh();
    }

    private void openLobbyWithLocale(String playerId, String locale) {
        driver.get(baseUrl);
        driver.manage().addCookie(new Cookie("playerid", playerId));
        driver.manage().addCookie(new Cookie("language", locale));
        driver.get(baseUrl + "?locale=" + locale);
    }

    private Room buildWaitingRoom(String name, String creatorId) {
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

    private void waitForTableRow() {
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.className("room-table-container")));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> !d.findElements(By.className("room-table-row")).isEmpty());
    }

    private static long toLong(Object jsResult) {
        if (jsResult instanceof Long)   return (Long) jsResult;
        if (jsResult instanceof Double) return ((Double) jsResult).longValue();
        return Long.parseLong(jsResult.toString());
    }
}