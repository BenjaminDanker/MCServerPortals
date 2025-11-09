package de.michiruf.serverportals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder;
import net.kyrptonaught.customportalapi.util.SHOULDTP;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.michiruf.serverportals.config.PortalRegistrationData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Michael Ruf
 * @since 2022-11-29
 */
public class ServerPortalsMod implements DedicatedServerModInitializer, ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ServerPortals");
    public static final de.michiruf.serverportals.config.Config CONFIG = de.michiruf.serverportals.config.Config.createAndLoad();
    public static final Identifier PORTAL_HANDOFF_CHANNEL = Identifier.of("serverportals", "portal_handoff");
    
    // Store pending portal teleports: UUID -> (portal name, timestamp)
    private static final Map<UUID, PendingTeleport> pendingPortalTeleports = new ConcurrentHashMap<>();
    
    // Store player join times for immunity period
    private static final Map<UUID, Long> playerJoinTimes = new ConcurrentHashMap<>();
    
    // Portal detection immunity period after join (milliseconds)
    private static final long PORTAL_IMMUNITY_AFTER_JOIN_MS = 2000;
    
    private record PendingTeleport(String portalName, long timestampMs) {}
    
    // Maximum age for pending teleports (5 seconds)
    private static final long PENDING_TELEPORT_MAX_AGE_MS = 5000;

    @Override
    public void onInitializeServer() {
        LOGGER.info("ServerPortals initializing on server");
        registerPlayerJoinListener();
        registerLoginHandshake();
        registerPluginHooks();
        LOGGER.info("ServerPortals server initialization complete");
    }
    
    /**
     * Store a pending portal teleport for a player.
     * This will be consumed when the player joins the server.
     */
    public static void setPendingPortalTeleport(UUID playerId, String portalName) {
        LOGGER.info("Setting pending portal teleport for player {} to portal {}", playerId, portalName);
        pendingPortalTeleports.put(playerId, new PendingTeleport(portalName, System.currentTimeMillis()));
    }
    
    /**
     * Check if a player has a pending portal teleport (without consuming it).
     * Returns true if there is a valid (not expired) pending teleport.
     */
    public static boolean hasPendingPortalTeleport(UUID playerId) {
        PendingTeleport pending = pendingPortalTeleports.get(playerId);
        if (pending != null) {
            long ageMs = System.currentTimeMillis() - pending.timestampMs();
            if (ageMs > PENDING_TELEPORT_MAX_AGE_MS) {
                // Clean up expired entry
                pendingPortalTeleports.remove(playerId);
                return false;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Get and clear a pending portal teleport for a player.
     * Returns null if not set or if too old (expired).
     */
    public static String consumePendingPortalTeleport(UUID playerId) {
        PendingTeleport pending = pendingPortalTeleports.remove(playerId);
        if (pending != null) {
            long ageMs = System.currentTimeMillis() - pending.timestampMs();
            if (ageMs > PENDING_TELEPORT_MAX_AGE_MS) {
                LOGGER.warn("Pending portal teleport for player {} to portal {} is too old ({}ms), ignoring", 
                           playerId, pending.portalName(), ageMs);
                return null;
            }
            LOGGER.info("Consumed pending portal teleport for player {} to portal {} (age: {}ms)", 
                       playerId, pending.portalName(), ageMs);
            return pending.portalName();
        }
        return null;
    }
    
    /**
     * Teleport a player to the destination coordinates of a portal.
     */
    private static void teleportToPortal(ServerPlayerEntity player, String portalName) {
        LOGGER.info("Player {} has pending portal teleport to {}", player.getName().getString(), portalName);
        
        // Find portal and teleport
        if (CONFIG.portals() != null) {
            for (de.michiruf.serverportals.config.PortalRegistrationData portal : CONFIG.portals()) {
                if (portalName.equals(portal.destinationPortalName())) {
                    if (portal.arrivalLocation() != null) {
                        LOGGER.info("Teleporting player {} to portal {} at ({}, {}, {})",
                                player.getName().getString(),
                                portal.index(),
                                portal.arrivalLocation().x(),
                                portal.arrivalLocation().y(),
                                portal.arrivalLocation().z());
                        
            player.setPosition(
                portal.arrivalLocation().x(),
                portal.arrivalLocation().y(),
                portal.arrivalLocation().z()
            );
            player.networkHandler.requestTeleport(
                portal.arrivalLocation().x(),
                portal.arrivalLocation().y(),
                portal.arrivalLocation().z(),
                player.getYaw(),
                player.getPitch()
            );

            if (player instanceof de.michiruf.serverportals.api.EntityPortalTracking tracking) {
                tracking.serverportals$clearPortalTracking();
                LOGGER.info("Cleared portal tracking for player {} after login teleport",
                    player.getName().getString());
            }
                        return;
                    }
                }
            }
        }
        LOGGER.warn("No matching portal found for pending teleport: {}", portalName);
    }

    private void registerLoginHandshake() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, sync) -> {
            try {
                sender.sendPacket(PORTAL_HANDOFF_CHANNEL, PacketByteBufs.empty());
                LOGGER.debug("Sent portal handoff query");
            } catch (Exception ex) {
                LOGGER.warn("Failed to send portal handoff query", ex);
            }
        });

        ServerLoginNetworking.registerGlobalReceiver(PORTAL_HANDOFF_CHANNEL,
                (server, handler, understood, buf, synchronizer, responseSender) -> {
                    if (!understood) {
                        LOGGER.debug("Connection did not understand portal handoff query");
                        return;
                    }

                    try {
                        boolean hasPortal = buf.readBoolean();
                        if (!hasPortal) {
                            LOGGER.debug("Portal handoff response contained no portal");
                            return;
                        }

                        String portalName = buf.readString(32767);
                        UUID playerId = buf.readUuid();
                        setPendingPortalTeleport(playerId, portalName);
                        LOGGER.info("Stored portal handoff '{}' for {}", portalName, playerId);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to process portal handoff payload", ex);
                    }
                });

        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            String portalName = consumePendingPortalTeleport(player.getUuid());
            if (portalName != null) {
                LOGGER.info("Applying portal handoff for player {} before spawn", player.getName().getString());
                teleportToPortal(player, portalName);
            }
        });
    }
    
    /**
     * Schedule a delayed check for pending portal teleports.
     */
    private static void scheduleDelayedTeleportCheck(net.minecraft.server.MinecraftServer server, 
                                                       ServerPlayerEntity player, 
                                                       long delayMs, 
                                                       int attemptNumber) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                String delayedPortalName = consumePendingPortalTeleport(player.getUuid());
                if (delayedPortalName != null) {
                    LOGGER.info("Found delayed pending portal teleport for {} (attempt {} after {}ms)", 
                               player.getName().getString(), attemptNumber, delayMs);
                    server.execute(() -> teleportToPortal(player, delayedPortalName));
                } else {
                    LOGGER.debug("No pending portal teleport found for {} on attempt {} after {}ms", 
                                player.getName().getString(), attemptNumber, delayMs);
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for delayed portal teleport check (attempt {})", attemptNumber, e);
            }
        }).start();
    }

    /**
     * Register listener for player join events to track immunity period.
     */
    private void registerPlayerJoinListener() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID playerId = handler.getPlayer().getUuid();
            playerJoinTimes.put(playerId, System.currentTimeMillis());
            LOGGER.info("Player {} joined, portal immunity active for {}ms", 
                       handler.getPlayer().getName().getString(), PORTAL_IMMUNITY_AFTER_JOIN_MS);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUuid();
            playerJoinTimes.remove(playerId);
            LOGGER.debug("Removed join time tracking for disconnected player {}", 
                        handler.getPlayer().getName().getString());
        });
    }
    
    /**
     * Check if a player is within the immunity period after joining.
     */
    public static boolean isWithinJoinImmunityPeriod(UUID playerId) {
        Long joinTime = playerJoinTimes.get(playerId);
        if (joinTime != null) {
            long timeSinceJoin = System.currentTimeMillis() - joinTime;
            return timeSinceJoin < PORTAL_IMMUNITY_AFTER_JOIN_MS;
        }
        return false;
    }
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("ServerPortals initializing on client");
        // Another variant:
        //switch (FabricLauncherBase.getLauncher().getEnvironmentType()) {

        // Register everything delayed until a world got loaded
        //ServerWorldEvents.LOAD.register((server, world) -> registerPluginHooks());
    }

    private void registerPluginHooks() {
        try {
            CommandRegistrationCallback.EVENT.register(Command::registerCommands);
            
            if (CONFIG.portals() != null) {
                registerPortals();
            }
            LOGGER.info("ServerPortals registered {} portals", CONFIG.portals() == null ? 0 : CONFIG.portals().size());
        } catch (Exception e) {
            LOGGER.error("Error during plugin hook registration", e);
        }
    }
    
    private void registerPortals() {
        for (de.michiruf.serverportals.config.PortalRegistrationData portal : ServerPortalsMod.CONFIG.portals()) {
            try {
                CustomPortalBuilder builder = CustomPortalBuilder.beginPortal();
                
                // Set both destination and return dimension to nether to make portal activatable
                builder.destDimID(Identifier.of("minecraft", "the_nether"));
                builder.returnDim(Identifier.of("minecraft", "overworld"), false);
                
                builder.frameBlock(portal.frameBlock());
                builder.lightWithItem(portal.lightWithItem());
                builder.tintColor(portal.color());
                
                // Register the event BEFORE registerPortal
                builder.registerBeforeTPEvent(entity -> {
                    LOGGER.info("Portal triggered: {} by entity: {}", portal.index(), entity.getName().getString());
                    
                    // Check if player is within join immunity period
                    if (entity instanceof ServerPlayerEntity player) {
                        if (isWithinJoinImmunityPeriod(player.getUuid())) {
                            LOGGER.info("Player {} is within join immunity period, skipping portal detection", 
                                       player.getName().getString());
                            return SHOULDTP.CANCEL_TP;
                        }
                        
                        // Check if player has pending portal teleport - if so, skip portal detection
                        if (hasPendingPortalTeleport(player.getUuid())) {
                            LOGGER.info("Player {} has pending portal teleport, skipping portal detection", 
                                       player.getName().getString());
                            return SHOULDTP.CANCEL_TP;
                        }
                    }
                    
                    // Check if player was just teleported from the destination portal (loop prevention)
                    if (portal.destinationPortalName() != null) {
                        var portalTracking = (de.michiruf.serverportals.api.EntityPortalTracking) entity;
                        if (portalTracking.serverportals$isRecentlyTeleportedFromPortal(portal.destinationPortalName())) {
                            LOGGER.info("Preventing infinite loop: entity was just teleported from {}", portal.destinationPortalName());
                            return SHOULDTP.CANCEL_TP;
                        }
                    }
                    
                    // Mark this entity as teleported from this portal
                    if (portal.index() != null) {
                        var portalTracking = (de.michiruf.serverportals.api.EntityPortalTracking) entity;
                        portalTracking.serverportals$setLastPortalName(portal.index());
                        LOGGER.info("Marked entity as teleported from portal: {}", portal.index());
                    }
                    
                    LOGGER.info("Executing portal command for {}: {}", portal.index(), portal.command());
                    executeCommand(entity, portal);
                    return SHOULDTP.CANCEL_TP;  // Cancel the teleport since Velocity handles it
                });
                
                // Now register the portal
                builder.registerPortal();
            } catch (Exception e) {
                LOGGER.error("Failed to register portal: {}", portal.index(), e);
            }
        }
    }

    private void executeCommand(Entity entity, PortalRegistrationData portal) {
        try {
            // Check if entity is a ServerPlayerEntity
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                var server = entity.getEntityWorld().getServer();
                if (server == null) {
                    LOGGER.error("Server is null");
                    return;
                }
                
                // Append source portal name inside the quotes if available
                String command = portal.command();
                if (portal.index() != null && command.contains("\"")) {
                    // Find last quote and insert portal name before it
                    int lastQuoteIndex = command.lastIndexOf("\"");
                    if (lastQuoteIndex > 0) {
                        command = command.substring(0, lastQuoteIndex) + " " + portal.index() + command.substring(lastQuoteIndex);
                        LOGGER.info("Appending source portal name '{}' inside command quotes", portal.index());
                    }
                }
                
                var commandWithSlash = command.startsWith("/") ? command : "/" + command;
                LOGGER.info("Portal {} sending player {} to server: {}", portal.index(), player.getName().getString(), 
                           portal.destinationServer() != null ? portal.destinationServer() : "unknown");
                
                // Execute the command on the server's main thread to ensure proper context
                server.execute(() -> {
                    try {
                        LOGGER.info("Executing command on main thread: {}", commandWithSlash);
                        server.getCommandManager().executeWithPrefix(player.getCommandSource(), commandWithSlash);
                        LOGGER.info("Command execution completed for portal {}", portal.index());
                    } catch (Exception e) {
                        LOGGER.error("Error executing portal command: {}", portal.command(), e);
                    }
                });
            } else {
                LOGGER.warn("Entity is not a ServerPlayerEntity, cannot execute command");
            }
        } catch (Exception e) {
            LOGGER.error("Fatal error in executeCommand", e);
        }
    }
}
