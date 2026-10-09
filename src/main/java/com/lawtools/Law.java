package com.lawtools;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

final class Law {
	static final String TEAM = "law";

	static boolean isCarpetBot(Entity e) {
		for (Class<?> c = e == null ? null : e.getClass(); c != null; c = c.getSuperclass()) {
			if (c.getName().equals("carpet.patches.EntityPlayerMPFake")) return true;
		}
		return false;
	}

	/** online Carpet bots of team law */
	static List<ServerPlayerEntity> bots(MinecraftServer s) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		Team t = s.getScoreboard().getTeam(TEAM);
		if (t == null) return out;
		for (String n : t.getPlayerList()) {
			ServerPlayerEntity p = s.getPlayerManager().getPlayer(n);
			if (p != null && isCarpetBot(p)) out.add(p);
		}
		return out;
	}

	/** the world an entity is currently in */
	static ServerWorld worldOf(MinecraftServer s, Entity e) {
		for (ServerWorld w : s.getWorlds()) {
			if (w.getEntity(e.getUuid()) == e) return w;
		}
		return null;
	}
}
