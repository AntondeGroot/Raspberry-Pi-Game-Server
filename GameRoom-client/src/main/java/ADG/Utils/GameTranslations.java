package ADG.Utils;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import java.util.HashMap;

public class GameTranslations {

    private static final HashMap<String, String> translations = new HashMap<>();

    public static void load(String baseUrl, Language lang, Runnable onComplete) {
        String url = baseUrl + "/i18n/" + lang.name() + ".json";
        GWT.log("Loading translations from: " + url);
        try {
            new RequestBuilder(RequestBuilder.GET, url).sendRequest(null, new RequestCallback() {
                public void onResponseReceived(Request req, Response res) {
                    if (res.getStatusCode() == 200) {
                        try {
                            String text = res.getText();
                            if (text == null || text.isEmpty()) {
                                GWT.log("Warning: Translation response body is empty");
                                onComplete.run();
                                return;
                            }
                            translations.clear();
                            flatten("", JSONParser.parseStrict(text).isObject());
                            GWT.log("Loaded " + translations.size() + " translation keys");
                        } catch (Exception e) {
                            GWT.log("Error parsing translation JSON: " + e.getMessage());
                        }
                        onComplete.run();
                    } else {
                        GWT.log("Failed to load translations: HTTP " + res.getStatusCode() + " " + res.getStatusText());
                        if (lang != Language.en) {
                            GWT.log("Falling back to English game translations");
                            load(baseUrl, Language.en, onComplete);
                        } else {
                            onComplete.run();
                        }
                    }
                }
                public void onError(Request req, Throwable t) {
                    GWT.log("Error loading translations: " + t.getMessage());
                    if (lang != Language.en) {
                        GWT.log("Falling back to English game translations");
                        load(baseUrl, Language.en, onComplete);
                    } else {
                        onComplete.run();
                    }
                }
            });
        } catch (RequestException e) {
            GWT.log("RequestException loading translations: " + e.getMessage());
            onComplete.run();
        }
    }

    private static void flatten(String prefix, JSONObject obj) {
        if (obj == null) return;
        for (String k : obj.keySet()) {
            String fullKey = prefix.isEmpty() ? k : prefix + "." + k;
            JSONValue val = obj.get(k);
            if (val.isString() != null) {
                translations.put(fullKey, val.isString().stringValue());
            } else if (val.isObject() != null) {
                flatten(fullKey, val.isObject());
            }
        }
    }

    public static String translate(String key) {
        String v = translations.get(key);
        if (v == null) {
            GWT.log("Missing translation: " + key);
        }
        return v != null ? v : key;
    }

    public static int getLoadedTranslationCount() {
        return translations.size();
    }
}