package de.michiruf.serverportals.config;

/**
 * Represents the destination server for a portal.
 * When a player enters this portal, they will be teleported to the named server.
 * The arrival coordinates on that server are specified separately in the PortalRegistrationData.
 * 
 * Example: A portal on vanilla1 with destination "vanilla2" will teleport the player to vanilla2 server.
 * 
 * @author Michael Ruf
 * @since 2025-11-07
 */
public final class PortalDestination {
    private String server;

    @SuppressWarnings("unused")
    // Suppress, because this will be used for serialization
    private PortalDestination() {
    }

    /**
     * @param server the target server name (e.g., "vanilla1", "vanilla2")
     */
    public PortalDestination(String server) {
        this.server = server;
    }

    /**
     * Get the destination server name.
     * @return server name
     */
    public String server() {
        return server;
    }

    @Override
    public String toString() {
        return "PortalDestination{server='" + server + "'}";
    }
}
