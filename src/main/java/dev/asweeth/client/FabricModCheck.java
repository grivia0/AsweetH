package dev.asweeth.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.asweeth.AsweetH;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Warns about Fabric mods sitting in the mods folder.
 *
 * <p>Forge silently ignores a jar it has no {@code mods.toml} for, so a player who drops Sodium or a
 * Fabric-only library into {@code mods/} only notices once they are in game and the feature is
 * missing. The one way to run Fabric mods next to a Forge-lineage mod is Sinytra Connector, which is a
 * <em>NeoForge</em> compatibility layer with no build for Minecraft 26.3 at all, so the useful thing to
 * do is say that clearly in the log at startup instead of pretending it can work.</p>
 *
 * <p>The scan only reads zip directories and a few kilobytes of metadata per jar, and it is capped, so
 * it cannot stall a launch even with a pathological mods folder.</p>
 */
public final class FabricModCheck {
    /** Marker entry every Fabric mod ships; Forge has no use for it. */
    private static final String MARKER = "fabric.mod.json";
    /** Fabric metadata is tiny - anything bigger is not worth parsing. */
    private static final long MAX_METADATA_BYTES = 64L * 1024L;
    /** Upper bound on jars inspected, so a huge mods folder never delays the launch. */
    private static final int MAX_JARS = 512;

    private static final String ADVICE =
            "Fabric mods need the Sinytra Connector compatibility layer, which only exists for NeoForge"
                    + " (Minecraft 26.2.x / 26.1.x); remove those jars or run ASweetH on NeoForge 26.2 with"
                    + " Connector + Forgified Fabric API";
    private static final String DOCS = "https://github.com/Sinytra/Connector";

    private static final Logger LOGGER = LogUtils.getLogger();

    private FabricModCheck() {
    }

    /** Logs one line per Fabric jar in the mods folder, plus a summary. Never throws. */
    public static void warnIfPresent() {
        Path mods;
        try {
            mods = FMLPaths.MODSDIR.get();
        } catch (RuntimeException exception) {
            LOGGER.debug("Mods directory is not available yet", exception);
            return;
        }
        List<String> fabricMods = scan(mods);
        if (fabricMods.isEmpty()) {
            return;
        }
        LOGGER.warn("{} found {} Fabric mod{} in {} that Forge cannot load:", AsweetH.CLIENT_NAME,
                fabricMods.size(), fabricMods.size() == 1 ? "" : "s", mods);
        for (String description : fabricMods) {
            LOGGER.warn("  - {}", description);
        }
        LOGGER.warn("{} - {}", ADVICE, DOCS);
    }

    /**
     * Lists the jars in {@code modsDirectory} that carry a {@code fabric.mod.json}, as readable
     * descriptions. Package visible so the check can be exercised without a running game.
     */
    static List<String> scan(Path modsDirectory) {
        List<String> found = new ArrayList<>();
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return found;
        }
        List<Path> jars;
        try (Stream<Path> entries = Files.list(modsDirectory)) {
            jars = entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .limit(MAX_JARS)
                    .toList();
        } catch (IOException exception) {
            LOGGER.debug("Could not list {}", modsDirectory, exception);
            return found;
        }
        for (Path jar : jars) {
            String description = describe(jar);
            if (description != null) {
                found.add(description);
            }
        }
        return found;
    }

    /** Returns a label for {@code jar} when it is a Fabric mod, or {@code null} when it is not one. */
    private static String describe(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(MARKER);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            String file = jar.getFileName().toString();
            JsonObject metadata = readMetadata(zip, entry);
            if (metadata == null) {
                // The marker exists but is unreadable, which still means "a Fabric jar FML will skip".
                return file;
            }
            String id = string(metadata, "id");
            String name = string(metadata, "name");
            String version = string(metadata, "version");
            StringBuilder label = new StringBuilder(name != null ? name : id != null ? id : file);
            if (version != null) {
                label.append(' ').append(version);
            }
            label.append(" (").append(file);
            if (id != null) {
                label.append(", id ").append(id);
            }
            return label.append(')').toString();
        } catch (IOException | RuntimeException exception) {
            // Unreadable jars and plain files are not Fabric mods either; nothing to report.
            LOGGER.debug("Could not inspect {} for Fabric metadata", jar, exception);
            return null;
        }
    }

    /** Parses {@code fabric.mod.json}, or returns {@code null} when it is absent, huge or malformed. */
    private static JsonObject readMetadata(ZipFile zip, ZipEntry entry) {
        long size = entry.getSize();
        if (size < 0L || size > MAX_METADATA_BYTES) {
            return null;
        }
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
