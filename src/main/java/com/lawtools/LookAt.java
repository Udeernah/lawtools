package com.lawtools;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** /lookat <direction|player|angle> and /lookat off - team-law bots keep looking there every tick. */
final class LookAt {
	enum Mode { OFF, DIRECTION, PLAYER }

	static volatile Mode mode = Mode.OFF;
	static volatile float yaw, pitch;
	static volatile String target;

	static void tick(MinecraftServer s) {
		if (mode == Mode.OFF) return;
		ServerPlayerEntity t = mode == Mode.PLAYER ? s.getPlayerManager().getPlayer(target) : null;
		for (ServerPlayerEntity p : Law.bots(s)) {
			float y = yaw, pt = pitch;
			if (mode == Mode.PLAYER) {
				if (t == null) continue;
				Vec3d e = p.getEyePos(), to = t.getEyePos();
				double dx = to.x - e.x, dy = to.y - e.y, dz = to.z - e.z;
				y = (float) Math.toDegrees(Math.atan2(-dx, dz));
				pt = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			}
			p.setYaw(y);
			p.setHeadYaw(y);
			p.setPitch(pt);
		}
	}
}
