package de.raindancer.modules.draftworld.service;

import de.raindancer.core.data.loadout.Loadout;
import de.raindancer.core.data.loadout.LoadoutStore;
import de.raindancer.core.data.loadout.Loadouts;
import de.raindancer.modules.draftworld.DraftWorldSettings;
import de.raindancer.modules.draftworld.model.Side;
import de.raindancer.modules.draftworld.rules.StayRule;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Swapping a builder's two lives — their own and the draft world's — the way {@code /admin} swaps staff's.
 *
 * <h2>The disk is the truth</h2>
 * Somebody is inside exactly while their own side is on disk. Its write is queued before the draft side goes on and its
 * delete only after it has come back on, on one writer that keeps that order, so a crash leaves their own things on disk
 * or on them — never lost. What is on disk is read at login and answered from memory, because the store is the
 * network's database. Both swaps run on the player's own thread; moving them is the caller's.
 */
public final class DraftWorldService {

    public enum Outcome { DONE, ALREADY, NOT_INSIDE, FILE_BROKEN, COULD_NOT_SAVE, UNREADABLE }

    /** Taking a player's things off and putting others on. {@link Loadouts} on a server, a fake in a test. */
    public interface Wardrobe {
        Loadout capture(Player player);

        boolean apply(Player player, Loadout loadout);

        int unreadable(Loadout loadout);

        static Wardrobe of(Loadouts loadouts) {
            return new Wardrobe() {
                @Override
                public Loadout capture(Player player) {
                    return loadouts.capture(player);
                }

                @Override
                public boolean apply(Player player, Loadout loadout) {
                    return loadouts.apply(player, loadout);
                }

                @Override
                public int unreadable(Loadout loadout) {
                    return loadouts.unreadable(loadout);
                }
            };
        }
    }

    /** What is on disk for one player, as last read or written. */
    private record Sides(boolean readable, Loadout own, Loadout draft) {
    }

    private static final de.raindancer.core.platform.log.LogChannel log =
            de.raindancer.core.platform.log.Log.of("draftworld");

    private final LoadoutStore store;
    private final Wardrobe wardrobe;
    private final Supplier<DraftWorldSettings> settings;
    private final java.util.concurrent.Executor writer;
    private final StayRule rule = new StayRule();
    private final java.util.Map<UUID, Sides> known = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param writer runs every read and write of the store, one after another and in order — the store is the
     *               network's database, which must not be waited for on the thread running the world
     */
    public DraftWorldService(LoadoutStore store, Wardrobe wardrobe, Supplier<DraftWorldSettings> settings,
                             java.util.concurrent.Executor writer) {
        this.store = store;
        this.wardrobe = wardrobe;
        this.settings = settings;
        this.writer = writer;
    }

    /**
     * Reads a player's sides before they need them — at login, on the login thread. Queued behind any write still
     * going out for them, so somebody who quit and came straight back reads what they left.
     */
    public java.util.concurrent.CompletableFuture<Void> prepare(UUID player) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> known.put(player, read(player)), writer);
    }

    /** Forgets a player who left, once whatever was still being written for them is out. */
    public void forget(UUID player) {
        writer.execute(() -> known.remove(player));
    }

    private Sides read(UUID player) {
        if (!store.readable(player)) {
            return new Sides(false, null, null);
        }
        return new Sides(true, store.load(player, Side.OWN).orElse(null), store.load(player, Side.DRAFT).orElse(null));
    }

    /** From memory; read on the spot only for somebody nobody prepared (a reload with players on). */
    private Sides sides(UUID player) {
        return known.computeIfAbsent(player, this::read);
    }

    private void write(UUID player, String side, Loadout loadout) {
        writer.execute(() -> {
            if (!store.save(player, side, loadout)) {
                log.error("The {} side of {} could not be written. It is still held in memory until they leave.",
                        side, player);
            }
        });
    }

    /** Inside as far as the disk knows. A file that will not read counts as inside: their own things may be in it. */
    public boolean isInside(UUID player) {
        Sides sides = sides(player);
        return !sides.readable() || sides.own() != null;
    }

    /** Where they stood before going in. */
    public Optional<Loadout.Place> outsidePlace(UUID player) {
        return Optional.ofNullable(sides(player).own()).map(Loadout::place);
    }

    /** Where they stood when they last came out. */
    public Optional<Loadout.Place> lastInside(UUID player) {
        return Optional.ofNullable(sides(player).draft()).map(Loadout::place);
    }

    /**
     * Their own side to disk, the draft side on. The write is queued before the draft side goes on, so it is out
     * before anything else is written for them.
     *
     * @param from where they stood before, to go back to
     */
    public Outcome takeIn(Player player, Loadout.Place from) {
        UUID id = player.getUniqueId();
        Sides sides = sides(id);
        if (!sides.readable()) {
            return Outcome.FILE_BROKEN;
        }
        if (sides.own() != null) {
            return Outcome.ALREADY;
        }
        Loadout captured = wardrobe.capture(player);
        if (captured == null) {
            return Outcome.COULD_NOT_SAVE;
        }
        Loadout own = captured.withPlace(from);
        DraftWorldSettings now = settings.get();
        Loadout draft = sides.draft() != null ? sides.draft() : Loadout.empty(now.firstMode().name());
        if (!rule.gameModeAllowed(modeOf(draft), now.gameModes())) {
            draft = inMode(draft, now.firstMode());
        }
        known.put(id, new Sides(true, own, sides.draft()));
        write(id, Side.OWN, own);
        if (!wardrobe.apply(player, draft)) {
            known.put(id, sides);
            writer.execute(() -> store.delete(id, Side.OWN));
            return Outcome.UNREADABLE;
        }
        return Outcome.DONE;
    }

    /**
     * Their own side back on, the draft side kept for next time. Not moving them: wherever they are going, they
     * arrive as themselves. Their own side leaves the disk only after the draft side is on it.
     *
     * @param lastInside where they stood inside, to come back to next time
     */
    public Outcome bringOut(Player player, Loadout.Place lastInside) {
        UUID id = player.getUniqueId();
        Sides sides = sides(id);
        if (!sides.readable()) {
            return Outcome.FILE_BROKEN;
        }
        Loadout own = sides.own();
        if (own == null) {
            return Outcome.NOT_INSIDE;
        }
        if (wardrobe.unreadable(own) > 0) {
            // Staying inside keeps their own things safe on disk until the server can read them again.
            return Outcome.UNREADABLE;
        }
        Loadout captured = wardrobe.capture(player);
        if (captured == null) {
            return Outcome.COULD_NOT_SAVE;
        }
        Loadout draft = captured.withPlace(lastInside);
        if (!wardrobe.apply(player, own)) {
            return Outcome.UNREADABLE;
        }
        known.put(id, new Sides(true, null, draft));
        write(id, Side.DRAFT, draft);
        writer.execute(() -> store.delete(id, Side.OWN));
        return Outcome.DONE;
    }

    private static GameMode modeOf(Loadout loadout) {
        try {
            return GameMode.valueOf(loadout.gameMode());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Loadout inMode(Loadout loadout, GameMode mode) {
        return new Loadout(loadout.inventory(), loadout.enderChest(), loadout.level(), loadout.exp(), loadout.health(),
                loadout.food(), loadout.saturation(), mode.name(), loadout.allowFlight(), loadout.flying(),
                loadout.effects(), loadout.place(), loadout.data());
    }
}
