package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AvatarRenderer.class)
public class EntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void customnickname$updateRenderState(
            Avatar entity,
            AvatarRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();
        String currentName = player.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        PlayerTeam team = player.getTeam();

        MutableComponent originalNameTag = state.nameTag != null
                ? state.nameTag.copy()
                : Component.literal(currentName);

        state.nameTag = NickDisplayBuilder.replaceInOriginalOrFallback(originalNameTag, currentName, nick, team, false, true);
    }
}