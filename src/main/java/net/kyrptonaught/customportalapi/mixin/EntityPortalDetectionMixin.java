package net.kyrptonaught.customportalapi.mixin;

import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPortalDetectionMixin {
    @Shadow
    private Level level;

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow
    public abstract boolean canUsePortal(boolean ignoreCreative);

    @Inject(method = "tick", at = @At("HEAD"))
    private void checkCustomPortalCollision(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        // Only check server-side players
        if (!(entity instanceof ServerPlayer)) return;
        if (!(this.level instanceof ServerLevel serverWorld)) return;
        
        BlockPos pos = this.blockPosition();
        BlockState state = serverWorld.getBlockState(pos);
        
        // Only proceed if standing on custom portal and can use portals
        if (state.getBlock() instanceof CustomPortalBlock && this.canUsePortal(false)) {
            entity.setAsInsidePortal((CustomPortalBlock) state.getBlock(), pos);
        }
    }
}
