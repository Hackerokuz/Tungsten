package kaptainwutax.tungsten.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.commandsystem.Command;
import net.minecraft.command.CommandSource;

public class SettingsCommand extends Command {

	public SettingsCommand(TungstenMod mod) {
		super("settings", "Handles bot settings", mod);
	}

	@Override
	public void build(LiteralArgumentBuilder<CommandSource> builder) {
		
		builder.then(argument("ignoreFallDamage", BoolArgumentType.bool()).executes(context -> {
	        TungstenModDataContainer.ignoreFallDamage = BoolArgumentType.getBool(context, "ignoreFallDamage");
			
			return SINGLE_SUCCESS;
		}));
	}
}
