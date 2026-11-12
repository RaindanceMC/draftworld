package de.raindancer.modules.draftworld.util;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/** What this module asks about somebody. Registered in code, since a hosted module has no descriptor. */
public final class PermissionNodes {

    /** Going into the draft world and being in it. Give it to the builder rank. */
    public static final String USE = "rainsdraftworld.use";

    /** Every command works inside the draft world, not only the listed ones. Admin mode still never does. */
    public static final String ANY_COMMAND = "rainsdraftworld.anycommand";

    private PermissionNodes() {
    }

    public static List<Permission> declared() {
        return List.of(
                new Permission(USE, "Go into the draft world with /draftworld", PermissionDefault.OP),
                new Permission(ANY_COMMAND, "Use every command inside the draft world", PermissionDefault.FALSE));
    }

    /** Registers whatever is not registered yet. @return how many were added */
    public static int register(Server server) {
        if (server == null) {
            return 0;
        }
        int added = 0;
        for (Permission permission : declared()) {
            if (server.getPluginManager().getPermission(permission.getName()) != null) {
                continue;
            }
            try {
                server.getPluginManager().addPermission(permission);
                added++;
            } catch (IllegalArgumentException alreadyThere) {
                // Registered by another copy between the check and the add. Nothing to do.
            }
        }
        return added;
    }
}
