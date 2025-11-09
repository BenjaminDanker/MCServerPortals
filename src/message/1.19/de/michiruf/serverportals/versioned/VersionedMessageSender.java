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
            
            var serverPlayerEntity = source.getPlayer();
            if (serverPlayerEntity != null) {
                serverPlayerEntity.sendMessage(Text.literal(text), false);
            } else {
                source.sendFeedback(Text.literal(text), false);
            }
        } catch (Exception e) {
            LOGGER.error("[VersionedMessageSender] Error sending message: {}", text, e);
        }
    }
}
