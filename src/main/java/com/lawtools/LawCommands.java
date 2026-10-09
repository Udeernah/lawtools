package com.lawtools;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

final class LawCommands {
	private static Predicate<ServerCommandSource> perm() {
		return CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);
	}

	/** compass direction -> {yaw, pitch} */
	private static final Map<String, float[]> DIRS = Map.of(
			"south", new float[]{0f, 0f}, "west", new float[]{90f, 0f}, "north", new float[]{180f, 0f},
			"east", new float[]{-90f, 0f}, "up", new float[]{0f, -90f}, "down", new float[]{0f, 90f});

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			Predicate<ServerCommandSource> perm = perm();

			// ---------------------------------------------------------------- /freeze, /unfreeze <jump>
			dispatcher.register(literal("freeze").requires(perm).executes(c -> {
				Freeze.moveFrozen = true;
				int n = Law.bots(c.getSource().getServer()).size();
				c.getSource().sendFeedback(() -> Text.literal("Froze " + n + " law bots (no walking, no jumping; they can still look around)"), true);
				return n;
			}));
			dispatcher.register(literal("unfreeze").requires(perm)
					.then(argument("jump", BoolArgumentType.bool()).executes(c -> {
						boolean jump = BoolArgumentType.getBool(c, "jump");
						Freeze.moveFrozen = false;
						Freeze.jumpBlocked = !jump;
						c.getSource().sendFeedback(() -> Text.literal("Unfroze law bots. Jumping " + (jump ? "enabled" : "still disabled")), true);
						return 1;
					})));

			// ---------------------------------------------------------------- /lookat
			var look = literal("lookat").requires(perm)
					.then(literal("off").executes(c -> {
						LookAt.mode = LookAt.Mode.OFF;
						c.getSource().sendFeedback(() -> Text.literal("Law bots can look freely again"), true);
						return 1;
					}))
					.then(literal("angle")
							.then(argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
									.then(argument("pitch", FloatArgumentType.floatArg(-90f, 90f)).executes(c -> {
										setDirection(c.getSource(), FloatArgumentType.getFloat(c, "yaw"),
												FloatArgumentType.getFloat(c, "pitch"), "that angle");
										return 1;
									}))))
					.then(argument("target", EntityArgumentType.player()).executes(c -> {
						ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "target");
						LookAt.target = t.getName().getString();
						LookAt.mode = LookAt.Mode.PLAYER;
						int n = Law.bots(c.getSource().getServer()).size();
						c.getSource().sendFeedback(() -> Text.literal(n + " law bots now look at " + LookAt.target + " until /lookat off"), true);
						return n;
					}));
			for (Map.Entry<String, float[]> d : DIRS.entrySet()) {
				look = look.then(literal(d.getKey()).executes(c -> {
					setDirection(c.getSource(), d.getValue()[0], d.getValue()[1], d.getKey());
					return 1;
				}));
			}
			dispatcher.register(look);

			// ---------------------------------------------------------------- /morph
			dispatcher.register(literal("morph")
					.executes(c -> openMenu(c))
					.then(literal("off").executes(c -> {
						ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
						MorphEngine.unmorph(c.getSource().getServer(), p);
						return 1;
					}))
					.then(literal("craft")
							.then(argument("mob", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Morphs.available(), b))
									.executes(c -> craft(c))))
					.then(literal("fragments").requires(perm)
							.then(argument("mob", StringArgumentType.word())
									.suggests((c, b) -> CommandSource.suggestMatching(Morphs.available(), b))
									.then(argument("count", IntegerArgumentType.integer(1, 64)).executes(c -> fragments(c))))));
		});
	}

	private static void setDirection(ServerCommandSource src, float yaw, float pitch, String label) {
		LookAt.yaw = yaw;
		LookAt.pitch = pitch;
		LookAt.mode = LookAt.Mode.DIRECTION;
		int n = Law.bots(src.getServer()).size();
		src.sendFeedback(() -> Text.literal(n + " law bots now look " + label + " until /lookat off"), true);
	}

	private static int openMenu(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource src = c.getSource();
		ServerPlayerEntity p = src.getPlayerOrThrow();
		final boolean op = perm().test(src);                      // operators can morph into anything
		p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inv, player) -> new MorphMenu(syncId, inv, new SimpleInventory(54), p, op),
				Text.literal("Morph")));
		return 1;
	}

	private static int craft(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		String id = StringArgumentType.getString(c, "mob");
		if (!Morphs.exists(id)) {
			c.getSource().sendError(Text.literal("Unknown mob: " + id));
			return 0;
		}
		PlayerInventory inv = p.getInventory();
		int have = 0;
		for (int i = 0; i < inv.size(); i++) if (id.equals(Items2.read(inv.getStack(i), Items2.FRAGMENT))) have += inv.getStack(i).getCount();
		if (have < 9) {
			c.getSource().sendError(Text.literal("You need 9 " + Morphs.title(id) + " fragments (you have " + have + ")."));
			return 0;
		}
		int left = 9;
		for (int i = 0; i < inv.size() && left > 0; i++) {
			ItemStack st = inv.getStack(i);
			if (id.equals(Items2.read(st, Items2.FRAGMENT))) {
				int take = Math.min(left, st.getCount());
				st.decrement(take);
				left -= take;
			}
		}
		inv.offerOrDrop(Items2.fruit(id));
		c.getSource().sendFeedback(() -> Text.literal("Crafted a " + Morphs.title(id) + " Morph Ability. Right-click it to unlock."), false);
		return 1;
	}

	private static int fragments(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		String id = StringArgumentType.getString(c, "mob");
		if (!Morphs.exists(id)) {
			c.getSource().sendError(Text.literal("Unknown mob: " + id));
			return 0;
		}
		int n = IntegerArgumentType.getInteger(c, "count");
		ItemStack st = Items2.fragment(id);
		st.setCount(n);
		p.getInventory().offerOrDrop(st);
		return n;
	}
}
