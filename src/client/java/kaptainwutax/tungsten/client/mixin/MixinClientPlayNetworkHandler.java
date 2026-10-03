package kaptainwutax.tungsten.client.mixin;

import java.util.ArrayList;
import java.util.Collection;

import kaptainwutax.tungsten.client.TungstenClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.chat.LoggedChatEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import kaptainwutax.tungsten.client.Debug;
import kaptainwutax.tungsten.client.TungstenModDataContainer;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler extends ClientCommonPacketListenerImpl {
	
	@Shadow
    private ClientLevel level;



    
    protected MixinClientPlayNetworkHandler(final Minecraft arg, final Connection arg2, final CommonListenerCookie arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(
            method = "sendChat(Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendChatMessage(String message, CallbackInfo ci) {
		String prefix = TungstenClient.getCommandPrefix();
        if (message.startsWith(prefix)) {
//            try {
//            	if (message.contains("|")) {
////            		CommandExecutor.dispatch(message.split(";")[0].substring(prefix.length()));
//                	Collection<Command> commands = new ArrayList<>(TungstenClient.getCommandExecutor().allCommands());
//                	commands.removeIf((command) -> !message.contains(command.getName()));
//                    TungstenClient.getCommandExecutor().executeRecursive(commands.toArray(new Command[commands.size()]), message.split("|"), 0, () -> {
//                    }, ex -> Debug.logWarning(ex.getMessage()));
//            	} else CommandExecutor.dispatch(message.substring(prefix.length()));
//            } catch (CommandSyntaxException e) {
//                Debug.logWarning(e.getMessage());
//            } catch (IllegalArgumentException e) {
//                Debug.logWarning(e.getMessage());
//            }
//
//            client.inGameHud.getChatHud().addToMessageHistory(message);
            ci.cancel();
        }
    }


    @Inject(method = "handleEntityPositionSync", at = @At(value = "HEAD"), cancellable = false)
    public void onPlayerPositionLook(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if(TungstenModDataContainer.EXECUTOR.isRunning()) {
            // Server teleported us — stop executor and let vanilla handle the teleport.
            // Previously this ci.cancel()'d the packet, which caused a deadlock:
            // server waits for TeleportConfirmC2SPacket, client never sends it,
            // server ignores all subsequent movement packets → player stuck.
            Debug.logMessage("Server teleport received during execution — stopping executor");
            TungstenModDataContainer.EXECUTOR.stop = true;
            TungstenModDataContainer.PATHFINDER.stop.set(true);
        }
    }

}
