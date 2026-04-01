package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
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
@Mixin(PlayerEntity.class)
public class PlayerEntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        Text originalResult = cir.getReturnValue();
        Team team = self.getScoreboardTeam();
        cir.setReturnValue(NickDisplayBuilder.replaceInOriginalOrFallback(originalResult, currentName, nick, team));
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // getName() wird von manchen Mods anschließend noch mit Team.decorateName verarbeitet.
        // Daher hier bewusst ohne Team-Prefix/Suffix UND ohne Indikator zurückgeben,
        // damit der Chat den Indikator nicht mitten im Namen bekommt.
        Text originalResult = cir.getReturnValue();
        Team team = self.getScoreboardTeam();
        Text baseName = NickDisplayBuilder.buildStyledBaseName(currentName, originalResult, team);
        MutableText nickComponent = ColorParser.buildNick(nick, baseName);

        cir.setReturnValue(nickComponent);
    }
}
