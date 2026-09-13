package net.kyrptonaught.customportalapi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.util.RandomSource;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.kyrptonaught.customportalapi.util.PortalLink;
import net.kyrptonaught.customportalapi.util.SHOULDTP;

/** Server-side custom portal block.  Teleport decisions are owned by MCServerPortals. */
public class CustomPortalBlock extends Block implements Portal {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape Y_SHAPE = Block.box(0, 6, 0, 16, 10, 16);

    public CustomPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) { case Z -> Z_SHAPE; case Y -> Y_SHAPE; default -> X_SHAPE; };
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }
    @Override protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) { return state; }
    @Override protected net.minecraft.world.item.ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
    @Override public int getPortalTransitionTime(ServerLevel world, Entity entity) { return 1; }
    @Override public TeleportTransition getPortalDestination(ServerLevel world, Entity entity, BlockPos pos) {
        // MCServerPortals owns the cross-server handoff.  Invoke the registered
        // before-teleport callback and deliberately return null so vanilla does
        // not perform a local dimension teleport as well.
        Block frame = CustomPortalHelper.getPortalBase(world, pos);
        PortalLink link = CustomPortalApiRegistry.getPortalLinkFromBase(frame);
        if (link != null) {
            SHOULDTP decision = link.getBeforeTPEvent().execute(entity);
            if (decision == SHOULDTP.CONTINUE_TP) {
                link.executePostTPEvent(entity);
            }
        }
        return null;
    }
}
