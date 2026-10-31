package dev.craftmine.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates an officially installed 25w14craftmine snapshot jar.
 *
 * <p>Asset model ("bring your own files", like OpenRCT2 / the SM64 port): the
 * mod never ships, extracts or downloads Mojang creative content. Instead it
 * registers the user's own snapshot installation as a runtime resource pack -
 * textures, models, blockstates, shaders and texts load straight from
 * Mojang's official file at its official location.
 *
 * <p>Search order (first hit wins):
 * <ol>
 * <li>the running game directory and up to two parent levels - covers the
 * standard {@code .minecraft} layout as well as
 * {@code .../instances/<current instance>} layouts going up to the launcher
 * root's {@code versions/} folder;</li>
 * <li>on Windows, every direct subdirectory of {@code %APPDATA%} (Roaming) -
 * launchers install there as {@code .minecraft}, {@code .lunarclient},
 * {@code .tlauncher}, {@code PrismLauncher}, ...; from each of them we go into
 * {@code versions/25w14craftmine};</li>
 * <li>Linux ({@code ~/.minecraft}, {@code ~/.local/share/*}) and macOS
 * ({@code ~/Library/Application Support/*}) equivalents.</li>
 * </ol>
 *
 * <p>A candidate is accepted only if it is a regular file whose zip central
 * directory contains {@code assets/minecraft/textures/gui/title/minecraft.png}
 * - the CRAFTMINE title logo, which only exists in the real snapshot jar.
 */
public final class CraftmineSnapshotLocator {
    public static final String VERSION_DIR = "25w14craftmine";
    public static final String VERSION_JAR = "25w14craftmine.jar";
    /** Marker entry: the snapshot replaced the vanilla title logo with CRAFTMINE art. */
    private static final String LOGO_ENTRY = "assets/minecraft/textures/gui/title/minecraft.png";

    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/snapshot");

    private static volatile boolean searched;
    private static Path cachedResult;

    private CraftmineSnapshotLocator() {
    }

    /**
     * Returns the located snapshot jar, or {@code null} if none was found.
     * The search runs once per process and is cached (cheap either way - it
     * is a handful of file-existence checks plus one zip central-directory
     * read for the accepted candidate).
     */
    public static Path locate() {
        if (searched) {
            return cachedResult;
        }
        synchronized (CraftmineSnapshotLocator.class) {
            if (searched) {
                return cachedResult;
            }
            cachedResult = search();
            searched = true;
            if (cachedResult != null) {
                LOGGER.info("Craftmine: using installed 25w14craftmine snapshot for game assets: {}", cachedResult);
            } else {
                LOGGER.warn("Craftmine: 25w14craftmine snapshot jar not found - the CRAFTMINE title logo, "
                        + "snapshot splashes and some sounds will be missing. Install the 25w14craftmine "
                        + "snapshot once in your launcher (any instance of it creates versions/25w14craftmine), "
                        + "then restart.");
            }
            return cachedResult;
        }
    }

    /** True once {@link #locate()} has completed a search without a hit. */
    public static boolean isSnapshotMissing() {
        locate();
        return cachedResult == null;
    }

    private static Path search() {
        // 1) Runtime game directory, then its parent and grandparent
        //    (.../instances/<current instance> -> .../versions/25w14craftmine).
        for (Path gameDir : gameDirCandidates()) {
            Path found = candidateUnder(gameDir);
            if (found != null) {
                return found;
            }
        }
        // 2) Windows: %APPDATA%/<launcher or minecraft> -> versions/25w14craftmine.
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path found = scanSiblings(Paths.get(appData));
            if (found != null) {
                return found;
            }
        }
        // 3) Linux + macOS standard locations.
        String home = System.getProperty("user.home", ".");
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                Path found = scanSiblings(Paths.get(localAppData));
                if (found != null) {
                    return found;
                }
            }
        } else {
            Path found = scanSiblings(Paths.get(home, ".minecraft"));
            if (found != null) {
                return found;
            }
            found = scanSiblings(Paths.get(home, ".local", "share"));
            if (found != null) {
                return found;
            }
            found = scanSiblings(Paths.get(home, "Library", "Application Support"));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Path> gameDirCandidates() {
        Path gameDir = configuredGameDir();
        if (gameDir == null) {
            return List.of();
        }
        Path parent = gameDir.getParent();
        Path grandParent = parent == null ? null : parent.getParent();
        if (grandParent == null) {
            return List.of(gameDir, parent);
        }
        return List.of(gameDir, parent, grandParent);
    }

    private static Path configuredGameDir() {
        // Test hook: lets the standalone harness exercise the resolution
        // without a booted FabricLoader.
        String override = System.getProperty("craftmine.gameDir");
        if (override != null) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        try {
            return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Checks {@code root/versions/25w14craftmine/25w14craftmine.jar}. The
     * game dir itself, instance dirs and launcher roots all share this shape.
     */
    private static Path candidateUnder(Path root) {
        if (root == null) {
            return null;
        }
        Path jar = root.resolve("versions").resolve(VERSION_DIR).resolve(VERSION_JAR);
        if (looksLikeSnapshot(jar)) {
            return jar;
        }
        return null;
    }

    /** Scans every direct subdirectory of {@code root} as a launcher data dir. */
    private static Path scanSiblings(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> children = Files.list(root)) {
            List<Path> dirs = children.filter(Files::isDirectory).toList();
            for (Path dir : dirs) {
                Path found = candidateUnder(dir);
                if (found != null) {
                    return found;
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Craftmine: could not scan {} for launcher installs", root, e);
        }
        return null;
    }

    private static boolean looksLikeSnapshot(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return false;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(LOGO_ENTRY) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
