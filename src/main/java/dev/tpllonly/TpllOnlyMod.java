/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly;

import dev.tpllonly.command.TpllCommands;
import dev.tpllonly.config.TpllConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. The mod is entirely server-side logic, but it loads on the client too so that the
 * commands are available in singleplayer (where the integrated server runs it as well).
 *
 * <p>On a multiplayer server only the server needs this mod installed &mdash; plain clients don't
 * have to know about it, because the commands are registered server-side and every message the mod
 * sends uses vanilla chat components.
 */
public final class TpllOnlyMod implements ModInitializer {

	public static final String MOD_ID = "tpll-only";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// touch the config on startup so that a broken config file is reported in the log
		// immediately rather than on first use
		TpllConfig.get();

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> TpllCommands.register(dispatcher));

		LOGGER.info("Registered /tpll and /tpllcalc");
	}
}
