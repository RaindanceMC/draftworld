package de.raindancer.modules.draftworld;

import de.raindancer.core.data.loadout.LoadoutStore;
import de.raindancer.core.data.loadout.Loadouts;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.draftworld.listener.DraftWorldListener;
import de.raindancer.modules.draftworld.service.DraftWorldService;
import de.raindancer.modules.draftworld.service.DraftWorldTrips;
import de.raindancer.modules.draftworld.service.DraftWorlds;
import de.raindancer.modules.draftworld.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * A world of its own for builders. {@code /draftworld} takes them in with a separate inventory, ender chest and game
 * mode, and back out to where they were. Nobody else gets in. Because it is a world on the same server, WorldEdit's
 * clipboard and schematics work across both without anything from this module.
 */
public final class DraftWorldModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("draftworld", "Draft World", "0.1.0")
            .describedAs("A separate world for builders with its own inventory: /draftworld in and back out")
            .by("Raindancer118");

    /** Every read and write of the sides, in order, off the server's threads. */
    private volatile java.util.concurrent.ExecutorService writer;

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        SettingsStore<DraftWorldSettings> settings = context.settings(DraftWorldSettings.class,
                DraftWorldSettings.DEFAULTS);
        context.core().messages().defineFrom(DraftWorldModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(DraftWorldModule.class.getResourceAsStream("messages-serious.yml"));
        Server server = context.plugin().getServer();
        PermissionNodes.register(server);

        World world = DraftWorlds.open(settings.current(), context.log());
        writer = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RainsDraftWorld sides");
            thread.setDaemon(true);
            return thread;
        });
        DraftWorldService service = new DraftWorldService(
                new LoadoutStore(context.dataFolder().resolve("sides")),
                DraftWorldService.Wardrobe.of(Loadouts.ofTheServer(context.core().flight())), settings::current,
                writer);
        DraftWorldTrips trips = new DraftWorldTrips(service, settings::current, context.core().messages());

        context.listener(new DraftWorldListener(trips, settings::current, context.core().messages(), server));
        // Whatever else moves people (homes, warps, teleport requests) asks before it does.
        try {
            context.closeWith(context.core().worldEntryRules().register("draftworld", (player, target) ->
                    trips.isDraftWorld(target) && !trips.isDraftWorld(player.getWorld())
                            ? trips.refusal(player).map(key -> context.core().messages().prefixed(key))
                            : Optional.empty()));
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore has no world entry rules: only the teleport itself is checked.");
        }
        // Somebody already online (a reload): their sides are read off the server's threads, then put right.
        for (Player online : server.getOnlinePlayers()) {
            java.util.UUID id = online.getUniqueId();
            service.prepare(id).thenRun(() -> Scheduling.entity(context.plugin(), online, () -> trips.settle(online)));
        }
        DraftWorldCommands.ready(new DraftWorldServices(context.core().messages(), trips));

        if (world == null) {
            context.log().warn("Draft world is up without its world — /draftworld says so until it loads.");
        } else {
            context.log().info("Draft world '{}' is ready ({}).", world.getName(),
                    settings.current().still() ? "kept still" : "running normally");
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return DraftWorldCommands.declared();
    }

    @Override
    public void disable() {
        DraftWorldCommands.stopped();
        java.util.concurrent.ExecutorService running = writer;
        writer = null;
        if (running != null) {
            // Whatever is still queued is somebody's inventory: it goes out before the plugin does.
            running.shutdown();
            try {
                running.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
