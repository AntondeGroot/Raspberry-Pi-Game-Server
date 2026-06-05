package ADG.Utils;

import ADG.UUID;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;

import java.util.Collection;
import java.util.Date;

public class Cookie {

    private static final String PLAYERID   = "playerid";
    private static final String USERNAME   = "username";
    private static final String LANGUAGE   = "language";
    private static final String ADMIN_HINT = "admin_hint";

    private static final long MILLIS_400_DAYS = 400L * 24L * 60L * 60L * 1000L;
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /**
     * Returns true when the {@code admin_hint} cookie is present.
     * The server sets this cookie on a successful admin login so that the lobby
     * can show a "Login" shortcut even after the Spring Security session expires.
     */
    public static boolean hasAdminHint() {
        return "1".equals(Cookies.getCookie(ADMIN_HINT));
    }

    public static String getPlayerId(){
        createPlayerIdCookie();
        return Cookies.getCookie(PLAYERID);
    }

    public static void createPlayerIdCookie(){
        String existing = Cookies.getCookie(PLAYERID);
        if (existing == null || invalidUUID(existing)) {
            Cookies.setCookie(PLAYERID, UUID.get(), longTermExpiry(), null, "/", isSecure());
        }
    }

    public static String getUsername() {
        String value = Cookies.getCookie(USERNAME);
        return value != null ? value : "";
    }

    public static void setUsername(String username) {
        Cookies.setCookie(USERNAME, username, longTermExpiry(), null, "/", isSecure());
    }

    public static Language getLanguage() {
        String value = Cookies.getCookie(LANGUAGE);
        if (value != null) {
            try { return Language.valueOf(value); } catch (IllegalArgumentException ignored) {}
        }
        return Language.en;
    }

    public static void createLanguageCookie() {
        Collection<String> cookieNames = Cookies.getCookieNames();
        if (!cookieNames.contains(LANGUAGE)) {
            Cookies.setCookie(LANGUAGE, Language.en.name(), longTermExpiry(), null, "/", isSecure());
        }
    }

    public static void setLanguage(Language language) {
        Cookies.setCookie(LANGUAGE, language.name(), longTermExpiry(), null, "/", isSecure());
    }

    public static void changeLanguage(Language language) {
        Cookies.setCookie(LANGUAGE, language.name(), longTermExpiry(), null, "/", isSecure());
        reloadWithLocale(language.name());
    }

    // Returns true if a navigation was triggered (caller should return immediately).
    public static boolean syncGwtLocale() {
        Language lang = getLanguage();
        String urlLocale = Window.Location.getParameter("locale");
        if (!lang.name().equals(urlLocale)) {
            reloadWithLocale(lang.name());
            return true;
        }
        return false;
    }

    private static void reloadWithLocale(String locale) {
        String hash = Window.Location.getHash();
        Window.Location.replace(Window.Location.getPath() + "?locale=" + locale + hash);
    }

    private static boolean invalidUUID(String value) {
        return !value.matches(UUID_PATTERN);
    }

    private static Date longTermExpiry() {
        return new Date(new Date().getTime() + MILLIS_400_DAYS);
    }

    private static boolean isSecure() {
        return Window.Location.getProtocol().startsWith("https");
    }
}
