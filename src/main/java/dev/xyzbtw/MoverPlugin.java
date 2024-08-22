package dev.xyzbtw;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;


public class MoverPlugin extends Plugin {
	public static final StashMover stashMoverModule = new StashMover();
	@Override
	public void onLoad() {
		RusherHackAPI.getModuleManager().registerFeature(stashMoverModule);
		stashMoverModule.blacklistChests.clear();
	}
	
	@Override
	public void onUnload() {
		stashMoverModule.blacklistChests.clear();
		this.getLogger().info("StashMover plugin successfully" + " unloaded!");
	}

}
