package net.kyrptonaught.customportalapi.api;

import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.kyrptonaught.customportalapi.event.CPASoundEventData;
import net.kyrptonaught.customportalapi.event.PortalIgniteEvent;
import net.kyrptonaught.customportalapi.event.PortalPreIgniteEvent;
import net.kyrptonaught.customportalapi.util.PortalLink;
import net.kyrptonaught.customportalapi.util.SHOULDTP;
import net.kyrptonaught.customportalapi.portal.PortalIgnitionSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Consumer;
import java.util.function.Function;

/** Server-side builder API retained for MCServerPortals compatibility. */
public final class CustomPortalBuilder {
    private final PortalLink portalLink;
    private CustomPortalBuilder(PortalLink link) { this.portalLink = link; }
    public static CustomPortalBuilder beginPortal() { return new CustomPortalBuilder(new PortalLink()); }
    public static CustomPortalBuilder beginPortal(PortalLink link) { return new CustomPortalBuilder(link); }
    public PortalLink registerPortal() {
        Block frame = portalLink.block == null ? null : BuiltInRegistries.BLOCK.getValue(portalLink.block);
        CustomPortalApiRegistry.addPortal(frame, portalLink); return portalLink;
    }
    @Deprecated public PortalLink registerPortalForced() {
        Block frame = portalLink.block == null ? null : BuiltInRegistries.BLOCK.getValue(portalLink.block);
        CustomPortalApiRegistry.forceAddPortal(frame, portalLink); return portalLink;
    }
    public CustomPortalBuilder frameBlock(Identifier id) { portalLink.block = id; return this; }
    public CustomPortalBuilder frameBlock(Block block) { portalLink.block = BuiltInRegistries.BLOCK.getKey(block); return this; }
    public CustomPortalBuilder destDimID(Identifier id) { portalLink.dimID = id; return this; }
    public CustomPortalBuilder returnDim(Identifier id, boolean only) { portalLink.returnDimID = id; portalLink.onlyIgnitableInReturnDim = only; return this; }
    public CustomPortalBuilder tintColor(int color) { portalLink.colorID = color; return this; }
    public CustomPortalBuilder tintColor(int r, int g, int b) { portalLink.colorID = (r << 16) | (g << 8) | b; return this; }
    public CustomPortalBuilder lightWithItem(Item item) { portalLink.portalIgnitionSource = PortalIgnitionSource.ItemUseSource(item); return this; }
    public CustomPortalBuilder lightWithFluid(Fluid fluid) { portalLink.portalIgnitionSource = PortalIgnitionSource.FluidSource(fluid); return this; }
    public CustomPortalBuilder lightWithWater() { portalLink.portalIgnitionSource = PortalIgnitionSource.WATER; return this; }
    public CustomPortalBuilder customIgnitionSource(Identifier id) { portalLink.portalIgnitionSource = PortalIgnitionSource.CustomSource(id); return this; }
    public CustomPortalBuilder customIgnitionSource(PortalIgnitionSource source) { portalLink.portalIgnitionSource = source; return this; }
    public CustomPortalBuilder forcedSize(int width, int height) { portalLink.forcedWidth = width; portalLink.forcedHeight = height; return this; }
    public CustomPortalBuilder customPortalBlock(CustomPortalBlock block) { portalLink.setPortalBlock(block); return this; }
    public CustomPortalBuilder onlyLightInOverworld() { portalLink.onlyIgnitableInReturnDim = true; return this; }
    public CustomPortalBuilder flatPortal() { return this; }
    public CustomPortalBuilder customFrameTester(Identifier id) { portalLink.portalFrameTester = id; return this; }
    public CustomPortalBuilder setPortalSearchYRange(int bottom, int top) { portalLink.portalSearchYBottom = bottom; portalLink.portalSearchYTop = top; return this; }
    public CustomPortalBuilder setReturnPortalSearchYRange(int bottom, int top) { portalLink.returnPortalSearchYBottom = bottom; portalLink.returnPortalSearchYTop = top; return this; }
    public CustomPortalBuilder registerBeforeTPEvent(Function<Entity, SHOULDTP> event) { portalLink.getBeforeTPEvent().register(event); return this; }
    public CustomPortalBuilder registerPostTPEvent(Consumer<Entity> event) { portalLink.setPostTPEvent(event); return this; }
    public CustomPortalBuilder registerPreIgniteEvent(PortalPreIgniteEvent event) { portalLink.setPortalPreIgniteEvent(event); return this; }
    public CustomPortalBuilder registerIgniteEvent(PortalIgniteEvent event) { portalLink.setPortalIgniteEvent(event); return this; }
    public CustomPortalBuilder registerInPortalAmbienceSound(Function<Player, CPASoundEventData> event) { return this; }
    public CustomPortalBuilder registerPostTPPortalAmbience(Function<Player, CPASoundEventData> event) { return this; }
}
