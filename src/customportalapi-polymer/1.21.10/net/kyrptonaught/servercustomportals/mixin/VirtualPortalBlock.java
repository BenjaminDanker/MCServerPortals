package net.kyrptonaught.servercustomportals.mixin;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyrptonaught.customportalapi.CustomPortalBlock;
import net.kyrptonaught.customportalapi.networking.ForcePlacePacket;
import net.kyrptonaught.customportalapi.util.CustomPortalHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import xyz.nucleoid.packettweaker.PacketContext;

// See https://github.com/kyrptonaught/CustomPortalApi-Polymer/blob/1.18/src/main/java/net/kyrptonaught/servercustomportals/mixin/VirtualPortalBlock.java
// Adapted for polymer-core API for MC 1.21.10
@Mixin(CustomPortalBlock.class)
public abstract class VirtualPortalBlock implements PolymerBlock {

    public Block getPolymerBlock(BlockState state) {
        Direction.Axis dir = CustomPortalHelper.getAxisFrom(state);
        if (dir == Direction.Axis.Y)
            return Blocks.END_PORTAL;
        return Blocks.NETHER_PORTAL;
    }

    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        Direction.Axis dir = CustomPortalHelper.getAxisFrom(state);
        if (dir == Direction.Axis.Y)
            return Blocks.END_PORTAL.getDefaultState();

        if (dir == Direction.Axis.Z)
            return this.getPolymerBlock(state).getDefaultState().with(NetherPortalBlock.AXIS, Direction.Axis.Z);

        return this.getPolymerBlock(state).getDefaultState();
    }

    public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, ServerPlayerEntity player) {
        Direction.Axis dir = CustomPortalHelper.getAxisFrom(blockState);
        if (dir == Direction.Axis.Y) {
            player.networkHandler.sendPacket(PolymerBlockUtils.createBlockEntityPacket(pos, BlockEntityType.END_PORTAL, new NbtCompound()));
        }
        ForcePlacePacket packet = new ForcePlacePacket(pos.toImmutable(), dir.ordinal());
        ServerPlayNetworking.send(player, packet);
    }
}
