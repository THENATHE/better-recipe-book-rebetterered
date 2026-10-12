package marsh.town.brb.mixins.instantcraft;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(at = @At("TAIL"), method = "handleContainerSetSlot")
    private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        BetterRecipeBook.instantCraftingManager.onSlotUpdate(packet.getContainerId(), packet.getStateId(), packet.getSlot());
    }

    @Inject(at = @At("TAIL"), method = "handleContainerContent")
    private void onContentUpdate(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        BetterRecipeBook.instantCraftingManager.onContentUpdate(packet.containerId(), packet.stateId());
    }

    @Inject(at = @At("TAIL"), method = "handlePlaceRecipe")
    private void onGhostRecipe(ClientboundPlaceGhostRecipePacket packet, CallbackInfo ci) {
        BetterRecipeBook.instantCraftingManager.onGhostRecipe(packet.containerId());
    }
}
