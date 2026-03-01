package dev.dasuro.customnickname;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.gui.CustomNickConfigScreen;
import dev.dasuro.customnickname.util.MojangLookup;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Customnickname implements ClientModInitializer {
    public static final String MOD_ID = "customnick";

    // Keybind: N opens the Custom Nickname menu (can be rebound in Controls)
    private static KeyBinding OPEN_MENU_KEY;

    @Override
    public void onInitializeClient() {
        NickConfig.load();

        // Refresh stored usernames (detect renames) on startup
        refreshAllKnownUsernamesAsync();

        // Register keybind
        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.customnickname.open_menu",
                        GLFW.GLFW_KEY_N,
                        KeyBinding.Category.MISC
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_MENU_KEY == null) return;

            while (OPEN_MENU_KEY.wasPressed()) {
                openConfigScreen(client);
            }
        });

        // Register /cnick commands
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) ->
                        dispatcher.register(
                                ClientCommandManager.literal("cnick")
                                        .then(
                                                ClientCommandManager.literal("add")
                                                        .then(
                                                                ClientCommandManager.argument(
                                                                                "player",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .then(
                                                                                ClientCommandManager.argument(
                                                                                                "nick",
                                                                                                StringArgumentType.greedyString()
                                                                                        )
                                                                                        .executes(ctx -> {
                                                                                            String playerName =
                                                                                                    StringArgumentType.getString(
                                                                                                            ctx,
                                                                                                            "player"
                                                                                                    );
                                                                                            String nick =
                                                                                                    StringArgumentType.getString(
                                                                                                            ctx,
                                                                                                            "nick"
                                                                                                    );

                                                                                            // 1) Prefer online tab list (instant UUID)
                                                                                            UUID onlineUuid =
                                                                                                    findOnlineUuid(
                                                                                                            playerName
                                                                                                    );
                                                                                            if (onlineUuid != null) {
                                                                                                NickEntry entry =
                                                                                                        NickConfig.get(
                                                                                                                onlineUuid
                                                                                                        );
                                                                                                if (entry == null) {
                                                                                                    entry =
                                                                                                            new NickEntry();
                                                                                                }

                                                                                                entry.username =
                                                                                                        findOnlineExactName(
                                                                                                                playerName
                                                                                                        );
                                                                                                entry.nickname = nick;

                                                                                                NickConfig.set(
                                                                                                        onlineUuid,
                                                                                                        entry
                                                                                                );

                                                                                                ctx.getSource()
                                                                                                        .sendFeedback(
                                                                                                                Text.literal(
                                                                                                                        "Custom Nickname: set nickname for " +
                                                                                                                                entry.username +
                                                                                                                                "."
                                                                                                                )
                                                                                                        );
                                                                                                return 1;
                                                                                            }

                                                                                            // 2) Otherwise resolve UUID via Mojang services
                                                                                            ctx.getSource().sendFeedback(
                                                                                                    Text.literal(
                                                                                                            "Custom Nickname: looking up UUID for " +
                                                                                                                    playerName +
                                                                                                                    "..."
                                                                                                    )
                                                                                            );

                                                                                            MojangLookup.resolveByName(
                                                                                                            playerName
                                                                                                    )
                                                                                                    .thenAccept(profile -> {
                                                                                                        MinecraftClient
                                                                                                                .getInstance()
                                                                                                                .execute(() -> {
                                                                                                                    if (profile ==
                                                                                                                            null) {
                                                                                                                        sendClientChat(
                                                                                                                                "Custom Nickname: could not resolve UUID for " +
                                                                                                                                        playerName +
                                                                                                                                        "."
                                                                                                                        );
                                                                                                                        return;
                                                                                                                    }

                                                                                                                    NickEntry entry =
                                                                                                                            NickConfig.get(
                                                                                                                                    profile.uuid()
                                                                                                                            );
                                                                                                                    if (entry ==
                                                                                                                            null) {
                                                                                                                        entry =
                                                                                                                                new NickEntry();
                                                                                                                    }

                                                                                                                    // cache UUID + update username to current official name
                                                                                                                    entry.username =
                                                                                                                            profile.name();
                                                                                                                    entry.nickname =
                                                                                                                            nick;

                                                                                                                    NickConfig.set(
                                                                                                                            profile.uuid(),
                                                                                                                            entry
                                                                                                                    );

                                                                                                                    sendClientChat(
                                                                                                                            "Custom Nickname: set nickname for " +
                                                                                                                                    profile.name() +
                                                                                                                                    "."
                                                                                                                    );
                                                                                                                });
                                                                                                    });

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                                        .then(
                                                ClientCommandManager.literal("remove")
                                                        .then(
                                                                ClientCommandManager.argument(
                                                                                "player",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes(ctx -> {
                                                                            String playerName =
                                                                                    StringArgumentType.getString(
                                                                                            ctx,
                                                                                            "player"
                                                                                    );

                                                                            // Prefer online removal (fast)
                                                                            UUID onlineUuid =
                                                                                    findOnlineUuid(playerName);
                                                                            if (onlineUuid != null) {
                                                                                NickConfig.remove(onlineUuid);
                                                                                ctx.getSource().sendFeedback(
                                                                                        Text.literal(
                                                                                                "Custom Nickname: removed entry for " +
                                                                                                        findOnlineExactName(
                                                                                                                playerName
                                                                                                        ) +
                                                                                                        "."
                                                                                        )
                                                                                );
                                                                                return 1;
                                                                            }

                                                                            // Otherwise try Mojang lookup
                                                                            ctx.getSource().sendFeedback(
                                                                                    Text.literal(
                                                                                            "Custom Nickname: looking up UUID for " +
                                                                                                    playerName +
                                                                                                    "..."
                                                                                    )
                                                                            );

                                                                            MojangLookup.resolveByName(
                                                                                            playerName
                                                                                    )
                                                                                    .thenAccept(profile -> {
                                                                                        MinecraftClient.getInstance()
                                                                                                .execute(() -> {
                                                                                                    if (profile ==
                                                                                                            null) {
                                                                                                        sendClientChat(
                                                                                                                "Custom Nickname: could not resolve UUID for " +
                                                                                                                        playerName +
                                                                                                                        "."
                                                                                                        );
                                                                                                        return;
                                                                                                    }

                                                                                                    NickConfig.remove(
                                                                                                            profile.uuid()
                                                                                                    );
                                                                                                    sendClientChat(
                                                                                                            "Custom Nickname: removed entry for " +
                                                                                                                    profile.name() +
                                                                                                                    "."
                                                                                                    );
                                                                                                });
                                                                                    });

                                                                            return 1;
                                                                        })
                                                        )
                                        )
                                        .then(
                                                ClientCommandManager.literal("list")
                                                        .executes(ctx -> {
                                                            Map<String, NickEntry> all =
                                                                    NickConfig.getAll();
                                                            if (all.isEmpty()) {
                                                                ctx.getSource().sendFeedback(
                                                                        Text.literal(
                                                                                "Custom Nickname: no entries."
                                                                        )
                                                                );
                                                                return 1;
                                                            }

                                                            ctx.getSource().sendFeedback(
                                                                    Text.literal("Custom Nickname entries:")
                                                            );

                                                            for (Map.Entry<String, NickEntry> e :
                                                                    all.entrySet()) {
                                                                String uuidStr = e.getKey();
                                                                NickEntry ne = e.getValue();
                                                                String name =
                                                                        ne.username != null
                                                                                ? ne.username
                                                                                : "?";
                                                                String nick =
                                                                        ne.nickname != null
                                                                                ? ne.nickname
                                                                                : "";

                                                                ctx.getSource().sendFeedback(
                                                                        Text.literal(
                                                                                " - " +
                                                                                        name +
                                                                                        " (" +
                                                                                        uuidStr +
                                                                                        ") -> " +
                                                                                        nick
                                                                        )
                                                                );
                                                            }
                                                            return 1;
                                                        })
                                        )
                        )
        );
    }

    private static void openConfigScreen(MinecraftClient client) {
        if (client == null) return;
        if (client.getWindow() == null) return;

        // Don't open a new instance if it's already open
        if (client.currentScreen instanceof CustomNickConfigScreen) return;

        client.setScreen(new CustomNickConfigScreen(client.currentScreen));
    }

    private static UUID findOnlineUuid(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;

        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) {
                return e.getProfile().id();
            }
        }
        return null;
    }

    private static String findOnlineExactName(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return name;

        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) {
                return n;
            }
        }
        return name;
    }

    private static void refreshAllKnownUsernamesAsync() {
        // Throttle to avoid rate limits
        List<UUID> uuids = new ArrayList<>();
        for (String uuidStr : NickConfig.getAll().keySet()) {
            try {
                uuids.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid keys
            }
        }

        CompletableFuture.runAsync(() -> {
            for (UUID uuid : uuids) {
                try {
                    String newName =
                            MojangLookup.resolveNameByUuid(uuid).join();
                    if (newName != null && !newName.isBlank()) {
                        NickConfig.updateUsernameIfChanged(uuid, newName);
                    }

                    Thread.sleep(250);
                } catch (Exception ignored) {
                    // Ignore network errors
                }
            }
        });
    }

    private static void sendClientChat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.sendMessage(Text.literal(msg), false);
    }
}
