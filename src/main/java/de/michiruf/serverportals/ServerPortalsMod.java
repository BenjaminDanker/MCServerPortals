package de.michiruf.serverportals;

import de.michiruf.serverportals.config.Config;
import de.michiruf.serverportals.config.PortalRegistrationData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder;
import net.kyrptonaught.customportalapi.util.SHOULDTP;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.world.TeleportTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.silver.wakeuplobby.portal.PortalRequestPayloadCodec;
import com.silver.wakeuplobby.portal.PortalRequestPayload;
import com.silver.wakeuplobby.portal.PortalRequestSigner;

/**
 * @author Michael Ruf
 * @since 2022-11-29
 */
public class ServerPortalsMod implements DedicatedServerModInitializer, ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ServerPortals");
    public static final Config CONFIG = Config.createAndLoad();
    public static final Identifier PORTAL_HANDOFF_CHANNEL = Identifier.of("serverportals", "portal_handoff");
    
    // Store pending portal teleports: UUID -> (portal name, timestamp)
    private static final Map<UUID, PendingTeleport> pendingPortalTeleports = new ConcurrentHashMap<>();
    
    // Store player join times for immunity period
    private static final Map<UUID, Long> playerJoinTimes = new ConcurrentHashMap<>();

    // Track recent handoff teleports to suppress duplicate receive-portal commands
    private static final Map<UUID, Long> recentHandoffTeleports = new ConcurrentHashMap<>();
    private static final long HANDOFF_RECEIVE_SUPPRESSION_MS = 5_000;

    // Queue login handoff teleports to fire after the client finishes its initial chunk sync
    private static final Map<UUID, LoginHandoff> loginHandoffQueue = new ConcurrentHashMap<>();
    private static final int LOGIN_HANDOFF_DELAY_TICKS = 8;
    
    // Portal detection immunity period after join (milliseconds)
    private static final long PORTAL_IMMUNITY_AFTER_JOIN_MS = 2000;
    
    private record PendingTeleport(String portalName, long timestampMs) {}
    private record LoginHandoff(String portalName, int ticksRemaining) {}
    
    // Maximum age for pending teleports (5 seconds)
    private static final long PENDING_TELEPORT_MAX_AGE_MS = 5000;

    @Override
    public void onInitializeServer() {
        LOGGER.info("ServerPortals initializing on server");

        try {
            PayloadTypeRegistry.playS2C().register(PortalRequestPayload.PACKET_ID, PortalRequestPayload.codec);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("Portal request payload type already registered; skipping duplicate registration");
        }

        registerPlayerJoinListener();
        registerLoginHandshake();
        registerLoginHandoffTicker();
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

    private static void markRecentHandoffTeleport(UUID playerId) {
        if (playerId != null) {
            recentHandoffTeleports.put(playerId, System.currentTimeMillis());
        }
    }

    public static boolean shouldSkipReceivePortal(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long timestamp = recentHandoffTeleports.get(playerId);
        if (timestamp == null) {
            return false;
        }
        long age = System.currentTimeMillis() - timestamp;
        if (age <= HANDOFF_RECEIVE_SUPPRESSION_MS) {
            LOGGER.info("Suppressing receive-portal for {} - recent handoff teleport {}ms ago", playerId, age);
            recentHandoffTeleports.remove(playerId);
            return true;
        }
        recentHandoffTeleports.remove(playerId);
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
        if (CONFIG.portals() != null) {
            LOGGER.info("Searching for portal named '{}' among {} configured portals", portalName, CONFIG.portals().size());
            for (PortalRegistrationData portal : CONFIG.portals()) {
                LOGGER.info("  Portal: index='{}' destinationPortalName='{}' destinationServer='{}' arrivalLoc={}", 
                    portal.index(), portal.destinationPortalName(), portal.destinationServer(), portal.arrivalLocation());
            }
            
            LOGGER.info("Attempt 1: Searching by destinationPortalName (current logic)...");
            for (PortalRegistrationData portal : CONFIG.portals()) {
                if (portalName.equals(portal.destinationPortalName())) {
                    LOGGER.info("  Found match by destinationPortalName: {}", portal.index());
                    if (teleportPlayerToArrival(player, portal, portalName, "login handoff")) {
                        markRecentHandoffTeleport(player.getUuid());
                        return;
                    }
                }
            }
            LOGGER.warn("  No match found by destinationPortalName");
            
            LOGGER.info("Attempt 2: Searching by index (alternative logic)...");
            for (PortalRegistrationData portal : CONFIG.portals()) {
                LOGGER.info("    Comparing portal.index='{}' with portalName='{}'", portal.index(), portalName);
                if (portalName.equals(portal.index())) {
                    LOGGER.info("  Found match by index: {}", portal.index());
                    if (teleportPlayerToArrival(player, portal, portalName, "login handoff")) {
                        markRecentHandoffTeleport(player.getUuid());
                        return;
                    }
                }
            }
            LOGGER.warn("  No match found by index");
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

        // Don't teleport in INIT; wait until JOIN so client/server spawn is established
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            LOGGER.debug("INIT for {} - deferring portal handoff until JOIN", handler.player.getName().getString());
        });

        // Apply handoff just after JOIN with a one-tick delay to ensure clean respawn flow
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                String portalName = consumePendingPortalTeleport(player.getUuid());
                if (portalName != null) {
                    queueLoginHandoffTeleport(player, portalName);
                } else {
                    LOGGER.debug("No pending portal handoff for {} on JOIN", player.getName().getString());
                }
            });
        });
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
            loginHandoffQueue.remove(playerId);
            LOGGER.debug("Removed join time tracking for disconnected player {}", 
                        handler.getPlayer().getName().getString());
        });
    }

    private static void queueLoginHandoffTeleport(ServerPlayerEntity player, String portalName) {
        loginHandoffQueue.put(player.getUuid(), new LoginHandoff(portalName, LOGIN_HANDOFF_DELAY_TICKS));
        suppressPortalInterceptor(player, "login handoff queued");
        LOGGER.info("Queued portal handoff '{}' for {} to fire in {} ticks", portalName, player.getName().getString(), LOGIN_HANDOFF_DELAY_TICKS);
    }

    public static boolean hasQueuedLoginHandoff(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return loginHandoffQueue.containsKey(playerId);
    }

    private void registerLoginHandoffTicker() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (loginHandoffQueue.isEmpty()) {
                return;
            }
            loginHandoffQueue.forEach((playerId, handoff) -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    LOGGER.debug("Dropping queued login handoff for {} because player is no longer online", playerId);
                    loginHandoffQueue.remove(playerId);
                    return;
                }

                int remaining = handoff.ticksRemaining() - 1;
                if (remaining <= 0) {
                    loginHandoffQueue.remove(playerId);
                    LOGGER.info("Executing queued portal handoff '{}' for {}", handoff.portalName(), player.getName().getString());
                    teleportToPortal(player, handoff.portalName());
                } else {
                    loginHandoffQueue.put(playerId, new LoginHandoff(handoff.portalName(), remaining));
                }
            });
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
        for (PortalRegistrationData portal : ServerPortalsMod.CONFIG.portals()) {
            try {
                CustomPortalBuilder builder = CustomPortalBuilder.beginPortal();
                
                // Set both destination and return dimension to nether to make portal activatable
                builder.destDimID(Identifier.of("minecraft", "the_nether"));
                builder.returnDim(Identifier.of("minecraft", "overworld"), false);
                
                builder.frameBlock(portal.frameBlock());
                builder.lightWithItem(portal.lightWithItem());
                builder.tintColor(portal.color());

                builder.registerPreIgniteEvent((player, world, portalPos, framePos, portalIgnitionSource) -> {
                    if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                        LOGGER.info("Blocked portal ignite for {} because igniter is not a player", portal.index());
                        return false;
                    }

                    if (!serverPlayer.hasPermissionLevel(1)) {
                        LOGGER.info("Blocked portal ignite for {} by non-op player {}", portal.index(), serverPlayer.getName().getString());
                        return false;
                    }

                    return true;
                });
                
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
                    
                        requestProxyTransfer(entity, portal);
                    return SHOULDTP.CANCEL_TP;  // Cancel the teleport since Velocity handles it
                });
                
                // Now register the portal
                builder.registerPortal();
            } catch (Exception e) {
                LOGGER.error("Failed to register portal: {}", portal.index(), e);
            }
        }
    }

    private void requestProxyTransfer(Entity entity, PortalRegistrationData portal) {
        try {
            // Check if entity is a ServerPlayerEntity
            if (entity instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                String target = resolveTargetServer(portal);
                if (target == null || target.isBlank()) {
                    LOGGER.warn("Portal {} has no resolvable target server; skipping proxy transfer", portal.index());
                    return;
                }

                String secret = CONFIG.portalRequestSecret();
                if (secret == null || secret.isBlank()) {
                    LOGGER.error("portalRequestSecret is not configured in server-portals.json; cannot request proxy transfer for portal {}", portal.index());
                    return;
                }

                String configuredDestinationPortal = portal.destinationPortalName();
                String sourcePortal;
                if (configuredDestinationPortal != null && !configuredDestinationPortal.isBlank()) {
                    sourcePortal = configuredDestinationPortal.trim();
                } else {
                    sourcePortal = portal.index() == null ? "" : portal.index();
                }
                long issuedAt = System.currentTimeMillis();
                String nonce = PortalRequestPayloadCodec.generateNonce();

                byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(player.getUuid(), target.trim(), sourcePortal, issuedAt, nonce);
                byte[] signature = PortalRequestSigner.hmacSha256(secret.trim(), unsigned);
                byte[] payload = PortalRequestPayloadCodec.encodeSigned(player.getUuid(), target.trim(), sourcePortal, issuedAt, nonce, signature);

                ServerPlayNetworking.send(player, new PortalRequestPayload(payload));
                LOGGER.info("Portal {} requested proxy transfer for {} -> {}", portal.index(), player.getName().getString(), target);
            } else {
                LOGGER.warn("Entity is not a ServerPlayerEntity, cannot execute command");
            }
        } catch (Exception e) {
            LOGGER.error("Fatal error in requestProxyTransfer", e);
        }
    }

    private static String resolveTargetServer(PortalRegistrationData portal) {
        if (portal == null) {
            return null;
        }
        String dest = portal.destinationServer();
        if (dest != null && !dest.isBlank()) {
            return dest.trim();
        }
        return null;
    }

    /**
     * Teleports the given player to the configured arrival location of the portal, including cross-world moves.
     * @return {@code true} if the teleport was performed successfully, {@code false} otherwise.
     */
    static boolean teleportPlayerToArrival(ServerPlayerEntity player,
                                           PortalRegistrationData portal,
                                           String portalName,
                                           String context) {
        if (portal == null) {
            LOGGER.warn("Attempted to teleport via portal '{}' but portal data was null ({})", portalName, context);
            return false;
        }

    ServerWorld currentWorld = player.getCommandSource().getWorld();
        String portalIndex = portal.index() != null ? portal.index() : portalName;

        LOGGER.info("Preparing teleport for player {} via portal {} ({}) from world {} at ({}, {}, {}) yaw={} pitch={} thread={}"
            , player.getName().getString()
            , portalIndex
            , context
            , currentWorld != null ? currentWorld.getRegistryKey().getValue() : "unknown"
            , player.getX()
            , player.getY()
            , player.getZ()
            , player.getYaw()
            , player.getPitch()
            , Thread.currentThread().getName());

        MinecraftServer server = player.getCommandSource().getServer();
        if (server == null) {
            LOGGER.error("Unable to teleport player {} via portal {} because server reference was null", 
                    player.getName().getString(), portal.index());
            return false;
        }

        var location = portal.arrivalLocation();
        LOGGER.debug("Portal {} arrival location data: {} ({})", portalIndex, location, context);
        if (location == null) {
            LOGGER.warn("Portal {} has no arrival location defined; cannot teleport player {} ({})",
                portalIndex, player.getName().getString(), context);
            return false;
        }

        ServerWorld targetWorld = resolveArrivalWorld(server, portal, portalName, context);
        if (targetWorld == null) {
            return false;
        }
        LOGGER.info("Resolved target world for portal {}: {} (expected from config: '{}') ({})",
            portalIndex,
                targetWorld.getRegistryKey().getValue(), 
                location.world(),
                context);

        double x = location.x();
        double y = location.y();
        double z = location.z();

        Vec3d position = new Vec3d(x, y, z);
        int chunkX = (int) Math.floor(x) >> 4;
        int chunkZ = (int) Math.floor(z) >> 4;
        boolean chunkLoaded = targetWorld.isChunkLoaded(chunkX, chunkZ);
        LOGGER.info("Target chunk for portal {}: chunkX={}, chunkZ={}, loaded={} in world {} ({})",
            portalIndex, chunkX, chunkZ, chunkLoaded, targetWorld.getRegistryKey().getValue(), context);

        if (!chunkLoaded) {
            LOGGER.info("Target chunk not loaded, forcing load for portal {} at ({}, {}) in world {}",
                portalIndex, chunkX, chunkZ, targetWorld.getRegistryKey().getValue());
            targetWorld.getChunk(chunkX, chunkZ);
        }

        LOGGER.debug("Computed teleport target for portal {}: world={} position=({}, {}, {}) velocity=(0.0, 0.0, 0.0) yaw={} pitch={} ({})",
            portalIndex,
        targetWorld.getRegistryKey().getValue(),
        x,
        y,
        z,
        player.getYaw(),
        player.getPitch(),
        context);
        
        // Suppress PortalInterceptor for this teleport by marking the player
        suppressPortalInterceptor(player, "login handoff teleport");
        
    LOGGER.info("Calling player.teleport with target world {} pos=({}, {}, {}) yaw={} pitch={} ({})",
        targetWorld.getRegistryKey().getValue(), x, y, z, player.getYaw(), player.getPitch(), context);
    player.teleport(targetWorld,
        x,
        y,
        z,
        java.util.EnumSet.noneOf(PositionFlag.class),
        player.getYaw(),
        player.getPitch(),
        true);
    LOGGER.info("After teleport: player world={} pos=({}, {}, {}) ({})",
        player.getCommandSource().getWorld().getRegistryKey().getValue(),
        player.getX(), player.getY(), player.getZ(), context);

        if (player instanceof de.michiruf.serverportals.api.EntityPortalTracking tracking) {
            tracking.serverportals$clearPortalTracking();
            LOGGER.info("Cleared portal tracking for player {} after teleport via portal {}", 
                    player.getName().getString(), portal.index());
        }

        LOGGER.info("Teleported player {} to portal {} in world {} at ({}, {}, {}) [{}]",
            player.getName().getString(),
            portalIndex,
                targetWorld.getRegistryKey().getValue(),
                x,
                y,
                z,
                context);
        return true;
    }

    private static ServerWorld resolveArrivalWorld(MinecraftServer server,
                                                   PortalRegistrationData portal,
                                                   String portalName,
                                                   String context) {
        PortalRegistrationData.ArrivalLocation location = portal.arrivalLocation();
        String rawWorld = location != null ? location.world() : null;
        Identifier worldId = normalizeWorldIdentifier(rawWorld);
        LOGGER.debug("Resolving arrival world for portal {} (rawWorld='{}', normalized='{}') ({})",
                portal.index(), rawWorld, worldId, context);
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        ServerWorld targetWorld = server.getWorld(worldKey);
        LOGGER.debug("Lookup result for world {}: {}", worldKey.getValue(), targetWorld != null ? "loaded" : "not loaded");
        if (targetWorld == null) {
            LOGGER.warn("Portal {} requested world {} but it is not loaded; falling back to overworld ({})", 
                    portal.index(), worldId, context);
            targetWorld = server.getOverworld();
            LOGGER.debug("Fallback overworld registry key: {}", targetWorld.getRegistryKey().getValue());
        }
        return targetWorld;
    }

    private static Identifier normalizeWorldIdentifier(String rawWorld) {
        if (rawWorld == null || rawWorld.isBlank()) {
            LOGGER.debug("normalizeWorldIdentifier: raw value '{}' treated as default overworld", rawWorld);
            return Identifier.of("minecraft", "overworld");
        }

        String trimmed = rawWorld.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "overworld":
            case "the_overworld":
                return Identifier.of("minecraft", "overworld");
            case "nether":
            case "the_nether":
                return Identifier.of("minecraft", "the_nether");
            case "end":
            case "the_end":
                return Identifier.of("minecraft", "the_end");
            default:
                Identifier parsed = Identifier.tryParse(trimmed);
                LOGGER.debug("normalizeWorldIdentifier: attempting to parse custom world '{}' -> {}", trimmed, parsed);
                if (parsed != null) {
                    return parsed;
                }
                LOGGER.warn("Could not parse world identifier '{}'; defaulting to minecraft:overworld", trimmed);
                return Identifier.of("minecraft", "overworld");
        }
    }

    private static void suppressPortalInterceptor(ServerPlayerEntity player, String reason) {
        try {
            Class<?> interceptorClass = Class.forName("com.silver.enderfight.portal.PortalInterceptor");
            java.lang.reflect.Method suppressMethod = interceptorClass.getMethod("suppressNextRedirect", ServerPlayerEntity.class);
            suppressMethod.invoke(null, player);
            LOGGER.info("Suppressed PortalInterceptor for {} of {}", reason, player.getName().getString());
        } catch (Exception e) {
            LOGGER.debug("Could not suppress PortalInterceptor ({}): {}", reason, e.getMessage());
        }
    }
}
