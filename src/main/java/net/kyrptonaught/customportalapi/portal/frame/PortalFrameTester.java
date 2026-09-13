package net.kyrptonaught.customportalapi.portal.frame;

/** Compatibility hook for mods that provide custom frame testers. */
public abstract class PortalFrameTester {
    @FunctionalInterface public interface PortalFrameTesterFactory {
        PortalFrameTester createInstanceOfPortalFrameTester();
    }
}
