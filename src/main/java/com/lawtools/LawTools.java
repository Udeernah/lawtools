package com.lawtools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class LawTools implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("lawtools");
	static volatile MinecraftServer SERVER;

	private static final Identifier LATE = Identifier.of("lawtools", "late");
	private static final Random RNG = new Random();

	@Override
	public void onInitialize() {
		Config.load();
		LawCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			SERVER = s;
			Morphs.init();
			Store.load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> MorphEngine.removeAll());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> SERVER = null);

		// run after every other mod's tick work so freeze / lookat / morph have the last word
		ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerTickEvents.END_SERVER_TICK.register(LATE, s -> {
			Freeze.tick(s);
			LookAt.tick(s);
			MorphEngine.tick(s);
		});

		// leftover mob copies (e.g. after a crash) are removed when their chunk loads
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getCommandTags().contains(MorphEngine.TAG) && !MorphEngine.OWNER_OF.containsKey(entity.getUuid())) {
				MinecraftServer s = SERVER;
				if (s != null) s.execute(entity::discard);
			}
		});

		// hitting a morphed player's mob copy hits the player
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient() || !entity.getCommandTags().contains(MorphEngine.TAG)) return ActionResult.PASS;
			MinecraftServer s = SERVER;
			ServerPlayerEntity owner = s == null ? null : MorphEngine.ownerOf(s, entity);
			if (owner != null && owner != player) player.attack(owner);
			return ActionResult.SUCCESS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!entity.getCommandTags().contains(MorphEngine.TAG)) return true;
			MinecraftServer s = SERVER;
			ServerPlayerEntity owner = s == null ? null : MorphEngine.ownerOf(s, entity);
			ServerWorld w = owner == null ? null : Law.worldOf(s, owner);
			if (owner != null && w != null) owner.damage(w, source, amount);   // arrows, explosions, ...
			return false;
		});

		// mob drops: rarely a fragment of that mob
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getAttacker() instanceof ServerPlayerEntity)) return;
			if (entity.getCommandTags().contains(MorphEngine.TAG)) return;
			String id = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
			if (!Morphs.exists(id) || RNG.nextDouble() >= Config.dropChance) return;
			MinecraftServer s = SERVER;
			ServerWorld w = s == null ? null : Law.worldOf(s, entity);
			if (w == null) return;
			w.spawnEntity(new ItemEntity(w, entity.getX(), entity.getY() + 0.3, entity.getZ(), Items2.fragment(id)));
		});

		// right-click a Morph Ability fruit: unlock that morph for good
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient()) return ActionResult.PASS;
			ItemStack st = player.getStackInHand(hand);
			String id = Items2.read(st, Items2.FRUIT);
			if (id == null || !Morphs.exists(id)) return ActionResult.PASS;
			if (Store.has(player.getUuid(), id)) {
				player.sendMessage(Text.literal("You already unlocked " + Morphs.title(id) + "."), true);
				return ActionResult.FAIL;
			}
			Store.unlock(player.getUuid(), id);
			st.decrement(1);
			player.sendMessage(Text.literal("Unlocked morph: " + Morphs.title(id) + "! Use /morph"), false);
			return ActionResult.SUCCESS;
		});
	}
}
