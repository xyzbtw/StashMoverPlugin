package dev.xyzbtw.commands;

import dev.xyzbtw.StashMover;
import net.minecraft.core.BlockPos;
import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

public class PositionCommand extends Command {

    public PositionCommand() {
        super("StashMoverPosition", "Sets the position to walk to");
    }

    @CommandExecutor
    private String runCommand(){
        if(mc.level == null || mc.player==null) return "Not in a world";

        BlockPos currentPos = mc.player.blockPosition();

        StashMover.walkToPosition = currentPos;

        return "Set position at " + currentPos.getX() + " " + currentPos.getY() + " " + currentPos.getZ();
    }

}
