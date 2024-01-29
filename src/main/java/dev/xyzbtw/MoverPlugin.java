package dev.xyzbtw;

import dev.xyzbtw.commands.ChestCommand;
import dev.xyzbtw.commands.PositionCommand;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;


public class MoverPlugin extends Plugin {
	
	@Override
	public void onLoad() {
		final ChestCommand chestCommand = new ChestCommand();
		RusherHackAPI.getCommandManager().registerFeature(chestCommand);

		final PositionCommand positionCommand = new PositionCommand();
		RusherHackAPI.getCommandManager().registerFeature(positionCommand);

		final StashMover stashMoverModule = new StashMover();
		RusherHackAPI.getModuleManager().registerFeature(stashMoverModule);

	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("StashMover plugin successfully" + " unloaded!");
	}

}
