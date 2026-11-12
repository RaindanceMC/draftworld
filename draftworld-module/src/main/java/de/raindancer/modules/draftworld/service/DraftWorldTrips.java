package de.raindancer.modules.draftworld.service;

import de.raindancer.core.data.loadout.Loadout;
import de.raindancer.core.moderation.players.AdminModes;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.draftworld.DraftWorldSettings;
import de.raindancer.modules.draftworld.rules.StayRule;
import de.raindancer.modules.draftworld.util.PermissionNodes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Going in and out, and putting right anybody whose world and disk disagree. The swaps themselves are
 * {@link DraftWorldService}'s; this is where they meet Bukkit — teleports, the main world, the wording.
 */
public final class DraftWorldTrips {

    private final DraftWorldService service;
    private final Supplier<DraftWorldSettings> settings;
    private final Messages messages;
    private final StayRule rule = new StayRule();

    /** Being swapped right now: the game mode their own side puts back must not be refused by the draft world's list. */
    private final Set<UUID> swapping = ConcurrentHashMap.newKeySet();

    public DraftWorldTrips(DraftWorldService service, Supplier<DraftWorldSettings> settings, Messages messages) {
        this.service = service;
        this.settings = settings;
        this.messages = messages;
    }

    public DraftWorldService service() {
        return service;
    }

    public boolean isDraftWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(settings.get().world());
    }

    public Optional<World> draftWorld() {
        return Optional.ofNullable(Bukkit.getWorld(settings.get().world()));
    }

    public boolean isSwapping(UUID player) {
        return swapping.contains(player);
    }

    /** Why this player may not go in right now, as a message key. */
    public Optional<String> refusal(Player player) {
        return rule.refusal(player.hasPermission(PermissionNodes.USE), AdminModes.isOn(player.getUniqueId()));
    }

    // ------------------------------------------------------------------------ the command

    /** {@code /draftworld} from outside. */
    public void goIn(Player player) {
        Optional<String> refused = refusal(player);
        if (refused.isPresent()) {
            messages.send(player, refused.get());
            return;
        }
        World world = draftWorld().orElse(null);
        if (world == null) {
            messages.send(player, "draftworld.no-world", "world", settings.get().world());
            return;
        }
        Location target = service.lastInside(player.getUniqueId())
                .filter(place -> place.world().equalsIgnoreCase(world.getName()))
                .map(DraftWorldTrips::location)
                .orElseGet(() -> arrival(world));
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(arrived -> {
            if (arrived) {
                messages.send(player, "draftworld.entered");
            }
        });
    }

    /** {@code /draftworld} from inside: back to where they were. */
    public void goOut(Player player) {
        player.teleportAsync(outsideTarget(player.getUniqueId()), PlayerTeleportEvent.TeleportCause.PLUGIN)
                .thenAccept(arrived -> {
                    if (arrived) {
                        messages.send(player, "draftworld.left");
                    }
                });
    }

    // ------------------------------------------------------------------------ swaps on any teleport

    /** Their own side to disk and the draft side on, as they cross in. @return false to stop the teleport */
    public boolean crossingIn(Player player, Location from) {
        return said(player, swap(player, () -> service.takeIn(player, place(from))));
    }

    /** Their own side back on, as they cross out — whichever way they leave. @return false to stop the teleport */
    public boolean crossingOut(Player player, Location from) {
        return said(player, swap(player, () -> service.bringOut(player, place(from))));
    }

    /**
     * Puts right somebody whose world and disk disagree: in without having swapped (an operator's /tp), out without
     * having swapped back (a restart, a death), or inside without the right to be.
     */
    public void settle(Player player) {
        boolean inside = isDraftWorld(player.getWorld());
        switch (rule.settle(inside, service.isInside(player.getUniqueId()), player.hasPermission(PermissionNodes.USE))) {
            case NOTHING -> {
            }
            case TAKE_IN -> {
                if (said(player, swap(player, () -> service.takeIn(player, place(mainSpawn()))))) {
                    messages.send(player, "draftworld.taken-in");
                }
            }
            case BRING_OUT -> {
                if (said(player, swap(player, () -> service.bringOut(player, null)))) {
                    messages.send(player, "draftworld.brought-out");
                }
            }
            case SEND_OUT -> sendOut(player);
            case BRING_OUT_AND_SEND_OUT -> {
                if (said(player, swap(player, () -> service.bringOut(player, place(player.getLocation()))))) {
                    sendOut(player);
                }
            }
        }
    }

    private void sendOut(Player player) {
        messages.send(player, "draftworld.sent-out");
        player.teleportAsync(outsideTarget(player.getUniqueId()), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private DraftWorldService.Outcome swap(Player player, Supplier<DraftWorldService.Outcome> doing) {
        UUID id = player.getUniqueId();
        swapping.add(id);
        try {
            return doing.get();
        } finally {
            swapping.remove(id);
        }
    }

    /** Says what went wrong, if anything. @return whether the trip may go on */
    private boolean said(Player player, DraftWorldService.Outcome outcome) {
        String key = switch (outcome) {
            case DONE, ALREADY, NOT_INSIDE -> null;
            case FILE_BROKEN -> "draftworld.file-broken";
            case COULD_NOT_SAVE -> "draftworld.could-not-save";
            case UNREADABLE -> "draftworld.unreadable";
        };
        if (key != null) {
            messages.send(player, key);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------ places

    /** Where they stood before going in, or the main world's spawn. */
    public Location outsideTarget(UUID player) {
        return service.outsidePlace(player)
                .filter(place -> Bukkit.getWorld(place.world()) != null)
                .filter(place -> !place.world().equalsIgnoreCase(settings.get().world()))
                .map(DraftWorldTrips::location)
                .orElseGet(DraftWorldTrips::mainSpawn);
    }

    private static Location mainSpawn() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    /** The draft world's spawn, standing on whatever is there rather than inside it. */
    private static Location arrival(World world) {
        Location spawn = world.getSpawnLocation();
        int top = world.getHighestBlockYAt(spawn);
        if (top > world.getMinHeight()) {
            spawn.setY(top + 1);
        }
        return spawn.toCenterLocation().subtract(0, 0.5, 0);
    }

    static Loadout.Place place(Location at) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        return new Loadout.Place(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
    }

    static Location location(Loadout.Place place) {
        return new Location(Bukkit.getWorld(place.world()), place.x(), place.y(), place.z(), place.yaw(),
                place.pitch());
    }
}
