package dev.dasuro.customnickname.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;

/**
 * Stores the storage mode preference (GLOBAL vs LOCAL) in a small settings file
 * that always lives in the local (modpack) config directory.
 */
public class StorageConfig {

    public enum StorageMode {
        GLOBAL, LOCAL
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File SETTINGS_FILE =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("customnickname-settings.json")
                    .toFile();

    private static StorageMode mode = StorageMode.GLOBAL;
    private static boolean showIndicator = false;

    /** The indicator character appended to nicknames when the option is enabled. */
    public static final String INDICATOR = " \u270E"; // yellow pencil ✎

    public static void load() {
        if (!SETTINGS_FILE.exists()) {
            mode = StorageMode.GLOBAL;
            showIndicator = false;
            return;
        }

        try (Reader r = new FileReader(SETTINGS_FILE)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj != null && obj.has("storageMode")) {
                String val = obj.get("storageMode").getAsString();
                try {
                    mode = StorageMode.valueOf(val);
                } catch (IllegalArgumentException e) {
                    mode = StorageMode.GLOBAL;
                }
            }
            if (obj != null && obj.has("showIndicator")) {
                showIndicator = obj.get("showIndicator").getAsBoolean();
            }
        } catch (Exception e) {
            mode = StorageMode.GLOBAL;
            showIndicator = false;
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(SETTINGS_FILE)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("storageMode", mode.name());
            obj.addProperty("showIndicator", showIndicator);
            GSON.toJson(obj, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static StorageMode getMode() {
        return mode;
    }

    public static void setMode(StorageMode newMode) {
        if (newMode == mode) return;
        mode = newMode;
        save();
    }

    public static boolean isShowIndicator() {
        return showIndicator;
    }

    public static void setShowIndicator(boolean value) {
        if (value == showIndicator) return;
        showIndicator = value;
        save();
    }
}

