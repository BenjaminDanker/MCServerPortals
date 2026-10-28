package de.michiruf.serverportals.mixin;

import de.michiruf.serverportals.ServerPortalsMod;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player arriving through a portal is created at the arrival spot: their saved position, dimension and facing are
 * replaced while the server reads them, so it prepares the arrival chunks and spawns them there, instead of spawning
 * them where they last stood and teleporting them after they joined.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListArrivalMixin {
    @Inject(method = "loadPlayerData", at = @At("RETURN"), cancellable = true)
    private void serverportals$placeAtArrival(NameAndId player, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        Optional<CompoundTag> saved = cir.getReturnValue();
        if (saved == null || saved.isEmpty()) {
            return; // first visit to this server: the arrival teleport after joining handles it
        }
        CompoundTag placed = ServerPortalsMod.placeAtPendingArrival(((PlayerList) (Object) this).getServer(), player.id(), saved.get());
        if (placed != null) {
            cir.setReturnValue(Optional.of(placed));
        }
    }
}
