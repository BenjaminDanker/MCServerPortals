package de.michiruf.serverportals.config;

import de.michiruf.serverportals.versioned.VersionedRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * @author Michael Ruf
 * @since 2022-12-19
 */
public final class PortalRegistrationData {

    private String index;
    private String frameBlockId;
    private String lightWithItemId;
    private int color;
    private String command;
    private String destinationPortalName;
    private PortalDestination destination;

    @SuppressWarnings("unused")
    // Suppress, because this will be used for serialization
    private PortalRegistrationData() {
    }

    public PortalRegistrationData(
            String index,
            String frameBlockId,
            String lightWithItemId,
            int color,
            String command) {
        this(index, frameBlockId, lightWithItemId, color, command, null, null);
    }

    public PortalRegistrationData(
            String index,
            String frameBlockId,
            String lightWithItemId,
            int color,
            String command,
            String destinationPortalName,
            PortalDestination destination) {
        this.index = index;
        this.frameBlockId = frameBlockId;
        this.lightWithItemId = lightWithItemId;
        this.color = color;
        this.command = command;
        this.destinationPortalName = destinationPortalName;
        this.destination = destination;
    }

    public Block frameBlock() {
        return VersionedRegistry.block().get(Identifier.tryParse(frameBlockId));
    }

    public Item lightWithItem() {
        return VersionedRegistry.item().get(Identifier.tryParse(lightWithItemId));
    }

    public String index() {
        return index;
    }

    public int color() {
        return color;
    }

    public String command() {
        return command;
    }

    public String destinationPortalName() {
        return destinationPortalName;
    }

    public PortalDestination destination() {
        return destination;
    }

    @Override
    public String toString() {
        return "[" +
                "index=" + index + ", " +
                "frameBlockId=" + frameBlockId + ", " +
                "lightWithItemId=" + lightWithItemId + ", " +
                "color=" + color + ", " +
                "command=" + command + ", " +
                "destinationPortalName=" + destinationPortalName + ']';
    }
}
