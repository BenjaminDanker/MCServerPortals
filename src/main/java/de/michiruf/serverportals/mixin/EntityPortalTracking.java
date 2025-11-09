package de.michiruf.serverportals.mixin;

/**
 * Interface to access portal tracking methods added by EntityPortalTrackingMixin
 */
public interface EntityPortalTracking {
    String serverportals$getLastPortalName();
    
    void serverportals$setLastPortalName(String portalName);
    
    boolean serverportals$isRecentlyTeleportedFromPortal(String portalName);
    
    void serverportals$clearPortalTracking();
}
