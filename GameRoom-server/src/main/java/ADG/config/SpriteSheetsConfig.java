package ADG.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sprite-sheets")
public class SpriteSheetsConfig {

    public static class SheetDef {
        private String url;
        private int cols = 4;
        private int rows = 4;
        private int imgWidth  = 1024;
        private int imgHeight = 1024;
        private int insetPx   = 0;
        /** 1-based indices to hide; all others are shown. Ignored when onlyInclude is non-empty. */
        private List<Integer> exclude     = new ArrayList<>();
        /** 1-based indices to show; all others are hidden. Takes precedence over exclude. */
        private List<Integer> onlyInclude = new ArrayList<>();
        /** When true, this sheet is reserved for computer/bot players and hidden from human selection. */
        private boolean botOnly = false;

        public String         getUrl()         { return url; }
        public int            getCols()        { return cols; }
        public int            getRows()        { return rows; }
        public int            getImgWidth()    { return imgWidth; }
        public int            getImgHeight()   { return imgHeight; }
        public int            getInsetPx()     { return insetPx; }
        public List<Integer>  getExclude()     { return exclude; }
        public List<Integer>  getOnlyInclude() { return onlyInclude; }
        public boolean        isBotOnly()      { return botOnly; }

        public void setUrl(String url)                      { this.url = url; }
        public void setCols(int cols)                       { this.cols = cols; }
        public void setRows(int rows)                       { this.rows = rows; }
        public void setImgWidth(int imgWidth)               { this.imgWidth = imgWidth; }
        public void setImgHeight(int imgHeight)             { this.imgHeight = imgHeight; }
        public void setInsetPx(int insetPx)                 { this.insetPx = insetPx; }
        public void setExclude(List<Integer> exclude)       { this.exclude = exclude; }
        public void setOnlyInclude(List<Integer> onlyInclude) { this.onlyInclude = onlyInclude; }
        public void setBotOnly(boolean botOnly)             { this.botOnly = botOnly; }
    }

    private List<SheetDef> sheets = new ArrayList<>();

    public List<SheetDef> getSheets() { return sheets; }
    public void setSheets(List<SheetDef> sheets) { this.sheets = sheets; }

    public List<Integer> botProfileIndices() {
        List<Integer> indices = new ArrayList<>();
        int globalOffset = 0;
        for (SheetDef sheet : sheets) {
            int total = sheet.getCols() * sheet.getRows();
            if (sheet.isBotOnly()) {
                Set<Integer> excluded = resolveExcluded(sheet, total);
                for (int local = 0; local < total; local++) {
                    if (!excluded.contains(local)) indices.add(globalOffset + local);
                }
            }
            globalOffset += total;
        }
        return indices;
    }

    private Set<Integer> resolveExcluded(SheetDef sheet, int total) {
        if (!sheet.getOnlyInclude().isEmpty()) {
            Set<Integer> keep = new HashSet<>();
            for (int i : sheet.getOnlyInclude()) keep.add(i - 1);
            Set<Integer> excluded = new HashSet<>();
            for (int i = 0; i < total; i++) if (!keep.contains(i)) excluded.add(i);
            return excluded;
        }
        Set<Integer> excluded = new HashSet<>();
        for (int i : sheet.getExclude()) excluded.add(i - 1);
        return excluded;
    }
}