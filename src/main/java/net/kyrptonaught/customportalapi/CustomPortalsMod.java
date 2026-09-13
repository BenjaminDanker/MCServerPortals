package net.kyrptonaught.customportalapi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kyrptonaught.customportalapi.portal.PortalIgnitionSource;
import net.kyrptonaught.customportalapi.portal.PortalPlacer;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Minimal server-side CustomPortalAPI bootstrap used by MCServerPortals. */
public final class CustomPortalsMod implements ModInitializer {
    public static final String MOD_ID = "customportalapi";
    public static final Identifier VANILLAPORTAL_FRAMETESTER = Identifier.fromNamespaceAndPath(MOD_ID, "vanillanether");
    public static final Identifier FLATPORTAL_FRAMETESTER = Identifier.fromNamespaceAndPath(MOD_ID, "flat");
    public static final CustomPortalBlock portalBlock = Registry.register(
            BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath(MOD_ID, "customportalblock"),
            new CustomPortalBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath(MOD_ID, "customportalblock")))
                    .noCollision().randomTicks().strength(-1.0f)
                    .sound(SoundType.GLASS).lightLevel(state -> 11).pushReaction(PushReaction.BLOCK)));

    @Override public void onInitialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            Item item = player.getItemInHand(hand).getItem();
            if (!PortalIgnitionSource.isRegisteredIgnitionSourceWith(item)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            HitResult hit = player.pick(6.0D, 1.0F, false);
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos portalPos = blockHit.getBlockPos().relative(blockHit.getDirection());
                if (PortalPlacer.attemptPortalLight(world, portalPos,
                        PortalIgnitionSource.ItemUseSource(item).withPlayer(player))) {
                    return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    public static void logError(String message) { System.out.println("[" + MOD_ID + "]ERROR: " + message); }
    public static Block getDefaultPortalBlock() { return portalBlock; }
}
