package de.michiruf.serverportals.versioned;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Ruf
 * @since 2023-12-13
 */
public class VersionedMessageSender {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerPortals");

    public static void send(CommandContext<ServerCommandSource> context, String text) {
        try {
            var source = context.getSource();
            if (source == null) {
                LOGGER.error("[VersionedMessageSender] CommandSource is null, cannot send message");
                return;
            }
            
            var player = source.getPlayer();
            if (player != null) {
                player.sendMessage(Text.literal(text));
            } else {
                source.sendMessage(Text.literal(text));
            }
        } catch (Exception e) {
            LOGGER.error("[VersionedMessageSender] Error sending message: {}", text, e);
        }
    }
}
