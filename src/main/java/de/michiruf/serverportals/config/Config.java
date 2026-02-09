package de.michiruf.serverportals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal config wrapper for ServerPortals.
 *
 * Uses strict JSON in <config>/server-portals.json.
 */
public final class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerPortals");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String CONFIG_FILE_NAME = "server-portals.json";

    private final Path configPath;

    private ConfigModel model;

    private Config(Path configPath) {
        this.configPath = configPath;
    }

    public static Config createAndLoad() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Config config = new Config(configDir.resolve(CONFIG_FILE_NAME));
        config.load();
        return config;
    }

    private void load() {
        if (Files.exists(configPath)) {
            this.model = readStrictJson(configPath);
            if (this.model == null) {
                this.model = createDefault();
            }
            return;
        }

        this.model = createDefault();
        save();
    }

    private static ConfigModel readStrictJson(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, ConfigModel.class);
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Failed to read config file {} (strict JSON), falling back to defaults", path, ex);
            return null;
        }
    }

    private static ConfigModel createDefault() {
        ConfigModel defaults = new ConfigModel();
        defaults.logLevel = 0;
        defaults.portals = new ArrayList<>();
        return defaults;
    }

    private static ConfigModel coerce(ConfigModel loaded) {
        if (loaded == null) {
            return createDefault();
        }
        if (loaded.portals == null) {
            loaded.portals = new ArrayList<>();
        }
        return loaded;
    }

    public void save() {
        this.model = coerce(this.model);
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(this.model, writer);
            }
        } catch (IOException ex) {
            LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public int logLevel() {
        return coerce(this.model).logLevel;
    }

    public void logLevel(int logLevel) {
        coerce(this.model).logLevel = logLevel;
    }

    public List<PortalRegistrationData> portals() {
        return coerce(this.model).portals;
    }

    public void portals(List<PortalRegistrationData> portals) {
        coerce(this.model).portals = portals;
    }

    public Path configPath() {
        return configPath;
    }
}
