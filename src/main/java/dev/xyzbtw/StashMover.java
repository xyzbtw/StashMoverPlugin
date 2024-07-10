package dev.xyzbtw;

import dev.xyzbtw.utils.BaritoneUtil;
import dev.xyzbtw.utils.InventoryUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.accessors.gui.IMixinAbstractContainerScreen;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.world.EventEntity;
import org.rusherhack.client.api.feature.command.ModuleCommand;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.ChatUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
	private final NumberSetting<Integer> chestDelay = new NumberSetting<>("ChestDelay", "Delay between chest clicks", 2, 0, 10).setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final BooleanSetting is2b = new BooleanSetting("Systemchat", "Changes chatpackets from player to system", true);
	private final BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "If lootchest is full.", false);
	private final BooleanSetting ignoreSingular = new BooleanSetting("IgnoreSingleChest", "Doesn't steal from single chests.", false);
	private final StringSetting otherIGN = new StringSetting("OtherIGN", "The username of the other person that's moving stash", "xyzbtwballs");

	/**
	 * variables
	 */

	private MOVER moverStatus = MOVER.LOOT;
	private LOADER loaderStatus = LOADER.WAITING;
	boolean hasThrownPearl = false;
	int chestTicks = 0;
	int ticksPassed = 0;
	String LOADPEARLMSG = "LOAD PEARL";
	Vec3 chamber;
	List<BlockPos> blacklistChests = new ArrayList<>();
	BlockPos currentChest;
	boolean sentMessage = false;
	public static BlockPos  LOADER_BACK_POSITION,
							pearlChestPosition,
							chestForLoot;
	Timer lagTimer = new Timer();

	/**
	 * constructor
	 */
	public StashMover() {
		super("StashMover", "Moves stashes with pearls", ModuleCategory.CLIENT);
		this.registerSettings(
				this.mode,
				this.chestDelay,
				this.is2b,
				this.autoDisable,
				this.ignoreSingular,
				this.otherIGN
		);
	}
	/**
	 * methods
	 */

	@Subscribe
	private void onUpdate(EventUpdate event) {
		if(mc.player == null || mc.level == null) return;

		if(mode.getValue().equals(MODES.MOVER)){
			if(pearlChestPosition == null || chestForLoot==null){
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "One of your positions isn't set big boy");
				this.toggle();
				return;
			}
		}else{
			if(chamber==null){
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Chamber position isn't set big boy");
				this.toggle();
				return;
			}
		}

		if(lagTimer.passed(1000)) return;

		ticksPassed++;
		if(ticksPassed < 2) return;
		ticksPassed = 0;


		if (mode.getValue().equals(MODES.MOVER)) {
			switch (moverStatus) {
				case SEND_LOAD_PEARL_MSG -> {
					if (!sentMessage) {
						mc.player.connection.sendCommand("msg " + otherIGN.getValue() + " " + LOADPEARLMSG);
						sentMessage = true;
					}
				}
				case WAIT_FOR_PEARL -> {
					sentMessage = false;
					if (mc.player.distanceToSqr(pearlChestPosition.getX(), pearlChestPosition.getY(), pearlChestPosition.getZ()) > 9) {
						return;
					}
					if (!(mc.player.containerMenu instanceof ChestMenu)) {
						openChest(pearlChestPosition);
						return;
					}

					if (InventoryUtils.findItemHotbar(Items.ENDER_PEARL) == -1) {
						InventoryUtil.stealOnePearl();
						return;
					} else {
						mc.player.closeContainer();
						moverStatus = MOVER.THROWING_PEARL;
					}

				}
				case THROWING_PEARL -> {
					throwPearl();
					if (hasThrownPearl) {
						moverStatus = MOVER.PUT_BACK_PEARLS;
						hasThrownPearl = false;
					}
				}
				case PUT_BACK_PEARLS -> {
					if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
						openChest(pearlChestPosition);
						return;
					}
					Container container = menu.getContainer();

					chestTicks++;
					if(InventoryUtils.findItem(Items.ENDER_PEARL, true, false) != -1){
						for(int i = 0; i < container.getContainerSize(); i++){
							if(!container.getItem(i).getItem().equals(Items.ENDER_PEARL)){
								InventoryUtils.clickSlot(i, false);
								InventoryUtils.clickSlot(container.getContainerSize() + 36 - 8, false);
								InventoryUtils.clickSlot(i, false);
							}
							chestTicks=0;
						}
						return;
					}
					mc.player.closeContainer();
					moverStatus = MOVER.WALKING_TO_CHEST;

				}
				case LOOT -> {
					if (mc.player.isDeadOrDying()) {
						mc.player.respawn();
						return;
					}
					if (InventoryUtils.isInventoryFull()) {
						moverStatus = MOVER.SEND_LOAD_PEARL_MSG;
						return;
					}
					if (!(mc.player.containerMenu instanceof ChestMenu)) {
						openChest(getChest());
						return;
					}
					if (InventoryUtil.isChestEmpty()) {
						blacklistChests.add(currentChest);
						System.out.println("Added " + currentChest + " to blacklist");
						mc.player.closeContainer();
						int count = 0;
						for(BlockEntity e : WorldUtils.getBlockEntities(true)){
							if(e instanceof ChestBlockEntity chest){
								if(ignoreSingular.getValue()){
									if(chest.getBlockState().getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE)) continue;
								}
								count++;
							}
						}
						if(!blacklistChests.isEmpty() && blacklistChests.size() >= count){
							moverStatus = MOVER.SEND_LOAD_PEARL_MSG;
							this.toggle();
							return;
						}
						return;
					}
					chestTicks++;
					for (int i = 0; i < mc.player.containerMenu.slots.size() - 36; i++) {
						if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
						if (chestTicks < chestDelay.getValue()) return;

						InventoryUtils.clickSlot(i, true);
						chestTicks = 0;
					}
				}
				case WALKING_TO_CHEST -> {

					if (mc.player.distanceToSqr(chestForLoot.getX(), chestForLoot.getY(), chestForLoot.getZ()) > 9) {
						BaritoneUtil.goTo(freeBlockAroundChest(chestForLoot));
						return;
					}

					if (!(mc.player.containerMenu instanceof ChestMenu)) {
						openChest(chestForLoot);
						return;
					}

					chestTicks++;
					for (int i = mc.player.containerMenu.slots.size() - 36; i < mc.player.containerMenu.slots.size(); i++) {
						if(InventoryUtil.isChestFull() && autoDisable.getValue()){
							this.toggle();
							return;
						}
						if (InventoryUtil.isInventoryEmpty()) {
							moverStatus = MOVER.LOOT;
							mc.player.connection.sendCommand("kill");
							return;
						}
						if (!mc.player.containerMenu.getSlot(i).hasItem()) continue;
						if (chestTicks < chestDelay.getValue()) return;

						InventoryUtils.clickSlot(i, true);
						chestTicks = 0;
					}
				}
			}
		}
		if (mode.getValue().equals(MODES.LOADER)) {
			switch (loaderStatus) {
				case WAITING -> {
					System.out.println("just waiting yk");
					return;
				}
				case LOAD_PEARL -> {
					if (chamber.distanceTo(mc.player.position()) > 3) {
						BaritoneUtil.goTo(BlockPos.containing(chamber));
						return;
					}
					System.out.println("interacting at " + chamber);
					RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);
					loaderStatus = LOADER.WAITING;
				}

			}
		}
	}

	@Subscribe
	private void onAddEntity(EventEntity.Add event){

		if(mode.getValue().equals(MODES.MOVER)) {
			if (event.getEntity() instanceof ThrownEnderpearl
					&& mode.getValue().equals(MODES.MOVER)
					&& moverStatus.equals(MOVER.THROWING_PEARL)) {
				hasThrownPearl = true;
			}
			if (event.getEntity() instanceof Player
					&& ((Player) event.getEntity()).getGameProfile().getName().equals(otherIGN.getValue())) {
				moverStatus = MOVER.WAIT_FOR_PEARL;
			}
		}
		else{
			if(event.getEntity() instanceof Player player && player.getGameProfile().getName().equalsIgnoreCase(otherIGN.getValue())){
				RusherHackAPI.interactions().useBlock(BlockPos.containing(chamber), InteractionHand.MAIN_HAND, true, false);
			}
		}
	}
	@Subscribe
	public void onPacketSend(EventPacket.Send event){
		if(event.getPacket() instanceof ServerboundContainerClosePacket){
			currentChest = null;
		}
		if(event.getPacket() instanceof ServerboundUseItemOnPacket packet){
			if(mc.level.getBlockState(packet.getHitResult().getBlockPos()).getBlock() instanceof ChestBlock){
				currentChest = packet.getHitResult().getBlockPos();
			}
		}
	}
	@Subscribe
	public void onPacketReceive(EventPacket.Receive event){
		if(mc.player == null || mc.level == null) return;

		lagTimer.reset();

		if(!mode.getValue().equals(MODES.LOADER)) return;
		if (event.getPacket() instanceof ClientboundSystemChatPacket systemChat && is2b.getValue()) {
			String contents = systemChat.content().getString();
			System.out.println(contents);
			if (contents.equalsIgnoreCase(otherIGN.getValue() + " whispers: " + LOADPEARLMSG) || contents.equalsIgnoreCase("From " + otherIGN.getValue() + ": " + LOADPEARLMSG)) {
				loaderStatus = LOADER.LOAD_PEARL;
			}

		}
		else if (event.getPacket() instanceof ClientboundPlayerChatPacket chatPacket) {
			if (!is2b.getValue()) {
				String message = chatPacket.body().content();
				if (message.equalsIgnoreCase(LOADPEARLMSG)) loaderStatus = LOADER.LOAD_PEARL;
			}
		}
	}
	protected String getLookPos(String string){
		Vec3 lookPos = null;
		if(mc.level!=null) {
			if (mc.hitResult == null) return "No hitresult, look at the block";

			if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";


			lookPos = mc.hitResult.getLocation();


			if(string.equalsIgnoreCase("pearlchest")) {
				 pearlChestPosition = BlockPos.containing(lookPos);
			}else if (string.equalsIgnoreCase("lootchest"))
				 chestForLoot = BlockPos.containing(lookPos);
		}
		return lookPos==null ? "You're not in a world??" : "Set to " + "X: " +  MathUtils.round(lookPos.x, 2)
				+ " Y: " + MathUtils.round(lookPos.y, 2)
				+ " Z: " + MathUtils.round(lookPos.z, 2);
	}

	@Override
	public String getMetadata(){
		switch (mode.getValue()){
			case LOADER -> {
				return loaderStatus.name();
			}
			case MOVER -> {
				return moverStatus.name();
			}
		}

		return "";
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
			private String setChamberPos(){
				if(mc.level == null || mc.player==null || mc.getCameraEntity() == null) return "Not in a world";
				if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";
				chamber = mc.hitResult != null ? mc.hitResult.getLocation() : null;
				return "Set chamber position.";
			}

		};
	}


	private UUID getUUID(String otherIGN){
		Optional<PlayerInfo> playerOpt = mc.player.connection.getOnlinePlayers().stream()
				.filter(p -> p.getProfile().getName().equalsIgnoreCase(otherIGN))
				.findFirst();
		if(playerOpt.isPresent()){
			return playerOpt.get().getProfile().getId();
		} else {
			RusherHackAPI.getNotificationManager().chat("Sorry lil bro couldn't find your 2nd acc");
		}
		return null;
	}
	protected void throwPearl(){
		mc.player.setXRot(90);

		if(mc.hitResult == null) return;
		if(mc.hitResult.getType() == HitResult.Type.BLOCK) return;

        if (!InventoryUtil.isHolding(Items.ENDER_PEARL)) {
            int slot = InventoryUtils.findItemHotbar(Items.ENDER_PEARL);

            if (slot == -1) {
                RusherHackAPI.getNotificationManager().chat("You don't have a pearl in your inventory, weird");
                return;
            }

            mc.player.getInventory().selected = slot;
			return;
        }
		if(RusherHackAPI.getServerState().getPlayerPitch() == 90)
			mc.player.connection.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0));
	}
	protected void openChest(BlockPos pos){
		if(pos == null) return;
		if(pos.distSqr(mc.player.blockPosition()) > 9) return;

		RusherHackAPI.getRotationManager().updateRotation(pos);
		if (isRotated(pos)) {
			RusherHackAPI.interactions().useBlock(pos, InteractionHand.MAIN_HAND, true, false);
		}
	}
	protected BlockPos getChest(){
		BlockPos closestChest = null;
		double shortestDistance = Integer.MAX_VALUE;

		for(BlockEntity blockentity : WorldUtils.getBlockEntities(true)){
			if(blacklistChests.contains(blockentity.getBlockPos())) continue;

			if(blockentity instanceof ChestBlockEntity chest){
				if(ignoreSingular.getValue()){
					if(chest.getBlockState().getValue(BlockStateProperties.CHEST_TYPE).equals(ChestType.SINGLE)) continue;
				}
				double distance = blockentity.getBlockPos().getCenter().distanceTo(mc.player.position());

				if(distance < shortestDistance) {
					shortestDistance = distance;
					closestChest = blockentity.getBlockPos();
				}
			}

		}

		if(closestChest == null) return null;

		if(shortestDistance > 3){
			BaritoneUtil.goTo(closestChest);
		}

		return closestChest;
	}

	public BlockPos freeBlockAroundChest(BlockPos pos){
		for(Direction direction : Direction.values()){
			if(direction == Direction.DOWN || direction == Direction.UP){
				continue;
			}
			BlockPos offsetPos = pos.relative(direction, 1);

			if(!mc.level.getBlockState(offsetPos).isAir()) continue;
			if(mc.level.getBlockState(offsetPos.below()).isAir()) continue;

			return offsetPos;
		}
		return pos.above();
	}

	boolean isRotated(BlockPos pos){
		return RusherHackAPI.getRotationManager().isLookingAt(pos);
	}

	protected enum MODES {
		MOVER,
		LOADER
	}
	protected enum MOVER{
		LOOT,
		SEND_LOAD_PEARL_MSG,
		WAIT_FOR_PEARL,
		WALKING_TO_CHEST,
		THROWING_PEARL,
		PUT_BACK_PEARLS
	}
	protected enum LOADER{
		LOAD_PEARL,
		WAITING
	}
}
