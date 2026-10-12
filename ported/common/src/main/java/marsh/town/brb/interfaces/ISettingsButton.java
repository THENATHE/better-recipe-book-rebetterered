package marsh.town.brb.interfaces;

import marsh.town.brb.config.Config;
import marsh.town.brb.util.BRBTextures;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public interface ISettingsButton {
    default ImageButton createSettingsButton(int x, int y) {
        ImageButton button = new ImageButton(x + 11, y + 137, 18, 18,
                BRBTextures.SETTINGS_BUTTON_SPRITES, ignored -> {
            Minecraft client = Minecraft.getInstance();
            client.gui.setScreen(AutoConfigClient.getConfigScreen(Config.class, client.gui.screen()).get());
        });
        button.setTooltip(Tooltip.create(Component.translatable("brb.gui.settings.open")));
        return button;
    }
}
