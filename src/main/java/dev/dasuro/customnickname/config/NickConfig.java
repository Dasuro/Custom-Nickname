package dev.dasuro.customnickname.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class NickConfig {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final String NICK_FILE_NAME = "customnickname.json";

    /**
     * Returns the config file based on the current storage mode.
     * GLOBAL  → ~/.minecraft/config/customnickname.json  (shared across all instances)
     * LOCAL   → <modpack>/config/customnickname.json      (modpack-specific)
     */
    private static File getConfigFile() {
        if (StorageConfig.getMode() == StorageConfig.StorageMode.GLOBAL) {
            return getGlobalConfigFile();
        }
        return getLocalConfigFile();
    }

    /** Local path – the modpack / instance config dir (FabricLoader). */
    static File getLocalConfigFile() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(NICK_FILE_NAME)
                .toFile();
    }

    /**
     * Global path – always inside the real .minecraft/config folder.
     * We use the standard OS-dependent .minecraft location so that every
     * Fabric instance (regardless of launcher) can share one file.
     */
    static File getGlobalConfigFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path minecraftDir;

        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            minecraftDir = Paths.get(appdata != null ? appdata : System.getProperty("user.home"), ".minecraft");
        } else if (os.contains("mac")) {
            minecraftDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        } else {
            minecraftDir = Paths.get(System.getProperty("user.home"), ".minecraft");
        }

        File configDir = minecraftDir.resolve("config").toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, NICK_FILE_NAME);
    }

    // Key = uuid.toString()
    private static Map<String, NickEntry> nicks = new HashMap<>();

    public static void load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            nicks = new HashMap<>();
            return;
        }

        try (Reader r = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, NickEntry>>() {}.getType();
            Map<String, NickEntry> loaded = GSON.fromJson(r, type);
            nicks = loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            nicks = new HashMap<>();
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(getConfigFile())) {
            GSON.toJson(nicks, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static NickEntry get(UUID uuid) {
        return nicks.get(uuid.toString());
    }

    public static void set(UUID uuid, NickEntry entry) {
        nicks.put(uuid.toString(), entry);
        save();
    }

    public static void remove(UUID uuid) {
        nicks.remove(uuid.toString());
        save();
    }

    public static Map<String, NickEntry> getAll() {
        return Collections.unmodifiableMap(nicks);
    }

    /**
     * Updates stored username when we see the player again (name change).
     * Does NOT create entries automatically; only updates existing ones.
     */
    public static void updateUsernameIfChanged(UUID uuid, String currentName) {
        if (currentName == null || currentName.isBlank()) return;

        NickEntry entry = get(uuid);
        if (entry == null) return;

        if (!Objects.equals(entry.username, currentName)) {
            entry.username = currentName;
            // Write once when changed (name changes are rare)
            set(uuid, entry);
        }
    }

    /**
     * Switches the storage mode and reloads nicknames from the new location.
     * The old file is NOT deleted – both files coexist independently.
     */
    public static void switchMode(StorageConfig.StorageMode newMode) {
        StorageConfig.setMode(newMode);
        load();
    }
}
