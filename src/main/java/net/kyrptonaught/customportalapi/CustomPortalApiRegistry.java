package net.kyrptonaught.customportalapi;

import net.kyrptonaught.customportalapi.util.PortalLink;
import net.kyrptonaught.customportalapi.portal.frame.PortalFrameTester;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomPortalApiRegistry {
    public static final ConcurrentHashMap<Block, PortalLink> portals = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, PortalFrameTester.PortalFrameTesterFactory> frameTesters = new ConcurrentHashMap<>();
    private CustomPortalApiRegistry() { }
    public static PortalLink getPortalLinkFromBase(Block block) { return block == null ? null : portals.get(block); }
    public static boolean isRegisteredFrameBlock(BlockState state) { return state != null && portals.containsKey(state.getBlock()); }
    public static Collection<PortalLink> getAllPortalLinks() { return portals.values(); }
    public static void registerPortalFrameTester(Identifier id, PortalFrameTester.PortalFrameTesterFactory factory) { frameTesters.put(id, factory); }
    public static PortalFrameTester.PortalFrameTesterFactory getPortalFrameTester(Identifier id) { return frameTesters.get(id); }
    public static void addPortal(Block frameBlock, PortalLink link) { if (frameBlock == null || link == null) { CustomPortalsMod.logError("Invalid portal registration"); return; } forceAddPortal(frameBlock, link); }
    public static void forceAddPortal(Block frameBlock, PortalLink link) { if (frameBlock != null && link != null) portals.put(frameBlock, link); }
}
