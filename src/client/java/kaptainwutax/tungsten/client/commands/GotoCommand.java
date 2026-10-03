package kaptainwutax.tungsten.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenClient;
import kaptainwutax.tungsten.client.TungstenModDataContainer;
import kaptainwutax.tungsten.client.commands.arguments.GotoTargetArgumentType;
import kaptainwutax.tungsten.client.commandsystem.Command;
import kaptainwutax.tungsten.client.commandsystem.CommandException;
import kaptainwutax.tungsten.client.path.targets.BlockTarget;

public class GotoCommand extends Command {
	
	public GotoCommand(TungstenClient mod) throws CommandException {
        // x z
        // x y z
        // x y z dimension
        // (dimension)
        // (x z dimension)
        super("goto", "Tell bot to travel to a set of coordinates", mod
                /*new Arg(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")*/
        );
    }

	@Override
	public void build(LiteralArgumentBuilder<net.minecraft.commands.CommandSource> builder) {
		
		builder.then(argument("gotoTarget", GotoTargetArgumentType.create()).executes(context -> {
	        try {
				
	        	BlockTarget target = GotoTargetArgumentType.get(context);
	        	if(!TungstenModDataContainer.PATHFINDER.active.get() && !TungstenModDataContainer.EXECUTOR.isRunning()) {
	        		TungstenClient.TARGET = target.getVec3d().add(0.5, 0, 0.5);
	        		TungstenModDataContainer.PATHFINDER.find(TungstenClient.mc.level, target.getVec3d().add(0.5, 0, 0.5), TungstenClient.mc.player);
	    		} else {
	    			Debug.logWarning("Already running!");
	    		}

			} catch (Exception e) {
				// TODO: handle exception
			}
			
			return SINGLE_SUCCESS;
		}));
	}

}
