package kaptainwutax.tungsten.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.commandsystem.Command;
import net.minecraft.commands.CommandSource;

public class StopCommand extends Command {
	public StopCommand(TungstenClient mod) {
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
