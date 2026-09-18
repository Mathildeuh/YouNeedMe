package fr.mathildeuh.youneedme.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Text input via a native Minecraft dialog screen (not an anvil-rename hack) - used by every
 * in-game editor (kits, shop, auction house) to prompt for a name/price/permission/etc.
 */
public final class DialogInputPrompt {

    private DialogInputPrompt() {}

    public static void open(
            Player player,
            String title,
            String label,
            String initialText,
            Consumer<String> onSubmit) {
        DialogInput textInput =
                DialogInput.text("value", Component.text(label)).initial(initialText).build();

        DialogAction confirmAction =
                DialogAction.customClick(
                        (response, audience) -> {
                            String value = response.getText("value");
                            onSubmit.accept(value == null ? "" : value);
                        },
                        ClickCallback.Options.builder().uses(1).build());
        ActionButton confirmButton =
                ActionButton.builder(Component.text("Confirm")).action(confirmAction).build();

        DialogBase base =
                DialogBase.builder(Component.text(title)).inputs(List.of(textInput)).build();
        DialogType type = DialogType.multiAction(List.of(confirmButton)).columns(1).build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }
}
