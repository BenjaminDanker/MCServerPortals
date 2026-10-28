package de.michiruf.serverportals.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;

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
    /** When true, any player may light this portal (with its item, or programmatically); otherwise lighting needs portal.use. */
    private boolean playerIgnite;

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
        return BuiltInRegistries.BLOCK.getValue(Identifier.tryParse(frameBlockId));
    }

    public Item lightWithItem() {
        return BuiltInRegistries.ITEM.getValue(Identifier.tryParse(lightWithItemId));
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

    public boolean playerIgnite() {
        return playerIgnite;
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
        private String world;
        private double x;
        private double y;
        private double z;
        /** Which way an arriving player faces (degrees; 0 = south, 90 = west, 180 = north, -90 = east); absent keeps theirs. */
        private Float yaw;
        /** Up/down look of an arriving player (degrees, -90 up to 90 down); absent keeps theirs. */
        private Float pitch;

        @SuppressWarnings("unused")
        private ArrivalLocation() {
        }

        public ArrivalLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public ArrivalLocation(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Float yaw() {
            return yaw;
        }

        public Float pitch() {
            return pitch;
        }

        public String world() {
            return world;
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
                    "world=" + world + ", " +
                    "x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    '}';
        }
    }
}
