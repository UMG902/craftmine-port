package dev.craftmine;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Craftmine implements ModInitializer {
        public static final String MOD_ID = "craftmine";
        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

        /** One-shot diagnostic flag: Player Unlocks screen was opened via the keybind. */
        public static boolean craftmineLoggedKeyOpen = false;

        @Override
        public void onInitialize() {
                LOGGER.info("Craftmine initializing...");
                // All registry bootstrap happens in CraftmineBootstrap.bootstrapAll(),
                // invoked from BuiltInRegistriesMixin during the vanilla bootstrap
                // window — the entrypoint runs after the registries are frozen.
                LOGGER.info("Craftmine initialized.");
        }
}
