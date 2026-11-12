package de.raindancer.modules.draftworld.listener;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.draftworld.DraftWorldSettings;
import de.raindancer.modules.draftworld.rules.CommandRule;
import de.raindancer.modules.draftworld.rules.StayRule;
import de.raindancer.modules.draftworld.service.DraftWorldTrips;
import de.raindancer.modules.draftworld.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The draft world's border, whichever way somebody crosses it: the command, a /tp, a home, a warp, a death, a restart.
 * Every crossing swaps their two lives, so no creative item ever walks out and nobody's own things walk in.
 */
public final class DraftWorldListener implements Listener {

    private final DraftWorldTrips trips;
    private final Supplier<DraftWorldSettings> settings;
    private final Messages messages;
    private final Server server;
    private final CommandRule commands = new CommandRule();
    private final StayRule stay = new StayRule();

    public DraftWorldListener(DraftWorldTrips trips, Supplier<DraftWorldSettings> settings, Messages messages,
                              Server server) {
        this.trips = trips;
        this.settings = settings;
        this.messages = messages;
        this.server = server;
    }

    /** Early, so everybody after sees it cancelled: who may not come in stays out. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void gate(PlayerTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo().getWorld();
        if (from == to || !trips.isDraftWorld(to) || trips.isDraftWorld(from)) {
            return;
        }
        Optional<String> refused = trips.refusal(event.getPlayer());
        if (refused.isPresent()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), refused.get());
        }
    }

    /** Last, once nobody else will stop it: the swap itself. A swap that fails stops the teleport. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo().getWorld();
        if (from == to) {
            return;
        }
        boolean fromDraft = trips.isDraftWorld(from);
        boolean toDraft = trips.isDraftWorld(to);
        if (toDraft && !fromDraft && !trips.crossingIn(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        } else if (fromDraft && !toDraft && !trips.crossingOut(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        }
    }

    /** No portals out of the draft world: a nether portal built as decoration is not a way to the nether. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void portal(PlayerPortalEvent event) {
        if (trips.isDraftWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "draftworld.portal-refused");
        }
    }

    /** Whatever slipped past a teleport — a respawn, a plugin moving people without an event — is put right here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void changedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        trips.settle(player);
        // Whatever else set a game mode on the way in (a plugin, a world default), inside it is the entry one.
        if (trips.isDraftWorld(player.getWorld())
                && !stay.gameModeAllowed(player.getGameMode(), settings.get().gameModes())) {
            player.setGameMode(settings.get().firstMode());
        }
    }

    /** Their sides are read here, on the login thread, so nothing waits for the database inside a tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void login(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            trips.service().prepare(event.getUniqueId()).join();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        trips.service().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        trips.settle(event.getPlayer());
    }

    /** Dying inside — creative rarely does — wakes them up inside again, not as themselves in the draft world. */
    @EventHandler(priority = EventPriority.HIGH)
    public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (trips.isDraftWorld(player.getWorld()) && player.hasPermission(PermissionNodes.USE)) {
            trips.draftWorld().ifPresent(world -> event.setRespawnLocation(world.getSpawnLocation()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!trips.isDraftWorld(player.getWorld())) {
            return;
        }
        String label = CommandRule.label(event.getMessage());
        if (CommandRule.neverInside(label)) {
            event.setCancelled(true);
            messages.send(player, "draftworld.admin-refused");
            return;
        }
        if (player.hasPermission(PermissionNodes.ANY_COMMAND)) {
            return;
        }
        DraftWorldSettings now = settings.get();
        if (!commands.allowed(event.getMessage(), now.commands(), this::ownedByAllowedPlugin)) {
            event.setCancelled(true);
            messages.send(player, "draftworld.command-refused", "command", label);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!trips.isDraftWorld(player.getWorld()) || trips.isSwapping(player.getUniqueId())) {
            return;
        }
        if (!stay.gameModeAllowed(event.getNewGameMode(), settings.get().gameModes())) {
            event.setCancelled(true);
            messages.send(player, "draftworld.game-mode-refused",
                    "mode", event.getNewGameMode().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private boolean ownedByAllowedPlugin(String label) {
        Command command = server.getCommandMap().getCommand(label);
        if (!(command instanceof PluginIdentifiableCommand owned)) {
            return false;
        }
        String plugin = owned.getPlugin().getName();
        return settings.get().commandPlugins().stream().anyMatch(name -> name.equalsIgnoreCase(plugin));
    }
}
