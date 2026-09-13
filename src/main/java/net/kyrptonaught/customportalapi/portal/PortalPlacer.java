package net.kyrptonaught.customportalapi.portal;

import net.kyrptonaught.customportalapi.CustomPortalApiRegistry;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.kyrptonaught.customportalapi.util.PortalLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small server-side frame igniter for the MCServerPortals use case.
 * It supports the standard two-by-three portal opening while keeping the
 * public CustomPortalAPI builder contract intact.
 */
public final class PortalPlacer {
    private PortalPlacer() { }

    public static boolean attemptPortalLight(Level world, BlockPos portalPos, PortalIgnitionSource ignitionSource) {
        BlockPos framePos = findNearbyFrame(world, portalPos);
        if (framePos == null) {
            return false;
        }
        Block frame = world.getBlockState(framePos).getBlock();
        PortalLink link = CustomPortalApiRegistry.getPortalLinkFromBase(frame);
        if (link == null || !link.doesIgnitionMatch(ignitionSource)
                || !link.canLightInDim(world.dimension().identifier())) {
            return false;
        }
        if (!link.getPortalPreIgniteEvent().attemptLight(ignitionSource.player, world, portalPos, framePos, ignitionSource)) {
            return true;
        }

        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            for (int offset = -2; offset <= 1; offset++) {
                for (int yOffset = -2; yOffset <= 1; yOffset++) {
                    BlockPos origin = portalPos.relative(axis, offset).below(-yOffset);
                    if (createPortal(world, origin, axis, frame, link)) {
                        link.getPortalIgniteEvent().afterLight(ignitionSource.player, world, origin, framePos, ignitionSource);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos findNearbyFrame(Level world, BlockPos pos) {
        if (CustomPortalApiRegistry.isRegisteredFrameBlock(world.getBlockState(pos))) {
            return pos;
        }
        for (Direction direction : Direction.values()) {
            BlockPos candidate = pos.relative(direction);
            if (CustomPortalApiRegistry.isRegisteredFrameBlock(world.getBlockState(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean createPortal(Level world, BlockPos origin, Direction.Axis axis,
                                       Block frame, PortalLink link) {
        int width = link.forcedWidth == 0 ? 2 : link.forcedWidth;
        int height = link.forcedHeight == 0 ? 3 : link.forcedHeight;
        if (width < 2 || height < 3 || width > 21 || height > 21) {
            return false;
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (!isReplaceable(world.getBlockState(origin.relative(axis, i).above(j)))) {
                    return false;
                }
            }
        }
        for (int i = -1; i <= width; i++) {
            if (!isFrame(world.getBlockState(origin.relative(axis, i).below()), frame)
                    || !isFrame(world.getBlockState(origin.relative(axis, i).above(height)), frame)) {
                return false;
            }
        }
        for (int j = 0; j < height; j++) {
            if (!isFrame(world.getBlockState(origin.relative(axis, -1).above(j)), frame)
                    || !isFrame(world.getBlockState(origin.relative(axis, width).above(j)), frame)) {
                return false;
            }
        }

        BlockState portalState = link.getPortalBlock().defaultBlockState()
                .setValue(CustomPortalBlock.AXIS, axis);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                world.setBlock(origin.relative(axis, i).above(j), portalState, 18);
            }
        }
        return true;
    }

    private static boolean isFrame(BlockState state, Block frame) {
        return state.getBlock() == frame;
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
