package dev.dasuro.customnickname.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MojangLookup {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private MojangLookup() {}

    public record ResolvedProfile(UUID uuid, String name) {}

    public static CompletableFuture<ResolvedProfile> resolveByName(
            String name
    ) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        URI uri = URI.create(
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/" +
                        encoded
        );

        HttpRequest req = HttpRequest.newBuilder(uri).GET().build();

        return CLIENT
                .sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return null;

                    JsonObject obj =
                            JsonParser.parseString(resp.body()).getAsJsonObject();

                    String id = obj.get("id").getAsString();
                    String fixedName = obj.get("name").getAsString();

                    return new ResolvedProfile(parseUuid(id), fixedName);
                })
                .exceptionally(e -> null);
    }

    public static CompletableFuture<String> resolveNameByUuid(UUID uuid) {
        String noDashes = uuid.toString().replace("-", "");
        URI uri = URI.create(
                "https://sessionserver.mojang.com/session/minecraft/profile/" +
                        noDashes
        );

        HttpRequest req = HttpRequest.newBuilder(uri).GET().build();

        return CLIENT
                .sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return null;

                    JsonObject obj =
                            JsonParser.parseString(resp.body()).getAsJsonObject();
                    return obj.get("name").getAsString();
                })
                .exceptionally(e -> null);
    }

    private static UUID parseUuid(String id) {
        // id may come with or without dashes
        if (id.contains("-")) return UUID.fromString(id);

        String s =
                id.substring(0, 8) +
                        "-" +
                        id.substring(8, 12) +
                        "-" +
                        id.substring(12, 16) +
                        "-" +
                        id.substring(16, 20) +
                        "-" +
                        id.substring(20);

        return UUID.fromString(s);
    }
}
