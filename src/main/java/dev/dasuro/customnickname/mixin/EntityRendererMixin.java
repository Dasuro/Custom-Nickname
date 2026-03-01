package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
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
    private void friendnicks$updateRenderState(
            PlayerLikeEntity entity,
            PlayerEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        UUID uuid = player.getUuid();
        String currentName = player.getGameProfile().name();

        // Auto-update stored username if the player renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // Basis: nur der echte Name als Text, Stil holen wir aus dem aktuellen state oder Original
        Text base = Text.literal(currentName);
        MutableText nickComponent = ColorParser.buildNick(nick, base);

        Team team = player.getScoreboardTeam();
        MutableText full = Text.empty();

        if (team != null && nick.showPrefix) full.append(team.getPrefix());
        full.append(nickComponent);
        if (team != null && nick.showSuffix) full.append(team.getSuffix());

        // Ersetze den Kopf-Nametag vollständig durch unseren Nick
        state.playerName = full;
        // displayName auf null setzen, damit der Name nicht doppelt gerendert wird
        state.displayName = null;
    }
}