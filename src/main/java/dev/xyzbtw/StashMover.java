package dev.xyzbtw;

import dev.xyzbtw.utils.BaritoneUtil;
import dev.xyzbtw.utils.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
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
    public final BooleanSetting censorCoordinates = new BooleanSetting("CensorCoords", "Censors your coords in chat", false);
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
    public List<BlockPos> blacklistChests = new ArrayList<>();
    BlockPos currentChest;
    boolean sentMessage = false;
    boolean filledEchest = false;
    boolean disableOnceTP = false;
    boolean checkedThisLoop = false;
    boolean reconnectThing = false;
    private BlockPos previousPos = null;
    private boolean hasMoved = false;
    float shulksMoved = 0;
    public Set<BlockPos> stealChests = new CopyOnWriteArraySet<>();
    public static BlockPos LOADER_BACK_POSITION,
            pearlChestPosition,
            chestForLoot,
            waterPos
    ;
    Timer lagTimer = new Timer();
    Timer noMoveTimer = new Timer();

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
                this.loadMessage,
                this.censorCoordinates
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        moverStatus = MOVER.LOOT;
        loaderStatus = LOADER.WAITING;
        filledEchest = false;
        sentMessage = false;
    }


    /**
     * methods
     */

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) return;

        if (mode.getValue().equals(MODES.MOVER)) {
            if (pearlChestPosition == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Pearl chest position isn't set!");
            } else if (chestForLoot == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Chest for loot position isn't set!");
            } else if (waterPos == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Water position isn't set!");
            } else if (chamber == null) {
                RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Chamber position isn't set!");
            }

            if (pearlChestPosition == null || chestForLoot == null || waterPos == null || chamber == null) {
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

        if(mc.player.isDeadOrDying()) {
            moverStatus = MOVER.LOOT;
            mc.player.respawn();
            return;
        }

        if (reconnectThing){
            int minX = (int) mc.player.getX() - 24;
            int maxX = (int) mc.player.getX() + 24;
            int minZ = (int) mc.player.getZ() - 24;
            int maxZ = (int) mc.player.getZ() + 24;

            int randomX = minX + new Random().nextInt(maxX - minX + 1);
            int randomZ = minZ + new Random().nextInt(maxZ - minZ + 1);

            BaritoneUtil.goTo(new BlockPos(randomX, (int) mc.player.getY(), randomZ));
            reconnectThing = false;
            return;
        }

        ticksPassed++;
        if (ticksPassed < 2) return;
        ticksPassed = 0;


        if (mc.player.getDeltaMovement().x != 0 || mc.player.getDeltaMovement().z != 0) {
            noMoveTimer.reset();
        }
        BlockPos currentPos = mc.player.blockPosition();

        if(previousPos != null){
            hasMoved = Math.abs(currentPos.getX() - previousPos.getX()) > 3 ||
                    Math.abs(currentPos.getY() - previousPos.getY()) > 3 ||
                    Math.abs(currentPos.getZ() - previousPos.getZ()) > 3;

        }

        previousPos = currentPos;



        if (mode.getValue().equals(MODES.MOVER)) {
            switch (moverStatus) {
                case SEND_LOAD_PEARL_MSG -> {
                    if (!sentMessage) {
                        String randomUUID = UUID.randomUUID().toString();
                        String secondRandomUUID2 = UUID.randomUUID().toString();

                        String shortUUID = randomUUID.substring(0, 4);
                        String secondShortUUID = secondRandomUUID2.substring(0, 4);

                        mc.player.connection.sendCommand("msg " + otherIGN.getValue() + " " + shortUUID + loadMessage.getValue() + " " + secondShortUUID);
                        ticksPassed = -40;
                        checkedThisLoop = false;
                    }
                }
                case WAIT_FOR_PEARL -> {
                    BaritoneUtil.goTo(waterPos);

                    if(mc.level.getBlockState(BlockPos.containing(chamber)).getBlock() instanceof TrapDoorBlock) {
                        if (!mc.level.getBlockState(BlockPos.containing(chamber)).getValue(TrapDoorBlock.OPEN)) {
                            float[] rotations = RotationUtils.getRotations(chamber);

                            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(rotations[0], rotations[1], mc.player.onGround()));
                            RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);

                            ticksPassed = -10;
                            return;
                        }
                    }

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
                        ticksPassed = -3;
                        return;
                    }

                    Container container = menu.getContainer();
                    chestTicks++;
                    if (InventoryUtils.findItemHotbar(Items.ENDER_PEARL) != -1) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (!container.getItem(i).getItem().equals(Items.ENDER_PEARL)) {
                                if (mc.player.containerMenu instanceof InventoryMenu) return;
                                if (chestTicks < chestDelay.getValue()) return;

                                if (mc.player.containerMenu.slots.size() < container.getContainerSize() + 36 - 8){
                                    mc.player.closeContainer();
                                    return;
                                }

                                InventoryUtils.clickSlot(i, false);
                                int pearlSlot = InventoryUtils.findItemHotbar(Items.ENDER_PEARL) + mc.player.containerMenu.slots.size() - 9;
                                InventoryUtils.clickSlot(pearlSlot, false);
                                InventoryUtils.clickSlot(i, false);
                                mc.player.closeContainer();
                                moverStatus = MOVER.WALKING_TO_CHEST;
                                break;
                            }
                            chestTicks = 0;
                        }
                        return;
                    }

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

                    if (InventoryUtils.isInventoryFull()) {
                        moverStatus = MOVER.ECHEST_LOOT;
                        if (filledEchest || !useEchest.getValue())
                            moverStatus = MOVER.SEND_LOAD_PEARL_MSG;

                        mc.player.closeContainer();
                        return;
                    }



                    if (!(mc.player.containerMenu instanceof ChestMenu)) {
                        if (noMoveTimer.passed(10000)) {
                            int minX = (int) mc.player.getX() - 3;
                            int maxX = (int) mc.player.getX() + 3;
                            int minZ = (int) mc.player.getZ() - 3;
                            int maxZ = (int) mc.player.getZ() + 3;

                            int randomX = minX + new Random().nextInt(maxX - minX + 1);
                            int randomZ = minZ + new Random().nextInt(maxZ - minZ + 1);

                            BaritoneUtil.goTo(new BlockPos(randomX, (int) mc.player.getY(), randomZ));
                            ticksPassed = -10;
                            return;
                        }
                        openChest(getChest());
                        return;
                    }

                    if(!InventoryUtil.isChestEmpty()){
                        chestTicks++;
                        for (int i = 0; i < mc.player.containerMenu.slots.size() - 36; i++) {
                            if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                            if (chestTicks < chestDelay.getValue()) return;
                            if(onlyShulkers.getValue()){
                                if(!(mc.player.containerMenu.getSlot(i).getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock)){
                                    continue;
                                }
                            }

                            InventoryUtil.clickSlot(i, true);
                            chestTicks = 0;
                        }
                        return;
                    }
                    mc.player.closeContainer();


                    ticksPassed = -5; //delay between each chest i guess
                    blacklistChests.add(currentChest);
                    if (blacklistChests.contains(currentChest)) {
                        if (!mc.level.getBlockState(currentChest).getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE)) {
                            Direction facing = ChestBlock.getConnectedDirection(mc.level.getBlockState(currentChest));
                            blacklistChests.add(currentChest.relative(facing));
                        }
                    }

                    if(!checkedThisLoop) {
                        boolean ignoreSingularValue = ignoreSingular.getValue();
                        boolean allBlacklisted = true;

                        for (BlockEntity e : WorldUtils.getBlockEntities(true)) {
                            if (e instanceof ChestBlockEntity) {
                                ChestType chestType = e.getBlockState().getValue(BlockStateProperties.CHEST_TYPE);
                                if (chestType.equals(ChestType.SINGLE) && ignoreSingularValue) {
                                    continue;
                                }
                                if (!blacklistChests.contains(e.getBlockPos())) {
                                    allBlacklisted = false;
                                    break;
                                }
                            }
                        }

                        if (allBlacklisted) {
                            moverStatus = MOVER.SEND_LOAD_PEARL_MSG;
                            disableOnceTP = true;
                            return;
                        }
                    }

                    return;

                }
                case WALKING_TO_CHEST -> {

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
                        ticksPassed = -10;

                        return;
                    }

                    if (currentChest != null && !currentChest.equals(chestForLoot) && mc.player.containerMenu instanceof ChestMenu) {
                        mc.player.closeContainer();
                        return;
                    }

                    if (!(mc.player.containerMenu instanceof ChestMenu)) {
                        openChest(chestForLoot);
                        return;
                    }

                    chestTicks++;
                    for (int i = mc.player.containerMenu.slots.size() - 36; i < mc.player.containerMenu.slots.size(); i++) {
                        if (InventoryUtil.isChestFull()) {
                            if(autoDisable.getValue())
                                this.toggle();
                            return;
                        }
                        if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
                        if (chestTicks < chestDelay.getValue()) return;

                        InventoryUtil.clickSlot(i, true);
                        chestTicks = 0;
                        shulksMoved += 1;
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
                    && ((Player) event.getEntity()).getGameProfile().getName().equals(otherIGN.getValue())
                    && moverStatus.equals(MOVER.SEND_LOAD_PEARL_MSG))
            {

                moverStatus = MOVER.WAIT_FOR_PEARL;
                System.out.println("ON ADD ENTITY!!");
                ticksPassed = -3;

                if(disableOnceTP){
                    this.toggle();
                    disableOnceTP = false;
                }
            }
            return;
        }

