package de.raindancer.modules.draftworld.service;

import de.raindancer.core.data.loadout.Loadout;
import de.raindancer.core.data.loadout.LoadoutStore;
import de.raindancer.modules.draftworld.DraftWorldSettings;
import de.raindancer.modules.draftworld.service.DraftWorldService.Outcome;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two lives of a builder. What must never happen: their own inventory written over, lost, or carried into the
 * draft world's — or a creative inventory carried out.
 */
class DraftWorldServiceTest {

    @TempDir
    Path folder;

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final FakeWardrobe wardrobe = new FakeWardrobe();
    private LoadoutStore store;
    private DraftWorldService service;

    private static final Loadout.Place AT_HOME = new Loadout.Place("world", 10, 70, 20, 90f, 0f);
    private static final Loadout.Place AT_THE_BUILD = new Loadout.Place("build", 1, -60, 2, 0f, 0f);

    /** Who is wearing what: the "items" are just the first slot, enough to tell sides apart. */
    private static final class FakeWardrobe implements DraftWorldService.Wardrobe {
        final Map<UUID, Loadout> wearing = new HashMap<>();
        boolean refuseToApply;

        @Override
        public Loadout capture(Player player) {
            return wearing.get(player.getUniqueId());
        }

        @Override
        public boolean apply(Player player, Loadout loadout) {
            if (refuseToApply) {
                return false;
            }
            wearing.put(player.getUniqueId(), loadout.withPlace(null));
            return true;
        }

        @Override
        public int unreadable(Loadout loadout) {
            return loadout.inventory().contains("BROKEN") ? 1 : 0;
        }
    }

    private static Loadout carrying(String item, String gameMode) {
        return new Loadout(List.of(item), List.of("ender-" + item), 3, 0.5f, 20, 20, 5f, gameMode, false, false,
                List.of(), null, Map.of());
    }

