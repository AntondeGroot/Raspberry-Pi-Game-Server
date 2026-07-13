package ADG.Utils;

import com.google.gwt.storage.client.Storage;

/**
 * Remembers, per browser, the password a player successfully used to enter a
 * password-protected room (keyed by room id, in localStorage).
 *
 * Purpose: if a player is later removed from the room and wants back in, they'd
 * otherwise need to re-type the password — and if the room is now empty, nobody
 * is left to remind them. Storing it lets the join dialog pre-fill it. It's only
 * ever written after a correct entry, so it never leaks a password to a browser
 * that didn't already know it.
 */
public final class RoomPasswordStore {

    private static final String PREFIX = "gr_roompw_";

    private RoomPasswordStore() {}

    public static void put(String roomId, String password) {
        Storage s = Storage.getLocalStorageIfSupported();
        if (s != null && roomId != null && password != null) {
            s.setItem(PREFIX + roomId, password);
        }
    }

    /** Returns the stored password for the room, or {@code null} if none. */
    public static String get(String roomId) {
        Storage s = Storage.getLocalStorageIfSupported();
        if (s == null || roomId == null) return null;
        return s.getItem(PREFIX + roomId);
    }

    public static void remove(String roomId) {
        Storage s = Storage.getLocalStorageIfSupported();
        if (s != null && roomId != null) {
            s.removeItem(PREFIX + roomId);
        }
    }
}
