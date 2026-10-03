package dev.phntm.hytweaks.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phntm.hytweaks.HyTweaks;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config.json}: one object per feature. Missing keys are filled with their defaults
 * on first read and written back, so the file always documents every option.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final JsonObject root;

    private Config(Path file, JsonObject root) {
        this.file = file;
        this.root = root;
    }

    @Nonnull
    public static Config load(@Nonnull Path file) {
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(file)) {
            try {
                root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                HyTweaks.LOG.atWarning().withCause(e).log("Unreadable %s; using defaults", file);
            }
        }
        return new Config(file, root);
    }

    @Nonnull
    public Section section(@Nonnull String id) {
        if (root.get(id) instanceof JsonObject existing) {
            return new Section(existing);
        }
        JsonObject created = new JsonObject();
        root.add(id, created);
        return new Section(created);
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            HyTweaks.LOG.atWarning().withCause(e).log("Could not write %s", file);
        }
    }

    public record Section(JsonObject json) {
        public boolean bool(@Nonnull String key, boolean fallback) {
            if (!json.has(key)) {
                json.addProperty(key, fallback);
            }
            return json.get(key).getAsBoolean();
        }

        @Nonnull
        public String string(@Nonnull String key, @Nonnull String fallback) {
            if (!json.has(key)) {
                json.addProperty(key, fallback);
            }
            return json.get(key).getAsString();
        }

        public double number(@Nonnull String key, double fallback) {
            if (!json.has(key)) {
                json.addProperty(key, fallback);
            }
            return json.get(key).getAsDouble();
        }
    }
}
