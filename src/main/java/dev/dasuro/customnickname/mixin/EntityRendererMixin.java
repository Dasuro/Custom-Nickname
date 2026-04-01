package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
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

        // Auto-update the stored username if the player has renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // 26.1.1 stores nametag text on EntityRenderState#nameTag.
        state.nameTag = player.getDisplayName();
    }
}