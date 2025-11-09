package de.michiruf.serverportals.config;

import de.michiruf.serverportals.versioned.VersionedRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * Configuration data for a single portal on this server.
 * Contains the portal's visual/functional properties and destination information.
 * 
 * The x, y, z coordinates (if present in config) represent where players arrive
 * on the destination server when they use this portal.
 * 
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
    private String destinationServer;
    private ArrivalLocation arrivalLocation;

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
        this(index, frameBlockId, lightWithItemId, color, command, null, null, null);
    }

    public PortalRegistrationData(
            String index,
            String frameBlockId,
            String lightWithItemId,
            int color,
            String command,
            String destinationPortalName,
            String destinationServer,
            ArrivalLocation arrivalLocation) {
        this.index = index;
        this.frameBlockId = frameBlockId;
        this.lightWithItemId = lightWithItemId;
        this.color = color;
        this.command = command;
        this.destinationPortalName = destinationPortalName;
        this.destinationServer = destinationServer;
        this.arrivalLocation = arrivalLocation;
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

    public String destinationServer() {
        return destinationServer;
    }

    public ArrivalLocation arrivalLocation() {
        return arrivalLocation;
    }

    @Override
    public String toString() {
        return "[" +
                "index=" + index + ", " +
                "frameBlockId=" + frameBlockId + ", " +
                "lightWithItemId=" + lightWithItemId + ", " +
                "color=" + color + ", " +
                "command=" + command + ", " +
                "destinationPortalName=" + destinationPortalName + ", " +
                "destinationServer=" + destinationServer + ", " +
                "arrivalLocation=" + arrivalLocation + ']';
    }

    /**
     * Represents the arrival coordinates on the destination server.
     */
    public static final class ArrivalLocation {
        private double x;
        private double y;
        private double z;

        @SuppressWarnings("unused")
        private ArrivalLocation() {
        }

        public ArrivalLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        @Override
        public String toString() {
            return "ArrivalLocation{" +
                    "x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    '}';
        }
    }
}
