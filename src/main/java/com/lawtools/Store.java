package com.lawtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Unlocked morphs and the currently active morph per player, saved to config/lawtools-morphs.json */
final class Store {
	static final class Data {
		Map<String, Set<String>> unlocked = new HashMap<>();
		Map<String, String> active = new HashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static Data data = new Data();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("lawtools-morphs.json");
	}

	static void load() {
		try {
			if (Files.exists(file())) {
				Data d = GSON.fromJson(Files.readString(file()), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) {
			LawTools.LOG.warn("[LawTools] could not read morph data: {}", e.toString());
		}
		if (data.unlocked == null) data.unlocked = new HashMap<>();
		if (data.active == null) data.active = new HashMap<>();
	}

	static void save() {
		try {
			Files.writeString(file(), GSON.toJson(data));
		} catch (IOException e) {
			LawTools.LOG.warn("[LawTools] could not save morph data: {}", e.toString());
		}
	}

	static boolean has(UUID p, String id) {
		Set<String> s = data.unlocked.get(p.toString());
		return s != null && s.contains(id);
	}

	static int count(UUID p) {
		Set<String> s = data.unlocked.get(p.toString());
		return s == null ? 0 : s.size();
	}

	static void unlock(UUID p, String id) {
		data.unlocked.computeIfAbsent(p.toString(), k -> new LinkedHashSet<>()).add(id);
		save();
	}

	static String active(UUID p) {
		return data.active.get(p.toString());
	}

	static void setActive(UUID p, String id) {
		if (id == null) data.active.remove(p.toString());
		else data.active.put(p.toString(), id);
		save();
	}
}
