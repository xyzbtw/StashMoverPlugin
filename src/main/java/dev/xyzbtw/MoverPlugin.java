package dev.xyzbtw;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;


public class MoverPlugin extends Plugin {
	
	@Override
	public void onLoad() {
		final StashMover stashMoverModule = new StashMover();
		RusherHackAPI.getModuleManager().registerFeature(stashMoverModule);

		final NewChunks newChunksModule = new NewChunks();
		//RusherHackAPI.getModuleManager().registerFeature(newChunksModule);

	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("StashMover plugin successfully" + " unloaded!");
	}

}
