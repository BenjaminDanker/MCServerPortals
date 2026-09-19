package de.michiruf.serverportals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.michiruf.serverportals.config.PortalRegistrationData;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

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

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                        CommandBuildContext registry,
                                        Commands.CommandSelection environment) {
        LiteralCommandNode<CommandSourceStack> rootNode = Commands
                .literal("serverportals")
                .requires(cmd -> cmd.permissions() instanceof LevelBasedPermissionSet p && p.level().id() >= 4)
                .executes(context -> {
                    send(context, "Usage: /serverportals list");
                    send(context, "Usage: /serverportals register name frameBlock lightWith color command");
                    send(context, "Usage: /serverportals unregister name");
                    return 1;
                })
                .build();
        registerListCommand(rootNode);
        registerRegisterCommand(rootNode, registry);
        registerUnregisterCommand(rootNode, registry);
        dispatcher.getRoot().addChild(rootNode);
    }

    private static void registerListCommand(LiteralCommandNode<CommandSourceStack> node) {
        ServerPortalsMod.LOGGER.debug("Registering list subcommand");
        node.addChild(Commands
                .literal("list")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.info("List command executed, processing execution");
                    return executeListCommand(context);
                })
                .build());
    }

    private static int executeListCommand(CommandContext<CommandSourceStack> context) {
        try {
            var listString = ServerPortalsMod.CONFIG.portals() != null
                    ? ServerPortalsMod.CONFIG.portals().stream()
                    .map(PortalRegistrationData::toString)
                    .collect(Collectors.joining("\n"))
                    : "";
            if (listString.isEmpty())
                listString = "None";
            send(context, listString);
            return 0;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing list command", e);
            return 1;
        }
    }

    private static void registerRegisterCommand(LiteralCommandNode<CommandSourceStack> node, CommandBuildContext registry) {
        ServerPortalsMod.LOGGER.debug("Registering register subcommand");
        node.addChild(Commands
                .literal("register")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.warn("Register command executed without required arguments");
                    send(context, "Invalid usage. See /serverportals");
                    return 1;
                })
                .then(Commands.argument("index", StringArgumentType.word())
                        .then(Commands.argument("frameBlock", BlockStateArgument.block(registry))
                                .then(Commands.argument("lightWith", ItemArgument.item(registry))
                                        .then(Commands.argument("color", HexColorArgument.hexColor())
                                                .then(Commands.argument("command", StringArgumentType.string())
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

    private static int executeRegisterCommand(CommandContext<CommandSourceStack> context) {
        try {
            var index = StringArgumentType.getString(context, "index");
            var frameBlock = BlockStateArgument.getBlock(context, "frameBlock");
            var lightWith = ItemArgument.getItem(context, "lightWith");
            var color = HexColorArgument.getHexColor(context, "color");
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
                send(context, "Portal with index " + index + " is already registered. Unregister it first");
                return 2;
            }

            var portal = new PortalRegistrationData(
                    index,
                    BuiltInRegistries.BLOCK.getKey(frameBlock.getState().getBlock()).toString(),
                    BuiltInRegistries.ITEM.getKey(lightWith.item().value()).toString(),
                    color != null ? color : 0,
                    command);
            ServerPortalsMod.CONFIG.portals().add(portal);
            ServerPortalsMod.CONFIG.save();

            send(context, "Registered portal " + portal);
            printRestartInfo(context);
            return 0;
        } catch (Exception e) {
            ServerPortalsMod.LOGGER.error("Error executing register command", e);
            return 1;
        }
    }

    private static void registerUnregisterCommand(LiteralCommandNode<CommandSourceStack> node, CommandBuildContext registry) {
        ServerPortalsMod.LOGGER.debug("Registering unregister subcommand");
        node.addChild(Commands
                .literal("unregister")
                .executes(context -> {
                    ServerPortalsMod.LOGGER.warn("Unregister command executed without required arguments");
                    send(context, "Invalid usage. See /serverportals");
                    return 1;
                })
                .then(Commands.argument("index", StringArgumentType.word())
                        .executes(context -> {
                            ServerPortalsMod.LOGGER.info("Unregister command executed with index argument");
                            return executeUnregisterCommand(context);
                        })
                )
                .build());
    }

    private static int executeUnregisterCommand(CommandContext<CommandSourceStack> context) {
        ServerPortalsMod.LOGGER.info("Executing /serverportals unregister command");
        try {
            var index = StringArgumentType.getString(context, "index");
            ServerPortalsMod.LOGGER.info("Executing /serverportals unregister command with index: {}", index);

            // Cancel if not list exists
            if (ServerPortalsMod.CONFIG.portals() == null) {
                ServerPortalsMod.LOGGER.error("Portal list does not exist");
                send(context, "Portal list does not exist");
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
                        send(context, "Unregistered portal " + portal);
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

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        CommandSourceStack source = context.getSource();
        if (source.getPlayer() != null) {
            source.getPlayer().sendSystemMessage(Component.literal(message));
        } else {
            source.sendSystemMessage(Component.literal(message));
        }
    }

    private static void printRestartInfo(CommandContext<CommandSourceStack> context) {
        send(context, "For this configuration to take effect, restart the server");
    }
}
