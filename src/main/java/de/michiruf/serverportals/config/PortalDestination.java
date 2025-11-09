package de.michiruf.serverportals.config;

/**
 * Represents the destination coordinates for a portal on another server
 * @author Michael Ruf
 * @since 2025-11-07
 */
public final class PortalDestination {
    private String server;
    private double x;
    private double y;
    private double z;

    @SuppressWarnings("unused")
    // Suppress, because this will be used for serialization
    private PortalDestination() {
    }

    public PortalDestination(String server, double x, double y, double z) {
        this.server = server;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String server() {
        return server;
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
        return "PortalDestination{" +
                "server='" + server + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}
