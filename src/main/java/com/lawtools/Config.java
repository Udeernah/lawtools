package com.lawtools;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** config/lawtools.properties */
final class Config {
	/** chance (0..1) that a killed mob drops its morph fragment */
	static double dropChance = 0.05;

	static void load() {
		Path f = FabricLoader.getInstance().getConfigDir().resolve("lawtools.properties");
		try {
			if (!Files.exists(f)) {
				Files.write(f, List.of("# chance (0.0 - 1.0) that a killed mob drops its morph fragment", "dropChance=0.05"));
			}
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(f)) {
				p.load(r);
			}
			dropChance = Math.max(0, Math.min(1, Double.parseDouble(p.getProperty("dropChance", "0.05").trim())));
		} catch (IOException | NumberFormatException e) {
			LawTools.LOG.warn("[LawTools] could not read lawtools.properties, using defaults: {}", e.toString());
		}
	}
}
