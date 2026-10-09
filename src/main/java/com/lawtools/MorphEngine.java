package com.lawtools;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side morph: the player is made invisible and a real, AI-less copy of the mob follows them
 * every tick. Hits on the copy are passed on to the player.
 */
final class MorphEngine {
	static final String TAG = "lawtools_morph";

	private static final class Copy {
		Entity entity;
		RegistryKey<World> dim;
		String type;
	}

	private static final Map<UUID, Copy> COPIES = new HashMap<>();
	/** copy uuid -> owner uuid */
	static final Map<UUID, UUID> OWNER_OF = new ConcurrentHashMap<>();
	private static long ticks;

	static void tick(MinecraftServer s) {
		ticks++;
		if (Store.data.active.isEmpty() && COPIES.isEmpty()) return;

		Set<UUID> wanted = new HashSet<>();
		for (Map.Entry<String, String> en : new ArrayList<>(Store.data.active.entrySet())) {
			UUID uuid = UUID.fromString(en.getKey());
			wanted.add(uuid);
			ServerPlayerEntity p = s.getPlayerManager().getPlayer(uuid);
			Copy c = COPIES.get(uuid);
			if (p == null || !p.isAlive() || p.isSpectator()) {
				if (c != null) remove(uuid);
				continue;
			}
			ServerWorld w = Law.worldOf(s, p);
			if (w == null) continue;

			if (c != null && (c.entity.isRemoved() || !c.dim.equals(w.getRegistryKey()) || !c.type.equals(en.getValue()))) {
				remove(uuid);
				c = null;
			}
			if (c == null) {
				c = spawn(w, p, en.getValue());
				if (c == null) continue;
				COPIES.put(uuid, c);
			}
			sync(c.entity, p);

			if (ticks % 10 == 0 && !p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false, false));
			}
		}
		for (UUID u : new ArrayList<>(COPIES.keySet())) if (!wanted.contains(u)) remove(u);
	}

	private static Copy spawn(ServerWorld w, ServerPlayerEntity p, String id) {
		Entity e = Morphs.type(id).create(w, SpawnReason.COMMAND);
		if (e == null) return null;
		OWNER_OF.put(e.getUuid(), p.getUuid());       // before spawning, so the load event keeps it
		e.addCommandTag(TAG);
		e.setInvulnerable(true);
		e.setNoGravity(true);
		e.noClip = true;
		if (e instanceof MobEntity m) m.setAiDisabled(true);
		e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
		w.spawnEntity(e);
		Copy c = new Copy();
		c.entity = e;
		c.dim = w.getRegistryKey();
		c.type = id;
		return c;
	}

	private static void sync(Entity e, ServerPlayerEntity p) {
		e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
		if (e instanceof LivingEntity le) {
			le.setHeadYaw(p.getHeadYaw());
			le.setBodyYaw(p.getBodyYaw());
		}
		if (e.isOnFire()) e.extinguish();
	}

	private static void remove(UUID owner) {
		Copy c = COPIES.remove(owner);
		if (c == null) return;
		OWNER_OF.remove(c.entity.getUuid());
		c.entity.discard();
	}

	static void removeAll() {
		for (UUID u : new ArrayList<>(COPIES.keySet())) remove(u);
	}

	// ---------------------------------------------------------------- API used by menu / commands

	static void morph(MinecraftServer s, ServerPlayerEntity p, String id) {
		Store.setActive(p.getUuid(), id);
		p.sendMessage(Text.literal("You morphed into " + Morphs.title(id) + ". /morph off to go back."), true);
	}

	static void unmorph(MinecraftServer s, ServerPlayerEntity p) {
		Store.setActive(p.getUuid(), null);
		remove(p.getUuid());
		p.removeStatusEffect(StatusEffects.INVISIBILITY);
		p.sendMessage(Text.literal("You are yourself again."), true);
	}

	static ServerPlayerEntity ownerOf(MinecraftServer s, Entity copy) {
		UUID o = OWNER_OF.get(copy.getUuid());
		return o == null ? null : s.getPlayerManager().getPlayer(o);
	}
}
