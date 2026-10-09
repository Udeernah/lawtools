package com.lawtools;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /freeze: walk speed and jump power of team-law bots go to 0 (mouse/perspective is untouched).
 * /unfreeze true|false: walking comes back; jumping comes back only with true.
 */
final class Freeze {
	static volatile boolean moveFrozen, jumpBlocked;

	/** original {speed, jump} per bot */
	private static final Map<UUID, double[]> ORIG = new HashMap<>();

	static void tick(MinecraftServer s) {
		boolean lockMove = moveFrozen;
		boolean lockJump = moveFrozen || jumpBlocked;
		if (!lockMove && !lockJump && ORIG.isEmpty()) return;

		for (ServerPlayerEntity p : Law.bots(s)) apply(p, lockMove, lockJump);
		if (!lockMove && !lockJump) ORIG.clear();                // everything restored
	}

	private static void apply(ServerPlayerEntity p, boolean lockMove, boolean lockJump) {
		EntityAttributeInstance sp = p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
		EntityAttributeInstance jp = p.getAttributeInstance(EntityAttributes.JUMP_STRENGTH);
		if (sp == null || jp == null) return;

		double[] o = ORIG.get(p.getUuid());
		if (o == null) {
			if (!lockMove && !lockJump) return;
			o = new double[]{sp.getBaseValue(), jp.getBaseValue()};
			ORIG.put(p.getUuid(), o);
		}
		double wantSpeed = lockMove ? 0.0 : o[0];
		double wantJump = lockJump ? 0.0 : o[1];
		if (sp.getBaseValue() != wantSpeed) sp.setBaseValue(wantSpeed);
		if (jp.getBaseValue() != wantJump) jp.setBaseValue(wantJump);
	}
}
