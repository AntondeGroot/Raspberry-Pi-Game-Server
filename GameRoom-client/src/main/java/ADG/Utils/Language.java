package ADG.Utils;

public enum Language {
    de("Deutsch"),
    en("English"),
    fr("Français"),
    nl("Nederlands"),
    nb("Norsk"),
    it("Italiano");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}