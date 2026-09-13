package net.kyrptonaught.customportalapi.util;

import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class CustomPortalHelper {
    private CustomPortalHelper() { }
    public static boolean isInstanceOfCustomPortal(Level world, BlockPos pos) { return isInstanceOfCustomPortal(world.getBlockState(pos)); }
    public static boolean isInstanceOfCustomPortal(BlockState state) { return state.getBlock() instanceof CustomPortalBlock; }
    public static boolean isInstanceOfPortalFrame(Level world, BlockPos pos) { return CustomPortalApiRegistry.isRegisteredFrameBlock(world.getBlockState(pos)); }
    public static Block getPortalBase(Level world, BlockPos pos) {
        if (isInstanceOfCustomPortal(world, pos)) return getPortalBaseDefault(world, pos);
        return isInstanceOfPortalFrame(world, pos) ? world.getBlockState(pos).getBlock() : Blocks.AIR;
    }
    public static Block getPortalBaseDefault(Level world, BlockPos pos) {
        if (isInstanceOfPortalFrame(world, pos)) return world.getBlockState(pos).getBlock();
        if (!isInstanceOfCustomPortal(world, pos)) return Blocks.AIR;
        Direction.Axis axis = getAxisFrom(world.getBlockState(pos));
        for (Direction dir : Direction.values()) {
            BlockPos candidate = pos.relative(dir);
            if (isInstanceOfPortalFrame(world, candidate)) return world.getBlockState(candidate).getBlock();
        }
        return Blocks.AIR;
    }
    public static Direction.Axis getAxisFrom(BlockState state) {
        if (state.getBlock() instanceof CustomPortalBlock) return state.getValue(CustomPortalBlock.AXIS);
        if (state.getBlock() instanceof NetherPortalBlock) return state.getValue(NetherPortalBlock.AXIS);
        if (state.getBlock() instanceof EndPortalBlock) return Direction.Axis.Y;
        return Direction.Axis.X;
    }
    public static BlockState blockWithAxis(BlockState state, Direction.Axis axis) { return state.getBlock() instanceof CustomPortalBlock ? state.setValue(CustomPortalBlock.AXIS, axis) : state; }
}
