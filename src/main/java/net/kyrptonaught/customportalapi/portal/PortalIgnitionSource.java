package net.kyrptonaught.customportalapi.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import java.util.HashSet;
import java.util.function.BiFunction;

public final class PortalIgnitionSource {
    public enum SourceType { USEITEM, BLOCKPLACED, FLUID, CUSTOM }
    public static final PortalIgnitionSource FIRE = new PortalIgnitionSource(SourceType.BLOCKPLACED, BuiltInRegistries.BLOCK.getKey(Blocks.FIRE));
    public static final PortalIgnitionSource WATER = FluidSource(Fluids.WATER);
    private static final HashSet<Item> USEITEMS = new HashSet<>();
    public final SourceType sourceType;
    public final Identifier ignitionSourceID;
    public Player player;
    private PortalIgnitionSource(SourceType type, Identifier id) { sourceType = type; ignitionSourceID = id; }
    public PortalIgnitionSource withPlayer(Player player) { this.player = player; return this; }
    public static PortalIgnitionSource ItemUseSource(Item item) { USEITEMS.add(item); return new PortalIgnitionSource(SourceType.USEITEM, BuiltInRegistries.ITEM.getKey(item)); }
    public static PortalIgnitionSource FluidSource(Fluid fluid) { return new PortalIgnitionSource(SourceType.FLUID, BuiltInRegistries.FLUID.getKey(fluid)); }
    public static PortalIgnitionSource CustomSource(Identifier id) { return new PortalIgnitionSource(SourceType.CUSTOM, id); }
    @Deprecated public void withCondition(BiFunction<Level, BlockPos, Boolean> condition) { }
    public boolean isWater() { Fluid fluid = BuiltInRegistries.FLUID.getValue(ignitionSourceID); return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER; }
    public boolean isLava() { Fluid fluid = BuiltInRegistries.FLUID.getValue(ignitionSourceID); return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA; }
    public static boolean isRegisteredIgnitionSourceWith(Item item) { return USEITEMS.contains(item); }
}
