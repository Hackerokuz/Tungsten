package kaptainwutax.tungsten.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.TungstenMod;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.commandsystem.Command;
import kaptainwutax.tungsten.path.PathFinder;
import net.minecraft.command.CommandSource;

public class StopCommand extends Command {
	public StopCommand(TungstenMod mod) {
        super("stop", "Tell bot to stop", mod);
    }

	@Override
	public void build(LiteralArgumentBuilder<CommandSource> builder) {
		
		builder.executes(context -> {
	        try {
				
	        	if(TungstenModDataContainer.PATHFINDER.active.get() || TungstenModDataContainer.EXECUTOR.isRunning()) {
	        		TungstenModDataContainer.PATHFINDER.stop.set(true);
	        		TungstenModDataContainer.EXECUTOR.stop = true;
					Debug.logMessage("Stopped!");
	    		} else {
					Debug.logMessage("Nothing to stop.");
	    		}

			} catch (Exception e) {
				// TODO: handle exception
			}
			
			return SINGLE_SUCCESS;
		});
	}
}
