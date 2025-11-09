package de.michiruf.serverportals.mixin;

import de.michiruf.serverportals.api.EntityPortalTracking;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Track which portal teleported this entity to prevent infinite loops
 */
@Mixin(Entity.class)
public abstract class EntityPortalTrackingMixin implements EntityPortalTracking {
    @Unique
    private String lastPortalName = null;
    
    @Unique
    private long lastPortalTime = 0;
    
    @Override
    public String serverportals$getLastPortalName() {
        return lastPortalName;
    }
    
    @Override
    public void serverportals$setLastPortalName(String portalName) {
        this.lastPortalName = portalName;
        this.lastPortalTime = System.currentTimeMillis();
    }
    
    @Override
    public boolean serverportals$isRecentlyTeleportedFromPortal(String portalName) {
        // Grace period: 3 seconds to prevent loops
        return portalName.equals(lastPortalName) && (System.currentTimeMillis() - lastPortalTime) < 3000;
    }
    
    @Override
    public void serverportals$clearPortalTracking() {
        this.lastPortalName = null;
        this.lastPortalTime = 0;
    }
}
