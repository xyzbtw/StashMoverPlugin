package dev.xyzbtw.commands;

import dev.xyzbtw.StashMover;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.HitResult;
import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

public class ChestCommand extends Command {

    public ChestCommand() {
        super("StashMoverChest", "Sets the chest for stashmover");
    }


    @CommandExecutor
    private String runCommand(){
        BlockPos lookPos = null;

        if(mc.level!=null) {
            if (mc.hitResult == null) return "No hitresult, look at the block";

            if (mc.hitResult.getType() != HitResult.Type.BLOCK) return "You're not looking at a block big boy";

            if (mc.level.getBlockState(new BlockPos(
                    (int) mc.hitResult.getLocation().x,
                    (int) mc.hitResult.getLocation().y,
                    (int) mc.hitResult.getLocation().z))
                    .getBlock() instanceof ChestBlock) return "You're not looking at a chest big boy";

            lookPos = new BlockPos((int) mc.hitResult.getLocation().x, (int)mc.hitResult.getLocation().y,(int) mc.hitResult.getLocation().z);

            StashMover.pearlChestPosition = lookPos;
        }

        return lookPos==null ? "You're not in a world??" : "Successfully set pos at " + lookPos.getX() + " " + lookPos.getY() + " " + lookPos.getZ();
    }


}
