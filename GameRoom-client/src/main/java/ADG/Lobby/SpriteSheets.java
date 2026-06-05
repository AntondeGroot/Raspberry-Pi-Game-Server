package ADG.Lobby;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime sprite-sheet configuration loaded from /sprite-sheets at startup.
 * Edit sprite-sheets.yaml on the server to add/remove sheets or change exclusions.
 */
public class SpriteSheets {

    public static class Sheet {
        public final String url;
        final int cols;
        final int rows;
        final int imgW;
        final int imgH;
        final int insetPx;
        /** 0-based local indices that are excluded. */
        final int[] excluded;
        /** When true, this sheet is reserved for bot/computer players and hidden from human selection. */
        public final boolean botOnly;

        Sheet(String url, int cols, int rows, int imgW, int imgH, int insetPx, int[] excluded, boolean botOnly) {
            this.url      = url;
            this.cols     = cols;
            this.rows     = rows;
            this.imgW     = imgW;
            this.imgH     = imgH;
            this.insetPx  = insetPx;
            this.excluded = excluded;
            this.botOnly  = botOnly;
        }

        int    total()  { return cols * rows; }
        double cellW()  { return (double) imgW / cols; }
        double cellH()  { return (double) imgH / rows; }

        double srcX(int localCol) { return cellW() * localCol + insetPx; }
        double srcY(int localRow) { return cellH() * localRow + insetPx; }
        double srcW()             { return cellW() - 2.0 * insetPx; }
        double srcH()             { return cellH() - 2.0 * insetPx; }

        boolean isExcluded(int localIndex) {
            for (int e : excluded) if (e == localIndex) return true;
            return false;
        }
    }

    private static final List<Sheet> sheets = new ArrayList<>();

    static boolean isLoaded() { return !sheets.isEmpty(); }

    /**
     * Populate the config from the JSON array returned by GET /sprite-sheets.
     * Indices in the JSON are 1-based (as written in sprite-sheets.yaml);
     * they are converted to 0-based here.
     */
    public static void load(JSONArray arr) {
        sheets.clear();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.get(i).isObject();
            if (o == null) continue;

            String url     = str(o, "url");
            int    cols    = num(o, "cols",      4);
            int    rows    = num(o, "rows",      4);
            int    imgW    = num(o, "imgWidth",  1024);
            int    imgH    = num(o, "imgHeight", 1024);
            int     insetPx = num(o, "insetPx",  0);
            boolean botOnly = bool(o, "botOnly");
            int     total   = cols * rows;

            // onlyInclude takes precedence over exclude (both 1-based in JSON)
            JSONValue onlyVal = o.get("onlyInclude");
            JSONArray onlyArr = onlyVal != null ? onlyVal.isArray() : null;

            int[] excluded;
            if (onlyArr != null && onlyArr.size() > 0) {
                // build excluded = all indices NOT in onlyInclude
                boolean[] keep = new boolean[total];
                for (int k = 0; k < onlyArr.size(); k++) {
                    int oneBased = (int) onlyArr.get(k).isNumber().doubleValue();
                    int zeroBased = oneBased - 1;
                    if (zeroBased >= 0 && zeroBased < total) keep[zeroBased] = true;
                }
                List<Integer> exList = new ArrayList<>();
                for (int j = 0; j < total; j++) if (!keep[j]) exList.add(j);
                excluded = toIntArray(exList);
            } else {
                JSONValue exclVal = o.get("exclude");
                JSONArray exclArr = exclVal != null ? exclVal.isArray() : null;
                List<Integer> exList = new ArrayList<>();
                if (exclArr != null) {
                    for (int k = 0; k < exclArr.size(); k++) {
                        int oneBased = (int) exclArr.get(k).isNumber().doubleValue();
                        exList.add(oneBased - 1); // convert to 0-based
                    }
                }
                excluded = toIntArray(exList);
            }

            sheets.add(new Sheet(url, cols, rows, imgW, imgH, insetPx, excluded, botOnly));
        }
    }

    static int totalSprites() {
        int n = 0;
        for (Sheet s : sheets) n += s.total();
        return n;
    }

    /** Sheet that owns the given global profile index. */
    static Sheet sheetFor(int globalIndex) {
        int offset = 0;
        for (Sheet s : sheets) {
            if (globalIndex < offset + s.total()) return s;
            offset += s.total();
        }
        return sheets.isEmpty() ? null : sheets.get(0);
    }

    /** Local (within-sheet) index for the given global profile index. */
    static int localIndexFor(int globalIndex) {
        int offset = 0;
        for (Sheet s : sheets) {
            if (globalIndex < offset + s.total()) return globalIndex - offset;
            offset += s.total();
        }
        return 0;
    }

    /** All loaded sheets (for iteration). */
    public static List<Sheet> all() { return sheets; }

    // -----------------------------------------------------------------------

    private static String str(JSONObject o, String key) {
        JSONValue v = o.get(key);
        return (v != null && v.isString() != null) ? v.isString().stringValue() : "";
    }

    private static int num(JSONObject o, String key, int defaultVal) {
        JSONValue v = o.get(key);
        return (v != null && v.isNumber() != null) ? (int) v.isNumber().doubleValue() : defaultVal;
    }

    private static boolean bool(JSONObject o, String key) {
        JSONValue v = o.get(key);
        return v != null && v.isBoolean() != null && v.isBoolean().booleanValue();
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
