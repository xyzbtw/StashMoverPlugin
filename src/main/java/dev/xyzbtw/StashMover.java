package dev.xyzbtw;

import dev.xyzbtw.utils.BaritoneUtil;
import dev.xyzbtw.utils.InventoryUtil;
import dev.xyzbtw.utils.TimerUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.network.EventPacket;
import org.rusherhack.client.api.events.world.EventEntity;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;

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
	private final NumberSetting<Integer> delay = new NumberSetting<>("Delay", "Delay between like thingy things in ticks", 5, 0, 60);
	private final NumberSetting<Integer> chestDelay = new NumberSetting<>("ChestDelay", "Delay betweenc chest clicks", 2, 0, 10);
	private final BooleanSetting is2b = new BooleanSetting("2b2t", "Changes chatpackets from player to system", true);
	private final NumberSetting<Float> rotateYaw = new NumberSetting<>("Yaw", 0f, 0f, 360f)
			.incremental(0.1f)
			.setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final NumberSetting<Float> rotatePitch = new NumberSetting<>("Pitch", 0f, -90f, 90f)
			.incremental(0.1f)
			.setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final NumberSetting<Float> rotateStep = new NumberSetting<>("RotationStep", 40f, 0f, 180f)
			.incremental(0.1f)
			.setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final StringSetting otherIGN = new StringSetting("OtherIGN", "The username of the other person that's moving stash", "xyzbtwballs");
	public static BlockPos pearlChestPosition;
	public static BlockPos walkToPosition;

	/**
	 * variables
	 */

	private MOVER moverStatus = MOVER.WAIT_FOR_PEARL;
	private LOADER loaderStatus = LOADER.WAITING;
	boolean hasThrownPearl = false;
	int ticksPassed, chestTicks = 0;
	private final TimerUtil lagTimer = new TimerUtil();

	/**
	 * constructor
	 */
	public StashMover() {
		super("StashMover", "Moves stashes with pearls", ModuleCategory.CLIENT);
		this.registerSettings(
				this.mode,
				this.delay,
				this.is2b,
				this.rotateYaw,
				this.rotatePitch,
				this.rotateStep
		);
	}
	/**
	 * methods
	 */

	@Subscribe
	private void onUpdate(EventUpdate event) {
		if(mc.player == null || mc.level == null) return;

		if(lagTimer.getPassedTimeMs() > 1000) return;
		ticksPassed++;

		if(ticksPassed>delay.getValue()) {
			switch (mode.getValue()) {
				case MOVER -> {
					switch (moverStatus) {
						case WAIT_FOR_PEARL -> {
							if(mc.player.distanceToSqr(pearlChestPosition.getX(), pearlChestPosition.getY(), pearlChestPosition.getZ()) > 100000){

							}
							if(mc.player.distanceToSqr(pearlChestPosition.getX(), pearlChestPosition.getY(), pearlChestPosition.getZ()) > 9){
								BaritoneUtil.goTo(pearlChestPosition.above());
								return;
							}

							openChest(pearlChestPosition);
							if(mc.screen instanceof AbstractContainerScreen handler){
								if(InventoryUtils.findItemHotbar(Items.ENDER_PEARL) == -1) {
									InventoryUtil.stealOnePearl(handler);
								}
								else {
									mc.screen.onClose();
									moverStatus = MOVER.WALKING_TO_THROWPEARL;
								}
							}

						}
						case WALKING_TO_THROWPEARL -> {
							if(mc.player.distanceToSqr(walkToPosition.getX(), walkToPosition.getY(), walkToPosition.getZ()) > 1){
								BaritoneUtil.goTo(walkToPosition);
							}
							else moverStatus = MOVER.THROWING_PEARL;
						}
						case THROWING_PEARL -> {
							throwPearl();

							if(hasThrownPearl){
								mc.options.keyUp.setDown(true);
								if(mc.player.fallDistance > 10){
									mc.options.keyUp.setDown(false);
									return;
								}
								if(mc.player.isDeadOrDying()){
									moverStatus = MOVER.LOOT;
								}
							}
						}
						case LOOT -> {
							if(mc.player.isDeadOrDying()) {
								mc.player.respawn();
								return;
							}
							if(InventoryUtil.isInventoryFull()){
								moverStatus = MOVER.WAIT_FOR_PEARL;
							}
							if(!(mc.screen instanceof AbstractContainerScreen)){
								openChest(getChest());
							}
							else {
                                AbstractContainerScreen handler = (AbstractContainerScreen) mc.screen;
								chestTicks++;
								for(int i = handler.getMenu().slots.size(); i > handler.getMenu().slots.size() + 36; i--){

									if(!mc.player.containerMenu.getSlot(i).hasItem()) return;
									if(chestTicks < chestDelay.getValue()) return;

									mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
									chestTicks=0;
								}
                            }
						}
					}
				}
				case LOADER -> {
					switch (loaderStatus) {

					}
				}
			}

			ticksPassed=0;
		}
	}

	@Subscribe
	private void onAddEntity(EventEntity.Add event){

		if(		event.getEntity() instanceof ThrownEnderpearl
				&& mode.getValue().equals(MODES.MOVER)
				&& moverStatus.equals(MOVER.THROWING_PEARL)){
			hasThrownPearl = true;
		}
		if(		event.getEntity() instanceof Player
				&& ((Player) event.getEntity()).getGameProfile().getName().equals(otherIGN.getValue())
				&& mode.getValue().equals(MODES.LOADER)){
			loaderStatus = LOADER.GO_BACK;
		}
	}
	@Subscribe
	private void onPacketReceive(EventPacket.Receive event){
		if(mc.player == null || mc.level == null) return;

		lagTimer.reset();

		if (event.getPacket() instanceof ClientboundSystemChatPacket systemChat && is2b.getValue()) {
			String contents = systemChat.content().getString();

			if (contents.startsWith(otherIGN.getValue()) && contents.contains("LOAD PEARL")) {
				loaderStatus = LOADER.LOAD_PEARL;
			}

		}
		else if (event.getPacket() instanceof ClientboundPlayerChatPacket chatPacket) {
			UUID senderID = chatPacket.sender();
			if (!is2b.getValue() && senderID == getUUID(otherIGN.getValue())) {

				String message = chatPacket.body().content();
				if (message.contains("LOAD PEARL")) loaderStatus = LOADER.LOAD_PEARL;

			}
		}
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
		if(hasThrownPearl) return;

		rotate(rotateYaw.getValue(), rotatePitch.getValue(), rotateStep.getValue());

        if (!InventoryUtil.isHolding(Items.ENDER_PEARL)) {
            int slot = InventoryUtils.findItemHotbar(Items.ENDER_PEARL);

            if (slot == -1) {
                RusherHackAPI.getNotificationManager().chat("You don't have a pearl in your inventory, weird");
                return;
            }

            mc.player.getInventory().selected = slot;

        } else return;

        if(isRotated()) mc.player.connection.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0));

	}
	protected void openChest(BlockPos pos){
		RusherHackAPI.getRotationManager().updateRotation(pos);
		if (isRotated(pos)) {
			mc.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, RusherHackAPI.getRotationManager().getLookRaycast(pos), 0));
		}
	}
	protected BlockPos getChest(){
		BlockPos closestChest = null;
		double shortestDistance = Integer.MAX_VALUE;

		for(BlockEntity chest : WorldUtils.getBlockEntities(true)){

			if(chest instanceof ChestBlockEntity){
				double distance = chest.getBlockPos().getCenter().distanceTo(mc.getCameraEntity().position());

				if(distance < shortestDistance) {
					shortestDistance = distance;
					closestChest = chest.getBlockPos();
				}
			}

		}

		return closestChest;
	}


	boolean isRotated(){
		return RusherHackAPI.getServerState().getPlayerPitch() == rotatePitch.getValue() && RusherHackAPI.getServerState().getPlayerYaw() == rotateYaw.getValue();
	}
	boolean isRotated(BlockPos pos){
		return RusherHackAPI.getRotationManager().isLookingAt(pos);
	}
	void rotate(float yaw, float pitch, float step){
		RusherHackAPI.getRotationManager().updateRotation(yaw, pitch, step);
	}

	protected enum MODES {
		MOVER,
		LOADER
	}
	protected enum MOVER{
		LOOT,
		WAIT_FOR_PEARL,
		WALKING_TO_CHEST,
		WALKING_TO_THROWPEARL,
		THROWING_PEARL,
		JUMPING_OFF
	}
	protected enum LOADER{
		LOAD_PEARL,
		GO_BACK,
		KILL_MOVER,
		WAITING
	}
}
