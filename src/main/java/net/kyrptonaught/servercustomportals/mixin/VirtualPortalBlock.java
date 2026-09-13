package net.kyrptonaught.servercustomportals.mixin;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Presents CustomPortalAPI's server-side portal block as vanilla portal blocks
 * to clients, preserving the vanilla-client compatibility of ServerPortals.
 */
@Mixin(CustomPortalBlock.class)
public abstract class VirtualPortalBlock implements PolymerBlock {

    @Override
    public Block getPolymerReplacement(Block state, PacketContext context) {
        // The replacement is selected again from the actual block state by
        // getPolymerBlockState below; Polymer passes the registered block here.
        return Blocks.NETHER_PORTAL;
    }

    private Block getPolymerBlock(BlockState state) {
        Direction.Axis axis = CustomPortalHelper.getAxisFrom(state);
        return axis == Direction.Axis.Y ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        Direction.Axis axis = CustomPortalHelper.getAxisFrom(state);
        if (axis == Direction.Axis.Y) {
            return Blocks.END_PORTAL.defaultBlockState();
        }
        BlockState result = Blocks.NETHER_PORTAL.defaultBlockState();
        return axis == Direction.Axis.Z
                ? result.setValue(NetherPortalBlock.AXIS, Direction.Axis.Z)
                : result;
    }

    @Override
    public void onPolymerBlockSend(BlockState state, BlockPos.MutableBlockPos pos, ServerPlayer player) {
        Direction.Axis axis = CustomPortalHelper.getAxisFrom(state);
        if (axis == Direction.Axis.Y) {
            player.connection.send(PolymerBlockUtils.createBlockEntityPacket(
                    pos,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "end_portal")),
                    new CompoundTag()));
        }
    }
}
