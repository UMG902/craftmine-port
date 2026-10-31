package dev.craftmine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Craftmine config file: {@code config/craftmine.properties}.
 *
 * <p>Options:
 * <ul>
 *   <li>{@code unoriginal_features} (default {@code false}) - master switch for every feature that
 *   was NOT in the original 25w14craftmine snapshot. When {@code false} none of them exist: the
 *   QoL unlock tab is hidden, the extra unlocks are hidden, reroll/relock cannot be used, the
 *   unoriginal boss mines never roll. Every future non-original feature must be gated behind
 *   {@link #unoriginalFeatures()}.</li>
 *   <li>Per-feature sub-toggles, all default {@code true}, each controlling exactly one unoriginal
 *   feature. They only have an effect while the master switch is on - a sub-toggle can never
 *   switch a feature on by itself:
 *     <ul>
 *       <li>{@code unoriginal_guardian_boss} - the elder guardian boss mine ("The Elder One");</li>
 *       <li>{@code unoriginal_breeze_boss} - the breeze boss mine ("The Breezy One");</li>
 *       <li>{@code unoriginal_relock_tree} - the relock feature (ctrl+click an unlock to take it back);</li>
 *       <li>{@code unoriginal_reroll_tree} - the reroll button in the minecrafter;</li>
 *       <li>{@code unoriginal_extra_unlocks} - the extra unlocks added to the normal unlock tabs.</li>
 *     </ul></li>
 * </ul>
 *
 * <p>The file is read once, lazily, on first access (client and server share the JVM in
 * singleplayer, so both sides always agree). Edit the file and restart the game to change it.
 */
public final class CraftmineConfig {
    private static final String KEY_UNORIGINAL = "unoriginal_features";
    private static final String KEY_GUARDIAN_BOSS = "unoriginal_guardian_boss";
    private static final String KEY_BREEZE_BOSS = "unoriginal_breeze_boss";
    private static final String KEY_RELOCK_TREE = "unoriginal_relock_tree";
    private static final String KEY_REROLL_TREE = "unoriginal_reroll_tree";
    private static final String KEY_EXTRA_UNLOCKS = "unoriginal_extra_unlocks";

    /** Sub-feature scope for unoriginal unlock nodes: the relock branch of the QoL tab. */
    public static final String FEATURE_RELOCK_TREE = "relock_tree";
    /** Sub-feature scope for unoriginal unlock nodes: the reroll branch of the QoL tab. */
    public static final String FEATURE_REROLL_TREE = "reroll_tree";
    /** Sub-feature scope for unoriginal unlock nodes: extra unlocks on the normal tabs. */
    public static final String FEATURE_EXTRA_UNLOCKS = "extra_unlocks";

    private static volatile boolean loaded;
    private static volatile boolean unoriginalFeatures;
    private static volatile boolean guardianBoss = true;
    private static volatile boolean breezeBoss = true;
    private static volatile boolean relockTree = true;
    private static volatile boolean rerollTree = true;
    private static volatile boolean extraUnlocks = true;

    private CraftmineConfig() {
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    /** True when the "unoriginal features" master switch is on. Off by default. */
    public static boolean unoriginalFeatures() {
        ensureLoaded();
        return unoriginalFeatures;
    }

    /** Master switch AND the guardian-boss sub-toggle. */
    public static boolean guardianBoss() {
        ensureLoaded();
        return unoriginalFeatures && guardianBoss;
    }

    /** Master switch AND the breeze-boss sub-toggle. */
    public static boolean breezeBoss() {
        ensureLoaded();
        return unoriginalFeatures && breezeBoss;
    }

    /** Master switch AND the relock sub-toggle. */
    public static boolean relockTree() {
        ensureLoaded();
        return unoriginalFeatures && relockTree;
    }

    /** Master switch AND the reroll sub-toggle. */
    public static boolean rerollTree() {
        ensureLoaded();
        return unoriginalFeatures && rerollTree;
    }

    /** Master switch AND the extra-unlocks sub-toggle. */
    public static boolean extraUnlocks() {
        ensureLoaded();
        return unoriginalFeatures && extraUnlocks;
    }

    /**
     * Query-time gate for unoriginal unlock nodes: {@code feature} is {@code null} for
     * master-only nodes, otherwise the named sub-toggle must also be on (and the master
     * switch, as always). Fail-closed: an unknown feature string keeps the node off -
     * new scopes must be added here (and as a config key) explicitly.
     */
    public static boolean unoriginalFeatureAllowed(String feature) {
        if (!unoriginalFeatures()) {
            return false;
        }
        if (feature == null) {
            return true;
        }
        return switch (feature) {
            case FEATURE_RELOCK_TREE -> relockTree;
            case FEATURE_REROLL_TREE -> rerollTree;
            case FEATURE_EXTRA_UNLOCKS -> extraUnlocks;
            case "guardian_boss" -> guardianBoss;
            case "breeze_boss" -> breezeBoss;
            default -> false;
        };
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        Path path = FabricLoader.getInstance().getConfigDir().resolve("craftmine.properties");
        Properties properties = new Properties();
        boolean rewrite = true;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
                rewrite = !properties.containsKey(KEY_UNORIGINAL) || !properties.containsKey(KEY_GUARDIAN_BOSS)
                    || !properties.containsKey(KEY_BREEZE_BOSS) || !properties.containsKey(KEY_RELOCK_TREE)
                    || !properties.containsKey(KEY_REROLL_TREE) || !properties.containsKey(KEY_EXTRA_UNLOCKS);
            } catch (IOException | IllegalArgumentException e) {
                Craftmine.LOGGER.error("Craftmine: could not read {}, using defaults", path, e);
            }
        }
        unoriginalFeatures = Boolean.parseBoolean(properties.getProperty(KEY_UNORIGINAL, "false").trim());
        guardianBoss = parseFeature(properties, KEY_GUARDIAN_BOSS);
        breezeBoss = parseFeature(properties, KEY_BREEZE_BOSS);
        relockTree = parseFeature(properties, KEY_RELOCK_TREE);
        rerollTree = parseFeature(properties, KEY_REROLL_TREE);
        extraUnlocks = parseFeature(properties, KEY_EXTRA_UNLOCKS);
        if (rewrite) {
            properties.setProperty(KEY_UNORIGINAL, Boolean.toString(unoriginalFeatures));
            properties.setProperty(KEY_GUARDIAN_BOSS, Boolean.toString(guardianBoss));
            properties.setProperty(KEY_BREEZE_BOSS, Boolean.toString(breezeBoss));
            properties.setProperty(KEY_RELOCK_TREE, Boolean.toString(relockTree));
            properties.setProperty(KEY_REROLL_TREE, Boolean.toString(rerollTree));
            properties.setProperty(KEY_EXTRA_UNLOCKS, Boolean.toString(extraUnlocks));
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Craftmine config\n"
                        + "unoriginal_features: master switch for every feature that was not in the original\n"
                        + "25w14craftmine snapshot (QoL unlock tab, reroll, relock, extra unlocks, boss mines).\n"
                        + "false = off (default), true = the per-feature toggles below take effect.\n"
                        + "unoriginal_<feature>: per-feature toggle, default true, each controls exactly one\n"
                        + "feature and only has an effect while the master switch is on.\n"
                        + "Restart the game after changing anything.");
                }
            } catch (IOException e) {
                Craftmine.LOGGER.error("Craftmine: could not write {}", path, e);
            }
        }
        Craftmine.LOGGER.info("Craftmine: unoriginal_features = {} (guardian_boss {}, breeze_boss {}, relock_tree {}, reroll_tree {}, extra_unlocks {})",
            unoriginalFeatures, guardianBoss, breezeBoss, relockTree, rerollTree, extraUnlocks);
        loaded = true;
    }

    private static boolean parseFeature(Properties properties, String key) {
        return Boolean.parseBoolean(properties.getProperty(key, "true").trim());
    }
}