    private String wearing() {
        return wardrobe.wearing.get(id).inventory().getFirst();
    }

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(id);
        store = new LoadoutStore(folder);
        service = new DraftWorldService(store, wardrobe, () -> DraftWorldSettings.DEFAULTS, Runnable::run);
        wardrobe.wearing.put(id, carrying("diamond_sword", "SURVIVAL"));
    }

    @Test
    void goingInPutsTheirOwnThingsOnDiskAndAnEmptyCreativeSideOn() {
        assertThat(service.takeIn(player, AT_HOME)).isEqualTo(Outcome.DONE);

        assertThat(service.isInside(id)).isTrue();
        assertThat(wardrobe.wearing.get(id).hasItems()).isFalse();
        assertThat(wardrobe.wearing.get(id).enderChest()).allMatch(String::isEmpty);
        assertThat(wardrobe.wearing.get(id).gameMode()).isEqualTo("CREATIVE");
        assertThat(service.outsidePlace(id)).contains(AT_HOME);
    }

    @Test
    void comingOutGivesTheirOwnThingsBackAndKeepsTheDraftSideForNextTime() {
        service.takeIn(player, AT_HOME);
        wardrobe.wearing.put(id, carrying("creative_stack", "CREATIVE"));

        assertThat(service.bringOut(player, AT_THE_BUILD)).isEqualTo(Outcome.DONE);
        assertThat(wearing()).isEqualTo("diamond_sword");
        assertThat(wardrobe.wearing.get(id).gameMode()).isEqualTo("SURVIVAL");
        assertThat(service.isInside(id)).isFalse();
        assertThat(service.lastInside(id)).contains(AT_THE_BUILD);

        assertThat(service.takeIn(player, AT_HOME)).isEqualTo(Outcome.DONE);
        assertThat(wearing()).isEqualTo("creative_stack");
    }

    @Test
    void goingInTwiceNeverWritesOverTheirOwnThings() {
        service.takeIn(player, AT_HOME);
        wardrobe.wearing.put(id, carrying("creative_stack", "CREATIVE"));

        assertThat(service.takeIn(player, AT_THE_BUILD)).isEqualTo(Outcome.ALREADY);
        service.bringOut(player, AT_THE_BUILD);
        assertThat(wearing()).isEqualTo("diamond_sword");
    }

    @Test
    void comingOutWithoutHavingGoneInChangesNothing() {
        assertThat(service.bringOut(player, AT_THE_BUILD)).isEqualTo(Outcome.NOT_INSIDE);
        assertThat(wearing()).isEqualTo("diamond_sword");
    }

    @Test
    void aSideThatWillNotGoOnLeavesThemAsTheyWere() {
        wardrobe.refuseToApply = true;

        assertThat(service.takeIn(player, AT_HOME)).isEqualTo(Outcome.UNREADABLE);
        assertThat(service.isInside(id)).isFalse();
        assertThat(wearing()).isEqualTo("diamond_sword");
    }

    @Test
    void anUnreadableOwnSideStaysOnDiskAndTheyStayInside() {
        wardrobe.wearing.put(id, carrying("BROKEN", "SURVIVAL"));
        service.takeIn(player, AT_HOME);
        wardrobe.wearing.put(id, carrying("creative_stack", "CREATIVE"));

        assertThat(service.bringOut(player, AT_THE_BUILD)).isEqualTo(Outcome.UNREADABLE);
        assertThat(service.isInside(id)).isTrue();
        assertThat(wearing()).isEqualTo("creative_stack");
    }

    @Test
    void everyVisitStartsInCreativeEvenAfterLeavingInSpectator() {
        service.takeIn(player, AT_HOME);
        wardrobe.wearing.put(id, carrying("creative_stack", "SPECTATOR"));
        service.bringOut(player, AT_THE_BUILD);

        service.takeIn(player, AT_HOME);
        assertThat(wardrobe.wearing.get(id).gameMode()).isEqualTo("CREATIVE");
        assertThat(wearing()).isEqualTo("creative_stack");
    }

    @Test
    void aDraftSideInAGameModeNoLongerAllowedComesBackCreative() {
        service.takeIn(player, AT_HOME);
        wardrobe.wearing.put(id, carrying("creative_stack", "SURVIVAL"));
        service.bringOut(player, AT_THE_BUILD);

        service.takeIn(player, AT_HOME);
        assertThat(wardrobe.wearing.get(id).gameMode()).isEqualTo("CREATIVE");
    }

    @Test
    void aBrokenFileKeepsEverybodyWhereTheyAre() throws Exception {
        service.takeIn(player, AT_HOME);
        try (var files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.writeString(file, "this: is: not: yaml: [");
            }
        }
        // read again at the next login
        DraftWorldService afterALogin = new DraftWorldService(store, wardrobe, () -> DraftWorldSettings.DEFAULTS,
                Runnable::run);
        afterALogin.prepare(id);
        assertThat(afterALogin.isInside(id)).as("a broken file counts as inside").isTrue();
        assertThat(afterALogin.bringOut(player, AT_THE_BUILD)).isEqualTo(Outcome.FILE_BROKEN);
        assertThat(afterALogin.takeIn(player, AT_HOME)).isEqualTo(Outcome.FILE_BROKEN);
    }

    @Test
    void theWritesGoOutInOrderAndOffTheCallersThread() {
        List<Runnable> queued = new java.util.ArrayList<>();
        DraftWorldService later = new DraftWorldService(store, wardrobe, () -> DraftWorldSettings.DEFAULTS,
                queued::add);
        later.prepare(id);
        queued.removeFirst().run();

        assertThat(later.takeIn(player, AT_HOME)).isEqualTo(Outcome.DONE);
        assertThat(later.isInside(id)).as("answered from memory before the write lands").isTrue();
        assertThat(store.has(id, "own")).as("nothing written on the caller's thread").isFalse();
        queued.forEach(Runnable::run);
        queued.clear();
        assertThat(store.has(id, "own")).isTrue();

        wardrobe.wearing.put(id, carrying("creative_stack", "CREATIVE"));
        assertThat(later.bringOut(player, AT_THE_BUILD)).isEqualTo(Outcome.DONE);
        assertThat(store.has(id, "own")).as("still on disk until the queue runs").isTrue();
        queued.forEach(Runnable::run);
        assertThat(store.has(id, "own")).isFalse();
        assertThat(store.load(id, "draft")).hasValueSatisfying(
                draft -> assertThat(draft.inventory().getFirst()).isEqualTo("creative_stack"));
    }

    @Test
    void aLoginReadsWhatTheLastVisitLeft() {
        service.takeIn(player, AT_HOME);
        DraftWorldService afterARestart = new DraftWorldService(store, wardrobe, () -> DraftWorldSettings.DEFAULTS,
                Runnable::run);
        afterARestart.prepare(id);
        assertThat(afterARestart.isInside(id)).isTrue();
        assertThat(afterARestart.outsidePlace(id)).contains(AT_HOME);
    }
}
