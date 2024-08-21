package dev.xyzbtw.utils;

import dev.xyzbtw.MoverPlugin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.datafix.fixes.ItemShulkerBoxColorFix;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.utils.InventoryUtils;

import java.util.Collections;
import java.util.Set;

import static org.rusherhack.client.api.Globals.mc;

public class InventoryUtil {
    public static boolean isHolding(Item item) {
        return mc.player.isHolding(item);
    }
    public static void stealOnePearl(){
        if(!(mc.player.containerMenu instanceof ChestMenu menu)) return;;

        int slotCount = menu.getContainer().getContainerSize();
        for(int i = 0; i < slotCount; i++){
            if(menu.getContainer().getItem(i).isEmpty() || !menu.getContainer().getItem(i).getItem().equals(Items.ENDER_PEARL)) continue;
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slotCount + 36 - 8, 1,ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, i, 0, ClickType.PICKUP, mc.player);
        }
    }
    public static boolean isInventoryEmpty() {
        boolean empty = true;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) {
                empty = false;
            }
        }
        return empty;
    }

    public static boolean isChestEmpty() {
        if (!(mc.player.containerMenu instanceof ChestMenu menu)) return false;


        if (MoverPlugin.stashMoverModule.onlyShulkers.getValue()) {
            boolean hasShulker = false;

            for (int i = 0; i < menu.getContainer().getContainerSize(); i++) {
                if (menu.getContainer().getItem(i).getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    hasShulker = true;
                    break;
                }
            }

            if (!hasShulker) {
                return true;
            }
        }

        for (int i = 0; i < menu.getContainer().getContainerSize(); i++) {
            if (!menu.getContainer().getItem(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static boolean isChestFull(){
        if(!(mc.player.containerMenu instanceof ChestMenu menu)) return false;

        for (int i = 0; i < menu.getContainer().getContainerSize(); i++) {
            if (menu.getContainer().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void clickSlot(int slotId, boolean shiftClick) {
        if(mc.player == null || mc.gameMode == null) {
            return;
        }

        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slotId, 0, shiftClick ? ClickType.QUICK_MOVE : ClickType.PICKUP, mc.player);
    }
}
