package de.raindancer.modules.draftworld.command;

import de.raindancer.modules.draftworld.DraftWorldServices;
import de.raindancer.modules.draftworld.util.PermissionNodes;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** {@code /draftworld}: in from anywhere, back out from inside. {@code back} only ever goes out. */
public final class DraftWorldCommand implements BasicCommand {

    private final Supplier<DraftWorldServices> services;

    public DraftWorldCommand(Supplier<DraftWorldServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        DraftWorldServices live = services.get();
        if (!(source.getSender() instanceof Player player)) {
            live.messages().send(source.getSender(), "draftworld.only-a-player");
            return;
        }
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean inside = live.trips().isDraftWorld(player.getWorld());
        switch (first) {
            case "" -> {
                if (inside) {
                    live.trips().goOut(player);
                } else {
                    live.trips().goIn(player);
                }
            }
            case "back", "out" -> {
                if (inside) {
                    live.trips().goOut(player);
                } else {
                    live.messages().send(player, "draftworld.not-inside");
                }
            }
            default -> live.messages().send(player, "draftworld.usage");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return args.length <= 1 && "back".startsWith(typed) ? List.of("back") : List.of();
    }

    @Override
    public String permission() {
        // Somebody inside who loses it is sent out on their next world change or join, without the command.
        return PermissionNodes.USE;
    }
}
