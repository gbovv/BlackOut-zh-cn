package kassuk.addon.blackout.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

/**
 * @author KassuK
 */

public class BlackoutGit extends Command {
    public BlackoutGit() {
        super("blackoutinfo", "显示Blackout的GitHub地址");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("项目GitHub地址：https://github.com/KassuK1/BlackOut");
            return SINGLE_SUCCESS;
        });
    }
}
