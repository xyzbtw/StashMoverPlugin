package dev.xyzbtw.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import net.minecraft.core.BlockPos;

public class BaritoneUtil {

    static IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    public static void goTo(BlockPos pos){
        if(!baritone.getPathingBehavior().hasPath()){
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
        }
    }




}
