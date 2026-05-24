package ADG.Lobby;

import com.google.gwt.user.client.rpc.IsSerializable;
import java.util.ArrayList;

public class GameOption implements IsSerializable {

    private String key;
    private String label;
    private String description;
    private String labelKey;
    private String descriptionKey;
    private String type;
    private String defaultValue;
    private ArrayList<String> choices;
    private Integer minValue;
    private Integer maxValue;
    private boolean adminOnly;

    public GameOption() {}

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLabelKey() { return labelKey; }
    public void setLabelKey(String labelKey) { this.labelKey = labelKey; }

    public String getDescriptionKey() { return descriptionKey; }
    public void setDescriptionKey(String descriptionKey) { this.descriptionKey = descriptionKey; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public ArrayList<String> getChoices() { return choices; }
    public void setChoices(ArrayList<String> choices) { this.choices = choices; }

    public Integer getMinValue() { return minValue; }
    public void setMinValue(Integer minValue) { this.minValue = minValue; }

    public Integer getMaxValue() { return maxValue; }
    public void setMaxValue(Integer maxValue) { this.maxValue = maxValue; }

    public boolean isAdminOnly() { return adminOnly; }
    public void setAdminOnly(boolean adminOnly) { this.adminOnly = adminOnly; }
}