//        if (event.getEntity() instanceof Player player && player.getGameProfile().getName().equalsIgnoreCase(otherIGN.getValue())) {
//            RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);
//        }

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
    private void onRemoveEntity(EventEntity.Remove event) {
        if(mc.player.isDeadOrDying()) {
            return;
        }
        if(mode.getValue().equals(MODES.MOVER)) {
            if (event.getEntity() instanceof ThrownEnderpearl) {
                if (mode.getValue().equals(MODES.MOVER)) {
                    if (moverStatus.equals(MOVER.ECHEST_FILL)
                            || moverStatus.equals(MOVER.THROWING_PEARL)
                            || moverStatus.equals(MOVER.WALKING_TO_CHEST)
                            || moverStatus.equals(MOVER.PUT_BACK_PEARLS)

                    ) {
                        BaritoneUtil.stopBaritone();
                        if (mc.player.containerMenu instanceof ChestMenu)
                            mc.player.closeContainer();
                        moverStatus = MOVER.WAIT_FOR_PEARL;
                        System.out.println("ON REMOVE ENTITY!!");
                        ticksPassed = 0;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPacketReceive(EventPacket.Receive event) {
        if (mc.player == null || mc.level == null) return;

        lagTimer.reset();
        if(event.getPacket() instanceof ClientboundDisconnectPacket){
            reconnectThing = true;
            ticksPassed = -600;
        }
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
        String coordinates = censorCoordinates.getValue() ? "Censored!!!" : "X: " + MathUtils.round(lookPos.x, 2)
                + " Y: " + MathUtils.round(lookPos.y, 2)
                + " Z: " + MathUtils.round(lookPos.z, 2);
        return lookPos == null ? "You're not in a world??" : "Set " + string + " to " + coordinates;
    }

    @Override
    public String getMetadata() {
        String dubsMoved = "Dubs: " + Math.floor(shulksMoved / 54);
        return switch (mode.getValue()){
            case LOADER -> loaderStatus.name();
            case MOVER -> moverStatus.name() + " " + dubsMoved;
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
                    (contents.startsWith(otherIGN.getValue() + " whispers: ") && contents.contains(loadMessage.getValue()))
                            || (contents.startsWith("From " + otherIGN.getValue() + ": ") && contents.contains(loadMessage.getValue()))
                            || (contents.startsWith(otherIGN.getValue() + " whispers to you: ") && contents.contains(loadMessage.getValue()))
            ) {
                ticksPassed = -10;
                String randomUUID = UUID.randomUUID().toString();
                String shortUUID = randomUUID.substring(0, 4);

                String secondRandomUUID = UUID.randomUUID().toString();
                String secondShortUUID = randomUUID.substring(0, 4);



                mc.player.connection.sendCommand("msg " + otherIGN.getValue() + " " + shortUUID + " RECEIVED MESSAGE " + secondShortUUID);
                loaderStatus = LOADER.LOAD_PEARL;
            }
        }

        if(mode.getValue().equals(MODES.MOVER)) {
            if(
                    contents.startsWith(otherIGN.getValue() + " whispers:") && contents.contains(" RECEIVED MESSAGE")
                    || contents.startsWith("From " + otherIGN.getValue() + ": ") && contents.contains("RECEIVED MESSAGE")
                    || contents.startsWith(otherIGN.getValue() + " whispers to you:") && contents.contains(" RECEIVED MESSAGE")
            )
            {
                sentMessage = true;
            }
        }

    }

    protected void throwPearl() {
        mc.player.setXRot(90);
        mc.player.setYRot(RotationUtils.getRotations(waterPos.getCenter())[0]);

        if (mc.hitResult == null) {
            System.out.println("SOMEHOW NULLLLLL");
            return;
        }


        if (mc.hitResult.getType().equals(HitResult.Type.BLOCK)) return;

        if (!InventoryUtil.isHolding(Items.ENDER_PEARL)) {
            int slot = InventoryUtils.findItemHotbar(Items.ENDER_PEARL);

            if (slot == -1) {
                RusherHackAPI.getNotificationManager().chat("You don't have a pearl in your inventory, weird");
                moverStatus = MOVER.WAIT_FOR_PEARL;
                System.out.println("ON THROW PEARL!!");
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

        float[] rotations = RotationUtils.getRotations(pos.getCenter());

        RusherHackAPI.getRotationManager().updateRotation(pos);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(rotations[0], rotations[1], mc.player.onGround()));
        RusherHackAPI.interactions().useBlock(pos, InteractionHand.MAIN_HAND, true, false);
    }

    protected BlockPos getChest() {

        if(mc.player.isDeadOrDying() || mc.player.position().distanceTo(chestForLoot.getCenter()) < 6) return null;
        if (BaritoneUtil.isBaritonePathing()) return null;

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
