package dev.xyzbtw.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class BaritoneUtil {

    public static IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    public static void goTo(BlockPos pos){
        if(!baritone.getPathingBehavior().hasPath()){
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
        }
    }
    public static void goTo(Block block){
        if(!baritone.getPathingBehavior().hasPath()){
            baritone.getGetToBlockProcess().getToBlock(block);
        }
    }
    public static boolean isBaritonePathing(){
        return baritone.getPathingBehavior().isPathing();
    }
    public static void stopBaritone(){
        baritone.getPathingBehavior().cancelEverything();
    }

}
