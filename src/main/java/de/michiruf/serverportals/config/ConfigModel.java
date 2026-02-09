package de.michiruf.serverportals.config;

import java.util.List;

/**
 * @author Michael Ruf
 * @since 2022-12-02
 */
@SuppressWarnings("unused")
public class ConfigModel {

    public int logLevel; // TODO Use this
    public List<PortalRegistrationData> portals;

    /**
     * Shared secret used to sign backend -> proxy portal requests.
     * Must match the entry in WakeUpLobby's backend_portal_request_secrets for this server.
     */
    public String portalRequestSecret;
}
