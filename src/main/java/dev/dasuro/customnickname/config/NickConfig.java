package dev.dasuro.customnickname.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class NickConfig {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_FILE =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("friendnicks.json")
                    .toFile();

    // Key = uuid.toString()
    private static Map<String, NickEntry> nicks = new HashMap<>();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            nicks = new HashMap<>();
            return;
        }

        try (Reader r = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, NickEntry>>() {}.getType();
            Map<String, NickEntry> loaded = GSON.fromJson(r, type);
            nicks = loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            nicks = new HashMap<>();
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_FILE)) {
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
}
