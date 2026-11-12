package de.raindancer.modules.draftworld;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.draftworld.command.DraftWorldCommand;
import de.raindancer.modules.draftworld.util.PermissionNodes;

import java.util.List;

/**
 * Declared at bootstrap, filled in when the module starts — Paper registers commands before any plugin is enabled, so
 * the command points at {@link #require} until then.
 */
public final class DraftWorldCommands {

    private static volatile DraftWorldServices services;

    private DraftWorldCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(ModuleCommand.of("draftworld", "Goes into the draft world, or back to where you were",
                        new DraftWorldCommand(DraftWorldCommands::require))
                .aliased("buildworld")
                .taking("(nothing) — in, or back out", "back — back to where you were")
                .needing(PermissionNodes.USE));
    }

    static void ready(DraftWorldServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    private static DraftWorldServices require() {
        DraftWorldServices live = services;
        if (live == null) {
            throw new IllegalStateException("the draftworld module is not running");
        }
        return live;
    }
}
