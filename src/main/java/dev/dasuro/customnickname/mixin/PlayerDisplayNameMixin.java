package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Hooks into PlayerEntity.getDisplayName() and PlayerEntity.getName() so that
 * ANY code path (including Entity Culling's separate nametag rendering for
 * culled entities) receives the custom nickname instead of the vanilla name.
 */
@Mixin(Player.class)
public class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        UUID uuid = self.getUUID();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        MutableComponent originalResult = cir.getReturnValue() != null
                ? cir.getReturnValue().copy() : null;
        PlayerTeam team = self.getTeam();
        MutableComponent result = NickDisplayBuilder.replaceInOriginalOrFallback(
                originalResult, currentName, nick, team, false, true);

        cir.setReturnValue(result);
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        UUID uuid = self.getUUID();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // Some nametag render paths use getName() directly
        MutableComponent originalResult = cir.getReturnValue() != null
                ? cir.getReturnValue().copy() : null;
        PlayerTeam team = self.getTeam();
        MutableComponent result = NickDisplayBuilder.replaceInOriginalOrFallback(
                originalResult, currentName, nick, team, false, true);

        cir.setReturnValue(result);
    }
}
