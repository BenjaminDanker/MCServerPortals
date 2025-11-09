package de.michiruf.serverportals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.michiruf.serverportals.config.PortalRegistrationData;
import de.michiruf.serverportals.versioned.VersionedMessageSender;
import de.michiruf.serverportals.versioned.VersionedRegistry;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * TODO Enhance this class with positions and dimensions?
 * <p>
 * Commands to test with:
 * /serverportals list
 * /serverportals register 1 minecraft:bone_block minecraft:ender_eye black "velocity version"
 * /serverportals unregister 1
 *
 * @author Michael Ruf
 * @since 2022-12-03
 */
public class Command {

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                        CommandRegistryAccess registry,
                                        CommandManager.RegistrationEnvironment environment) {
        LiteralCommandNode<ServerCommandSource> rootNode = CommandManager
                .literal("serverportals")
                .requires(cmd -> cmd.hasPermissionLevel(4))
                .executes(context -> {
                    VersionedMessageSender.send(context, "Usage: /serverportals list");
                    VersionedMessageSender.send(context, "Usage: /serverportals register name frameBlock lightWith color command");
                    VersionedMessageSender.send(context, "Usage: /serverportals unregister name");
                    return 1;
                })
                .build();
        registerListCommand(rootNode);
        registerRegisterCommand(rootNode, registry);
        registerUnregisterCommand(rootNode, registry);
        registerReceivePortalCommand(rootNode);
        dispatcher.getRoot().addChild(rootNode);
    }

    private static void registerReceivePortalCommand(LiteralCommandNode<ServerCommandSource> node) {
        ServerPortalsMod.LOGGER.debug("Registering receive-portal subcommand");
        node.addChild(CommandManager
                .literal("receive-portal")
                .then(CommandManager.argument("portalName", StringArgumentType.word())
                        .executes(context -> {
                            ServerPortalsMod.LOGGER.info("Receive-portal command executed");
                            return executeReceivePortalCommand(context);
                        })
                )
                .build());
    }

    private static int executeReceivePortalCommand(CommandContext<ServerCommandSource> context) {
        try {
            var source = context.getSource();
            var player = source.getPlayer();
            if (player == null) {
                ServerPortalsMod.LOGGER.warn("Receive-portal called but source is not a player");
                return 1;
            }
            
            String portalName = StringArgumentType.getString(context, "portalName");
            ServerPortalsMod.LOGGER.info("Player {} will be teleported to portal {}", 
                    player.getName().getString(), portalName);
            
            // Find portal in config with matching destinationPortalName
            // When you come through portal X on server A, you arrive at server B
            // Server B needs to find its portal whose destinationPortalName == X
            if (ServerPortalsMod.CONFIG.portals() != null) {
                for (de.michiruf.serverportals.config.PortalRegistrationData portal : ServerPortalsMod.CONFIG.portals()) {
                    if (portalName.equals(portal.destinationPortalName())) {
                        if (portal.arrivalLocation() != null) {
                            ServerPortalsMod.LOGGER.info("Found matching portal {} (came from: {}) with destination ({}, {}, {})",
                                    portal.index(),
                                    portal.destinationPortalName(),
                                    portal.arrivalLocation().x(),
                                    portal.arrivalLocation().y(),
                                    portal.arrivalLocation().z());

                            var dest = portal.arrivalLocation();
                            player.setPosition(dest.x(), dest.y(), dest.z());
                            player.networkHandler.requestTeleport(dest.x(), dest.y(), dest.z(),
                                    player.getYaw(), player.getPitch());

                            // Clear portal tracking so player doesn't immediately trigger the portal again
                            if (player instanceof de.michiruf.serverportals.api.EntityPortalTracking tracking) {
                                tracking.serverportals$clearPortalTracking();
                                ServerPortalsMod.LOGGER.info("Cleared portal tracking for player {} after teleport",
                                        player.getName().getString());
                            }

                            ServerPortalsMod.LOGGER.info("Teleported player {} to portal destination",
                                    player.getName().getString());
                            return 0;
                        }
                    }
                }
            }
            
            ServerPortalsMod.LOGGER.warn("No matching portal found for destinationPortalName: {}", portalName);
            return 1;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing receive-portal command", e);
            return 1;
        }
    }

    private static void registerListCommand(LiteralCommandNode<ServerCommandSource> node) {
        ServerPortalsMod.LOGGER.debug("Registering list subcommand");
        node.addChild(CommandManager
                .literal("list")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.info("List command executed, processing execution");
                    return executeListCommand(context);
                })
                .build());
    }

    private static int executeListCommand(CommandContext<ServerCommandSource> context) {
        try {
            var listString = ServerPortalsMod.CONFIG.portals() != null
                    ? ServerPortalsMod.CONFIG.portals().stream()
                    .map(PortalRegistrationData::toString)
                    .collect(Collectors.joining("\n"))
                    : "";
            if (listString.isEmpty())
                listString = "None";
            VersionedMessageSender.send(context, listString);
            return 0;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing list command", e);
            return 1;
        }
    }

    private static void registerRegisterCommand(LiteralCommandNode<ServerCommandSource> node, CommandRegistryAccess registry) {
        ServerPortalsMod.LOGGER.debug("Registering register subcommand");
        node.addChild(CommandManager
                .literal("register")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.warn("Register command executed without required arguments");
                    VersionedMessageSender.send(context, "Invalid usage. See /serverportals");
                    return 1;
                })
                .then(CommandManager.argument("index", StringArgumentType.word())
                        .then(CommandManager.argument("frameBlock", BlockStateArgumentType.blockState(registry))
                                .then(CommandManager.argument("lightWith", ItemStackArgumentType.itemStack(registry))
                                        .then(CommandManager.argument("color", ColorArgumentType.color())
                                                .then(CommandManager.argument("command", StringArgumentType.string())
                                                        .executes(context -> {
                                                            ServerPortalsMod.LOGGER.info("Register command executed with all arguments");
                                                            return executeRegisterCommand(context);
                                                        })
                                                )
                                        )
                                )
                        )
                )
                .build());
    }

    private static int executeRegisterCommand(CommandContext<ServerCommandSource> context) {
        try {
            var index = StringArgumentType.getString(context, "index");
            var frameBlock = BlockStateArgumentType.getBlockState(context, "frameBlock");
            var lightWith = ItemStackArgumentType.getItemStackArgument(context, "lightWith");
            var color = ColorArgumentType.getColor(context, "color");
            var command = StringArgumentType.getString(context, "command");

            // Create the list if null
            if (ServerPortalsMod.CONFIG.portals() == null) {
                ServerPortalsMod.CONFIG.portals(new ArrayList<>());
            }

            // Check if the list contains this index and abort if so
            var registeredIndexes = ServerPortalsMod.CONFIG.portals().stream()
                    .map(PortalRegistrationData::index)
                    .toList();
            if (registeredIndexes.contains(index)) {
                VersionedMessageSender.send(context, "Portal with index " + index + " is already registered. Unregister it first");
                return 2;
            }

            var portal = new PortalRegistrationData(
                    index,
                    VersionedRegistry.block().getId(frameBlock.getBlockState().getBlock()).toString(),
                    VersionedRegistry.item().getId(lightWith.getItem()).toString(),
                    color.getColorValue() != null ? color.getColorValue() : 0,
                    command);
            ServerPortalsMod.CONFIG.portals().add(portal);
            ServerPortalsMod.CONFIG.save();

            VersionedMessageSender.send(context, "Registered portal " + portal);
            printRestartInfo(context);
            return 0;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing register command", e);
            return 1;
        }
    }

    private static void registerUnregisterCommand(LiteralCommandNode<ServerCommandSource> node, CommandRegistryAccess registry) {
        ServerPortalsMod.LOGGER.debug("Registering unregister subcommand");
        node.addChild(CommandManager
                .literal("unregister")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.warn("Unregister command executed without required arguments");
                    VersionedMessageSender.send(context, "Invalid usage. See /serverportals");
                    return 1;
                })
                .then(CommandManager.argument("index", StringArgumentType.word())
                        .executes(context -> {
                            ServerPortalsMod.LOGGER.info("Unregister command executed with index argument");
                            return executeUnregisterCommand(context);
                        })
                )
                .build());
    }

    private static int executeUnregisterCommand(CommandContext<ServerCommandSource> context) {
        ServerPortalsMod.LOGGER.info("Executing /serverportals unregister command");
        try {
            var index = StringArgumentType.getString(context, "index");
            ServerPortalsMod.LOGGER.info("Executing /serverportals unregister command with index: {}", index);

            // Cancel if not list exists
            if (ServerPortalsMod.CONFIG.portals() == null) {
                ServerPortalsMod.LOGGER.error("Portal list does not exist");
                VersionedMessageSender.send(context, "Portal list does not exist");
                return 2;
            }

            // Remove the entry (iterative, if some duplicate entries exist because errors occurred at some point)
            boolean contained;
            int removedCount = 0;
            do {
                contained = false;
                for (int i = 0; i < ServerPortalsMod.CONFIG.portals().size(); i++) {
                    var portal = ServerPortalsMod.CONFIG.portals().get(i);
                    if (index.equals(portal.index())) {
                        contained = ServerPortalsMod.CONFIG.portals().remove(portal);
                        removedCount++;
                        ServerPortalsMod.LOGGER.info("Unregistered portal {}", portal);
                        VersionedMessageSender.send(context, "Unregistered portal " + portal);
                    }
                }
            } while (contained);

            if (removedCount == 0) {
                ServerPortalsMod.LOGGER.warn("No portal found with index {} to unregister", index);
            } else {
                ServerPortalsMod.LOGGER.info("Successfully unregistered {} portal(s) with index {}", removedCount, index);
            }

            // Save must be trigger manually here, because it is a list and cannot observe changes (even when calling
            // getter and setter manually
            ServerPortalsMod.CONFIG.save();

            printRestartInfo(context);
            return 0;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing unregister command", e);
            return 1;
        }
    }

    private static void printRestartInfo(CommandContext<ServerCommandSource> context) {
        VersionedMessageSender.send(context, "For this configuration to take effect, restart the server");
    }
}
