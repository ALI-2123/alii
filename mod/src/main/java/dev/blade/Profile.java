package dev.blade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Reads config/blade/profile.json (written by the launcher) and keeps the player's cosmetic choices. */
public final class Profile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("blade");
    private static final Cosmetics.Set COSMETICS = new Cosmetics.Set();

    public static String serverUrl = "";
    public static String serverName = "";
    public static String serverToken = "";

    private Profile() {}

    public static Cosmetics.Set cosmetics() {
        return COSMETICS;
    }

    static JsonObject readObject(Path file) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    static void writeJson(Path file, JsonObject json) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(file, GSON.toJson(json));
        } catch (IOException e) {
            BladeClient.LOGGER.warn("Couldn't write {}", file, e);
        }
    }

    private static String pick(JsonObject json, String key, String[] allowed) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            String value = json.get(key).getAsString();
            if (Arrays.asList(allowed).contains(value)) return value;
        }
        return "none";
    }

    private static void apply(JsonObject c) {
        COSMETICS.aura = pick(c, "aura", Cosmetics.AURA);
        COSMETICS.wings = pick(c, "wings", Cosmetics.WINGS);
        COSMETICS.blade = pick(c, "blade", Cosmetics.BLADE);
        COSMETICS.trail = pick(c, "trail", Cosmetics.TRAIL);
    }

    public static void load() {
        JsonObject root = readObject(DIR.resolve("profile.json"));
        if (root.has("cosmetics") && root.get("cosmetics").isJsonObject()) apply(root.getAsJsonObject("cosmetics"));
        // Choices made in game are kept here and win over the launcher's starting look.
        Path saved = DIR.resolve("cosmetics.json");
        if (Files.exists(saved)) apply(readObject(saved));
        if (root.has("server") && root.get("server").isJsonObject()) {
            JsonObject server = root.getAsJsonObject("server");
            serverUrl = server.has("url") ? server.get("url").getAsString() : "";
            serverName = server.has("name") ? server.get("name").getAsString() : "";
            serverToken = server.has("token") ? server.get("token").getAsString() : "";
        }
    }

    public static void saveCosmetics() {
        JsonObject json = new JsonObject();
        json.addProperty("aura", COSMETICS.aura);
        json.addProperty("wings", COSMETICS.wings);
        json.addProperty("blade", COSMETICS.blade);
        json.addProperty("trail", COSMETICS.trail);
        writeJson(DIR.resolve("cosmetics.json"), json);
        Social.get().postCosmetics();
    }

    static Path dir() {
        return DIR;
    }
}
