package dev.xyzbtw;

import dev.xyzbtw.utils.BaritoneUtil;
import dev.xyzbtw.utils.InventoryUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.HitResult;
import org.rusherhack.client.api.RusherHackAPI;
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
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;
import org.rusherhack.core.utils.Timer;

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
	private final NumberSetting<Integer> delay = new NumberSetting<>("Delay", "Delay between like thingy things in ticks", 50, 0, 300);
	private final NumberSetting<Integer> chestDelay = new NumberSetting<>("ChestDelay", "Delay betweenc chest clicks", 2, 0, 10).setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final BooleanSetting is2b = new BooleanSetting("2b2t", "Changes chatpackets from player to system", true);
	private final NumberSetting<Float> rotateStep = new NumberSetting<>("RotationStep", 40f, 0f, 180f)
			.incremental(0.1f)
			.setVisibility(()-> mode.getValue().equals(MODES.MOVER));
	private final StringSetting otherIGN = new StringSetting("OtherIGN", "The username of the other person that's moving stash", "xyzbtwballs");

	/**
	 * variables
	 */

	private MOVER moverStatus = MOVER.WAIT_FOR_PEARL;
	private LOADER loaderStatus = LOADER.WAITING;
	boolean hasThrownPearl = false;
	int ticksPassed, chestTicks = 0;
	String LOADPEARLMSG = "LOAD PEARL";
	public static BlockPos walkToPosition, LOADER_BACK_POSITION, pearlChestPosition, chestForLoot;
	Timer lagTimer = new Timer();

	/**
	 * rotations[0] is pitch
	 * rotations[1] is yaw
	 */
	float[] rotations = null;

	/**
	 * constructor
	 */
	public StashMover() {
		super("StashMover", "Moves stashes with pearls", ModuleCategory.CLIENT);
		this.registerSettings(
				this.mode,
				this.delay,
				this.chestDelay,
				this.is2b,
				this.rotateStep,
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
			if(pearlChestPosition == null || chestForLoot==null || walkToPosition == null){
				ChatUtils.print("One of your positions isn't set big boy");
				return;
			}
		}else{
			if(walkToPosition==null){
				ChatUtils.print("One of your positions isn't set big boy");
				return;
			}
		}

		if(lagTimer.passed(1000)) return;


		ticksPassed++;

		if(ticksPassed>delay.getValue()) {
			switch (mode.getValue()) {
				case MOVER -> {
					switch (moverStatus) {
						case SEND_LOAD_PEARL_MSG -> {
							mc.player.connection.sendChat(LOADPEARLMSG);
                        }
						case WAIT_FOR_PEARL -> {
							if(mc.player.distanceToSqr(pearlChestPosition.getX(), pearlChestPosition.getY(), pearlChestPosition.getZ()) > 9){
								BaritoneUtil.goTo(freeBlockAroundChest(pearlChestPosition));
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
									hasThrownPearl=false;
								}
							}
						}
						case LOOT -> {
							if(mc.player.isDeadOrDying()) {
								mc.player.respawn();
								return;
							}
							if(InventoryUtils.isInventoryFull()){
								moverStatus = MOVER.SEND_LOAD_PEARL_MSG;
							}
							if(!(mc.screen instanceof AbstractContainerScreen)){
								openChest(getChest());
							}
							else {
                                AbstractContainerScreen handler = (AbstractContainerScreen) mc.screen;
								chestTicks++;
								for(int i = 0; i < handler.getMenu().slots.size() - 36; i++){
									if(!mc.player.containerMenu.getSlot(i).hasItem()) return;
									if(chestTicks < chestDelay.getValue()) return;

									InventoryUtils.clickSlot(i, true);
									chestTicks=0;
								}
                            }
						}
						case WALKING_TO_CHEST -> {

							if(mc.player.distanceToSqr(chestForLoot.getX(), chestForLoot.getY(), chestForLoot.getZ()) > 9){
								BaritoneUtil.goTo(freeBlockAroundChest(chestForLoot));
								return;
							}
							if(!(mc.screen instanceof AbstractContainerScreen handler)){
								openChest(chestForLoot);
								return;
							}
                            chestTicks++;
							for(int i = handler.getMenu().slots.size(); i > handler.getMenu().slots.size() - 36; i--){
								if(InventoryUtil.isInventoryEmpty()){
									moverStatus = MOVER.WAIT_FOR_PEARL;
								}
								if(!mc.player.containerMenu.getSlot(i).hasItem()) return;
								if(chestTicks < chestDelay.getValue()) return;

								InventoryUtils.clickSlot(i, true);
								chestTicks=0;
							}
						}
					}
				}
				case LOADER -> {
					switch (loaderStatus) {
						case WAITING -> {
							LOADER_BACK_POSITION = null;
							return;
						}
						case LOAD_PEARL -> {
							if(LOADER_BACK_POSITION == null)
								LOADER_BACK_POSITION = mc.player.blockPosition();

							BaritoneUtil.goTo(walkToPosition);
						}
						case GO_BACK -> {
							if(mc.player.distanceToSqr(LOADER_BACK_POSITION.getX(), LOADER_BACK_POSITION.getY(), LOADER_BACK_POSITION.getZ()) > 16)
								BaritoneUtil.goTo(LOADER_BACK_POSITION);
							else loaderStatus = LOADER.WAITING;
						}

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
				&& ((Player) event.getEntity()).getGameProfile().getName().equals(otherIGN.getValue())){
			loaderStatus = LOADER.GO_BACK;
			moverStatus = MOVER.WALKING_TO_CHEST;
		}
	}
	@Subscribe
	private void onPacketReceive(EventPacket.Receive event){
		if(mc.player == null || mc.level == null) return;

		lagTimer.reset();

		if (event.getPacket() instanceof ClientboundSystemChatPacket systemChat && is2b.getValue()) {
			String contents = systemChat.content().getString();

			if (contents.startsWith(otherIGN.getValue()) && contents.contains(LOADPEARLMSG)) {
				loaderStatus = LOADER.LOAD_PEARL;
			}

		}
		else if (event.getPacket() instanceof ClientboundPlayerChatPacket chatPacket) {
			UUID senderID = chatPacket.sender();
			if (!is2b.getValue() && senderID == getUUID(otherIGN.getValue())) {

				String message = chatPacket.body().content();
				if (message.contains(LOADPEARLMSG)) loaderStatus = LOADER.LOAD_PEARL;

			}
		}
	}
	protected String getLookPos(String string){
		BlockPos lookPos = null;
		if(mc.level!=null) {
			if (mc.hitResult == null) return "No hitresult, look at the block";

			if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";


			lookPos = new BlockPos((int) mc.hitResult.getLocation().x, (int)mc.hitResult.getLocation().y,(int) mc.hitResult.getLocation().z);


			if(string.equalsIgnoreCase("pearlchest")) {
				StashMover.pearlChestPosition = lookPos;
			}else if (string.equalsIgnoreCase("lootchest"))
				StashMover.chestForLoot = lookPos;
			else{
				return "USAGE: *stashmover chest pearlchest OR *stashmover chest lootchest";
			}
		}
		return lookPos==null ? "You're not in a world??" : "Successfully set to the pos you were looking at";
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

			@CommandExecutor(subCommand = "walkPos")
			private String SetWalkPos(){
				if(mc.level == null || mc.player==null || mc.getCameraEntity() == null) return "Not in a world";

				BlockPos currentPos = mc.getCameraEntity().blockPosition();

				StashMover.walkToPosition = currentPos;

				return "Set position to current position";
			}
			@CommandExecutor(subCommand = "rotations")
			private String setRotations(){
				if(mc.level == null || mc.player==null || mc.getCameraEntity() == null) return "Not in a world";
				rotations[0] = RusherHackAPI.getServerState().getPlayerPitch();
				rotations[1] = RusherHackAPI.getServerState().getPlayerYaw();

				return "Set rotations." + " Yaw: " + rotations[1] + ". Pitch: " + rotations[0];
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
		if(hasThrownPearl) return;

		rotate(rotations[1], rotations[0], rotateStep.getValue());

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

	boolean isRotated(){
		return RusherHackAPI.getServerState().getPlayerPitch() == rotations[0] && RusherHackAPI.getServerState().getPlayerYaw() == rotations[1];
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
		SEND_LOAD_PEARL_MSG,
		WAIT_FOR_PEARL,
		WALKING_TO_CHEST,
		WALKING_TO_THROWPEARL,
		THROWING_PEARL
	}
	protected enum LOADER{
		LOAD_PEARL,
		GO_BACK,
		WAITING
	}
}
