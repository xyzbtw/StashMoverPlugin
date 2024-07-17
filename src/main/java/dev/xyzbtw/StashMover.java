package dev.xyzbtw;

import dev.xyzbtw.utils.BaritoneUtil;
import dev.xyzbtw.utils.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.client.chat.EventAddChat;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.events.world.EventEntity;
import org.rusherhack.client.api.feature.command.ModuleCommand;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.client.api.utils.RotationUtils;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.command.annotations.CommandExecutor;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.notification.NotificationType;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;
import org.rusherhack.core.utils.MathUtils;
import org.rusherhack.core.utils.Timer;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * A module that moves stashes for you
 *
 * @author xyzbtw
 */
@SuppressWarnings("unused")
public class StashMover extends ToggleableModule {

    /**
     * Settings
     */
    private final EnumSetting<MODES> mode = new EnumSetting<>("Mode", MODES.MOVER);
    private final NumberSetting<Integer> chestDelay = new NumberSetting<>("ChestDelay", "Delay between chest clicks", 1, 0, 10).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    private final NumberSetting<Integer> distance = new NumberSetting<>("ChestDistance", "Distance between you and lootchests", 100, 10, 1000).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    private final BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "If lootchest is full.", true).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    private final BooleanSetting useEchest = new BooleanSetting("UseEChest", "uses echest.", true).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    private final BooleanSetting ignoreSingular = new BooleanSetting("IgnoreSingleChest", "Doesn't steal from single chests.", true).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    public final BooleanSetting onlyShulkers = new BooleanSetting("OnlyShulkers", "Only steals shulkers", false).setVisibility(() -> mode.getValue().equals(MODES.MOVER));
    private final StringSetting otherIGN = new StringSetting("OtherIGN", "The username of the other person that's moving stash", "xyzbtwballs");
    private final StringSetting loadMessage = new StringSetting("LoadMessage", "The message that both accounts use.", "LOAD PEARL");

    /**
     * variables
     */

    private MOVER moverStatus = MOVER.LOOT;
    private LOADER loaderStatus = LOADER.WAITING;
    boolean hasThrownPearl = false;
    int chestTicks = 0;
    int ticksPassed = 0;
    Vec3 chamber;
    List<BlockPos> blacklistChests = new ArrayList<>();
    BlockPos currentChest;
    boolean sentMessage = false;
    boolean filledEchest = false;
    boolean disableOnceTP = false;
    Set<BlockPos> stealChests = new CopyOnWriteArraySet<>();
    public static BlockPos LOADER_BACK_POSITION,
            pearlChestPosition,
            chestForLoot,
            waterPos
    ;
    Timer lagTimer = new Timer();

    /**
     * constructor
     */
    public StashMover() {
        super("StashMover", "Moves stashes with pearls", ModuleCategory.MISC);
        this.registerSettings(
                this.mode,
                this.distance,
                this.chestDelay,
                this.onlyShulkers,
                this.useEchest,
                this.autoDisable,
                this.ignoreSingular,
                this.otherIGN,
                this.loadMessage
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        moverStatus = MOVER.LOOT;
        loaderStatus = LOADER.WAITING;
        sentMessage = false;
    }


    /**
     * methods
     */

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) return;

        if (mode.getValue().equals(MODES.MOVER)) {
            if (pearlChestPosition == null || chestForLoot == null || waterPos == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "One of your positions isn't set big boy");
                this.toggle();
                return;
            }
        } else {
            if (chamber == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Chamber position isn't set big boy");
                this.toggle();
                return;
            }
        }

        if (lagTimer.passed(1000)) return;

        ticksPassed++;
        if (ticksPassed < 2) return;
        ticksPassed = 0;


        if (mode.getValue().equals(MODES.MOVER)) {
            switch (moverStatus) {
                case SEND_LOAD_PEARL_MSG -> {
                    if (!sentMessage) {
                        String randomUUID = UUID.randomUUID().toString();
                        String shortUUID = randomUUID.substring(0, 8);
                        mc.player.connection.sendCommand("msg " + otherIGN.getValue() + " " + loadMessage.getValue() + " " + shortUUID);
                        ticksPassed = -40;
                    }
                }
                case WAIT_FOR_PEARL -> {
                    sentMessage = false;
                    if (!(mc.player.containerMenu instanceof ChestMenu) && !(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                        openChest(pearlChestPosition);
                        return;
                    }

                    if (InventoryUtils.findItemHotbar(Items.ENDER_PEARL) == -1) {
                        InventoryUtil.stealOnePearl();
                        return;
                    }

                    mc.player.closeContainer();
                    moverStatus = MOVER.THROWING_PEARL;
                }
                case THROWING_PEARL -> {
                    BaritoneUtil.goTo(waterPos);
                    throwPearl();
                    if (!hasThrownPearl) return;

                    BaritoneUtil.stopBaritone();
                    moverStatus = MOVER.PUT_BACK_PEARLS;
                    hasThrownPearl = false;
                }
                case PUT_BACK_PEARLS -> {
                    if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
                        openChest(pearlChestPosition);
                        return;
                    }
                    Container container = menu.getContainer();

                    chestTicks++;
                    if (InventoryUtils.findItem(Items.ENDER_PEARL, true, false) != -1) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (!container.getItem(i).getItem().equals(Items.ENDER_PEARL)) {
                                InventoryUtil.clickSlot(i, false);
                                InventoryUtil.clickSlot(container.getContainerSize() + 36 - 8, false);
                                InventoryUtil.clickSlot(i, false);
                            }
                            chestTicks = 0;
                        }
                        return;
                    }
                    mc.player.closeContainer();
                    moverStatus = MOVER.WALKING_TO_CHEST;

                }
                case ECHEST_LOOT -> {
                    if (filledEchest || !useEchest.getValue())
                        moverStatus = MOVER.SEND_LOAD_PEARL_MSG;

                    if(filledEchest) return;

                    if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
                        BaritoneUtil.goTo(Blocks.ENDER_CHEST);
                        return;
                    }

                    if (InventoryUtil.isChestFull() || InventoryUtil.isInventoryEmpty()) {
                        filledEchest = true;
                        mc.player.closeContainer();
                        moverStatus = MOVER.LOOT;
                        return;
                    }

                    chestTicks++;
                    for (int i = menu.getContainer().getContainerSize(); i < menu.getContainer().getContainerSize() + 36; i++) {
                        if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                        if (chestTicks < chestDelay.getValue()) return;

                        InventoryUtil.clickSlot(i, true);
                        chestTicks = 0;
                    }

                    return;
                }
                case LOOT -> {
                    if (mc.player.isDeadOrDying()) {
                        mc.player.respawn();
                        return;
                    }

                    if (InventoryUtils.isInventoryFull()) {
                        moverStatus = MOVER.ECHEST_LOOT;
                        if (filledEchest || !useEchest.getValue())
                            moverStatus = MOVER.SEND_LOAD_PEARL_MSG;

                        mc.player.closeContainer();
                        return;
                    }


                    if (!(mc.player.containerMenu instanceof ChestMenu)) {
                        openChest(getChest());
                        return;
                    }

                    if(!InventoryUtil.isChestEmpty()){
                        chestTicks++;
                        for (int i = 0; i < mc.player.containerMenu.slots.size() - 36; i++) {
                            if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                            if (chestTicks < chestDelay.getValue()) return;
                            if(onlyShulkers.getValue()){
                                if(mc.player.containerMenu.getSlot(i).getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock){
                                    continue;
                                }
                            }

                            InventoryUtil.clickSlot(i, true);
                            chestTicks = 0;
                        }
                        return;
                    }

                    blacklistChests.add(currentChest);

                    for (BlockEntity e : WorldUtils.getBlockEntities(true)) {
                        if (e instanceof ChestBlockEntity chest) {
                            if (blacklistChests.contains(e.getBlockPos())) {
                                if (!chest.getBlockState().getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE)) {
                                    Direction facing = ChestBlock.getConnectedDirection(chest.getBlockState());
                                    blacklistChests.add(chest.getBlockPos().relative(facing));
                                }
                            }
                        }
                    }


                    mc.player.closeContainer();
                    ticksPassed = -5; //delay between each chest i guess

                    if (new HashSet<>(blacklistChests).containsAll(WorldUtils.getBlockEntities(true)
                            .stream()
                            .filter(e -> e instanceof ChestBlockEntity)
                            .filter(e -> !e.getBlockState().getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE) || !ignoreSingular.getValue())
                            .map(BlockEntity::getBlockPos).collect(Collectors.toSet()))) {
                        moverStatus = MOVER.SEND_LOAD_PEARL_MSG;
                        disableOnceTP = true;
                        return;
                    }
                    return;

                }
                case WALKING_TO_CHEST -> {

                    if(mc.player.isDeadOrDying()) {
                        mc.player.respawn();
                        moverStatus = MOVER.LOOT;
                        return;
                    }

                    if (mc.player.distanceToSqr(chestForLoot.getX(), chestForLoot.getY(), chestForLoot.getZ()) > 9) {
                        BaritoneUtil.goTo(freeBlockAroundChest(chestForLoot));
                        return;
                    }

                    if (InventoryUtil.isInventoryEmpty()) {
                        if (filledEchest && useEchest.getValue()) {
                            moverStatus = MOVER.ECHEST_FILL;
                            mc.player.closeContainer();
                            return;
                        }

                        mc.player.connection.sendCommand("kill");

                        return;
                    }

                    if (!(mc.player.containerMenu instanceof ChestMenu)) {
                        openChest(chestForLoot);
                        return;
                    }

                    chestTicks++;
                    for (int i = mc.player.containerMenu.slots.size() - 36; i < mc.player.containerMenu.slots.size(); i++) {
                        if (InventoryUtil.isChestFull() && autoDisable.getValue()) {
                            this.toggle();
                            return;
                        }
                        if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                        if (chestTicks < chestDelay.getValue()) return;

                        InventoryUtil.clickSlot(i, true);
                        chestTicks = 0;
                    }
                }
                case ECHEST_FILL -> {
                    if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
                        BaritoneUtil.goTo(Blocks.ENDER_CHEST);
                        return;
                    }

                    if (InventoryUtil.isChestEmpty()) {
                        filledEchest = false;
                        moverStatus = MOVER.WALKING_TO_CHEST;
                        mc.player.closeContainer();
                        return;
                    }

                    chestTicks++;
                    for (int i = 0; i < menu.getContainer().getContainerSize(); i++) {
                        if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                        if (chestTicks < chestDelay.getValue()) return;

                        InventoryUtil.clickSlot(i, true);
                        chestTicks = 0;
                    }

                }
            }
        }
        if (mode.getValue().equals(MODES.LOADER)) {
            switch (loaderStatus) {
                case WAITING -> {
                    return;
                }
                case LOAD_PEARL -> {
                    if (chamber.distanceTo(mc.player.position()) > 4) {
                        BaritoneUtil.goTo(BlockPos.containing(chamber));
                        return;
                    }
                    RusherHackAPI.getRotationManager().updateRotation(BlockPos.containing(chamber));
                    RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);
                    loaderStatus = LOADER.WAITING;
                }

            }
        }
    }

	@Subscribe
	private void onRender(EventRender3D event){
		final IRenderer3D renderer = event.getRenderer();

		renderer.begin(event.getMatrixStack());

		if(!stealChests.isEmpty()){
			for(BlockPos pos : stealChests){
                if(pos.getCenter().distanceTo(mc.player.position()) > 14) continue;

				renderer.drawBox(pos, true, true, new Color(255, 140, 0, 70).getRGB());
			}
		}

		renderer.end();
	}

    @Subscribe
    private void onAddEntity(EventEntity.Add event) {
        if (mode.getValue().equals(MODES.MOVER)) {
            if (event.getEntity() instanceof ThrownEnderpearl
                    && mode.getValue().equals(MODES.MOVER)
                    && moverStatus.equals(MOVER.THROWING_PEARL)) {
                hasThrownPearl = true;
            }
            if (event.getEntity() instanceof Player
                    && ((Player) event.getEntity()).getGameProfile().getName().equals(otherIGN.getValue())) {
                moverStatus = MOVER.WAIT_FOR_PEARL;
                ticksPassed = 0;
                if(disableOnceTP){
                    this.toggle();
                    disableOnceTP = false;
                }
            }
            return;
        }

        if (event.getEntity() instanceof Player player && player.getGameProfile().getName().equalsIgnoreCase(otherIGN.getValue())) {
            RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);
        }

    }

    @Subscribe
    public void onPacketSend(EventPacket.Send event) {
        if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
            if (mc.level.getBlockState(packet.getHitResult().getBlockPos()).getBlock() instanceof ChestBlock) {
                currentChest = packet.getHitResult().getBlockPos();
            }
        }
    }

    @Subscribe
    public void onPacketReceive(EventPacket.Receive event) {
        if (mc.player == null || mc.level == null) return;

        lagTimer.reset();
    }


    protected String getLookPos(String string) {
        Vec3 lookPos = null;
        if (mc.level != null) {
            if (mc.hitResult == null) return "No hitresult, look at the block";

            if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";


            lookPos = mc.hitResult.getLocation();


            if (string.equalsIgnoreCase("pearlchest")) {
                pearlChestPosition = BlockPos.containing(lookPos);
            } else if (string.equalsIgnoreCase("lootchest"))
                chestForLoot = BlockPos.containing(lookPos);
        }
        return lookPos == null ? "You're not in a world??" : "Set to " + "X: " + MathUtils.round(lookPos.x, 2)
                + " Y: " + MathUtils.round(lookPos.y, 2)
                + " Z: " + MathUtils.round(lookPos.z, 2);
    }

    @Override
    public String getMetadata() {
        return switch (mode.getValue()){
            case LOADER -> loaderStatus.name();
            case MOVER -> moverStatus.name();
        };
    }

    @Override
    public ModuleCommand createCommand() {
        return new ModuleCommand(this) {
            @CommandExecutor(subCommand = "pearlchest")
            private String setPearlChestPos() {
                return getLookPos("pearlchest");
            }

            @CommandExecutor(subCommand = "lootchest")
            private String setLootChestPos() {
                return getLookPos("lootchest");
            }

            @CommandExecutor(subCommand = "chamber")
            private String setChamberPos() {
                if (mc.level == null || mc.player == null || mc.getCameraEntity() == null) return "Not in a world";
                if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";
                chamber = mc.hitResult != null ? mc.hitResult.getLocation() : null;
                return "Set chamber position.";
            }
            @CommandExecutor(subCommand = "water")
            private String setWaterPos() {
                waterPos = mc.player.blockPosition();
                return "Set water position.";
            }

        };
    }
    @Subscribe
    private void onChat(EventAddChat event) {

        String contents = event.getChatComponent().getString();

        if(mode.getValue().equals(MODES.LOADER)) {
            if (
                    (contents.startsWith(otherIGN.getValue()) && contents.contains(" whispers: " + loadMessage.getValue()))
                            || (contents.startsWith("From " + otherIGN.getValue()) && contents.contains(": " + loadMessage.getValue()))
                            || (contents.startsWith(otherIGN.getValue()) && contents.contains(" whispers to you: " + loadMessage.getValue()))
            ) {
                String randomUUID = UUID.randomUUID().toString();
                String shortUUID = randomUUID.substring(0, 8);
                mc.player.connection.sendCommand("msg " + otherIGN.getValue() + " RECEIVED MESSAGE " + shortUUID);
                loaderStatus = LOADER.LOAD_PEARL;
            }
        }

        if(mode.getValue().equals(MODES.MOVER)) {
            if(
                    contents.startsWith(otherIGN.getValue()) && contents.contains(" whispers: RECEIVED MESSAGE")
                    || contents.startsWith("From " + otherIGN.getValue() + ": ") && contents.contains("RECEIVED MESSAGE")
                    || contents.startsWith(otherIGN.getValue()) && contents.contains(" whispers to you: RECEIVED MESSAGE")
            )
            {
                sentMessage = true;
            }
        }

    }

    protected void throwPearl() {
        mc.player.setXRot(90);
        mc.player.setYRot(RotationUtils.getRotations(pearlChestPosition.getCenter())[0]);

        if (mc.hitResult == null) {
            System.out.println("SOMEHOW NULLLLLL");
            return;
        }
        if (mc.hitResult.getType().equals(HitResult.Type.BLOCK)) return;

        if (!InventoryUtil.isHolding(Items.ENDER_PEARL)) {
            int slot = InventoryUtils.findItemHotbar(Items.ENDER_PEARL);

            if (slot == -1) {
                RusherHackAPI.getNotificationManager().chat("You don't have a pearl in your inventory, weird");
                return;
            }

            mc.player.getInventory().selected = slot;
            return;
        }

        if (mc.player.getXRot() == 90) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    protected void openChest(BlockPos pos) {
        if (pos == null) return;
        if (pos.getCenter().distanceTo(mc.player.getEyePosition()) > 4.5) return;

        BaritoneUtil.stopBaritone();
        RusherHackAPI.getRotationManager().updateRotation(pos);

        if (isRotated(pos)) {
            RusherHackAPI.interactions().useBlock(pos, InteractionHand.MAIN_HAND, true, false);
        }
    }

    protected BlockPos getChest() {

        if(mc.player.isDeadOrDying() || mc.player.position().distanceTo(chestForLoot.getCenter()) < 6) return null;

        BlockPos closestChest = null;
        double shortestDistance = Integer.MAX_VALUE;

        for (BlockEntity blockentity : WorldUtils.getBlockEntities(true)) {
            if (blacklistChests.contains(blockentity.getBlockPos())) continue;


            if (blockentity instanceof ChestBlockEntity chest) {
                if (ignoreSingular.getValue()) {
                    if (chest.getBlockState().getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE))
                        continue;
                }

                double distance = blockentity.getBlockPos().getCenter().distanceTo(mc.player.getEyePosition());

                if(distance > this.distance.getValue()) continue;

                stealChests.add(blockentity.getBlockPos());

                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    closestChest = blockentity.getBlockPos();
                }
            }

        }
        blacklistChests.forEach(stealChests::remove);

        if (closestChest == null) return null;

        if (shortestDistance > 4.5) {
            BaritoneUtil.goTo(closestChest);
        }

        return closestChest;
    }

    public BlockPos freeBlockAroundChest(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN || direction == Direction.UP) {
                continue;
            }
            BlockPos offsetPos = pos.relative(direction, 1);

            if (!mc.level.getBlockState(offsetPos).isAir()) continue;
            if (mc.level.getBlockState(offsetPos.below()).isAir()) continue;

            return offsetPos;
        }
        return pos.above();
    }

    boolean isRotated(BlockPos pos) {
        return RusherHackAPI.getRotationManager().isLookingAt(pos);
    }

    protected enum MODES {
        MOVER,
        LOADER
    }

    protected enum MOVER {
        LOOT,
        SEND_LOAD_PEARL_MSG,
        WAIT_FOR_PEARL,
        WALKING_TO_CHEST,
        THROWING_PEARL,
        PUT_BACK_PEARLS,
        ECHEST_LOOT,
        ECHEST_FILL
    }

    protected enum LOADER {
        LOAD_PEARL,
        WAITING
    }
}
