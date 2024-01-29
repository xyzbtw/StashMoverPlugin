package dev.xyzbtw.utils;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.utils.InventoryUtils;

import java.util.Collections;
import java.util.Set;

import static org.rusherhack.client.api.Globals.mc;

public class InventoryUtil {
    public static boolean isHolding(Item item) {
        return mc.player.isHolding(item);
    }
    public static void stealOnePearl(AbstractContainerScreen container){
        int slotCountWithInv = container.getMenu().slots.size() + 36;
        for(int i = container.getMenu().slots.size(); i > container.getMenu().slots.size() + 36; i--){
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slotCountWithInv - 8 /* To place it in first slot, you're prolly dead anyways and idrc */ , 1,ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, i, 0,ClickType.PICKUP, mc.player);
        }
    }

    public static boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

}
