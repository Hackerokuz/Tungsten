package kaptainwutax.tungsten.client;

import kaptainwutax.tungsten.Tungsten;
import kaptainwutax.tungsten.client.commands.*;

public class TungstenCommands {

	public TungstenCommands(Tungsten mod) throws CommandException {
		Tungsten.getCommandExecutor().registerNewCommand(
				new ClickCommand(mod),
				new GotoCommand(mod),
				new StopCommand(mod),
				new SettingsCommand(mod)
		);
	}
}
