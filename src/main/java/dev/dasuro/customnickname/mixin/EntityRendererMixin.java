package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerEntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void customnickname$updateRenderState(
            PlayerLikeEntity entity,
            PlayerEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        UUID uuid = player.getUuid();
        String currentName = player.getGameProfile().name();

        // Auto-update the stored username if the player has renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        Team team = player.getScoreboardTeam();

        // Determine the base color: use Team.decorateName() which provides
        // the server's intended styling (team color). This is the same color
        // that Vanilla would show for this player.
        Text base;
        if (team != null) {
            base = Team.decorateName(team, Text.literal(currentName != null ? currentName : ""));
        } else {
            base = Text.literal(currentName != null ? currentName : "");
        }
        MutableText nickComponent = ColorParser.buildNick(nick, base);
        MutableText full = Text.empty();

        if (team != null && nick.showPrefix) full.append(team.getPrefix());
        full.append(nickComponent);
        if (team != null && nick.showSuffix) full.append(team.getSuffix());

        if (StorageConfig.isShowIndicator()) {
            full.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
        }

        // Hide the default nametag by clearing playerName
        state.playerName = null;
        // Set displayName to our custom nick so that other mods (e.g. LabyMod)
        // still detect an active nametag and render their addon icons
        // (VoiceChat, Party, etc.) above it.
        state.displayName = full;
    }
}