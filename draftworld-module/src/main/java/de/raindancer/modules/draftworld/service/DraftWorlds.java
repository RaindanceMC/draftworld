package de.raindancer.modules.draftworld.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.world.manage.VoidGenerator;
import de.raindancer.modules.draftworld.DraftWorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/** Loading the draft world, or making it the first time. Bukkit only; checked on a real server. */
public final class DraftWorlds {

    private DraftWorlds() {
    }

    /** @return the world, or null when the server would not load or make it (said in the log) */
    public static World open(DraftWorldSettings settings, LogChannel log) {
        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            WorldCreator creator = new WorldCreator(settings.world()).generateStructures(false);
            switch (settings.terrain()) {
                case FLAT -> creator.type(WorldType.FLAT);
                case VOID -> creator.type(WorldType.FLAT).generator(VoidGenerator.empty());
                case NORMAL -> creator.type(WorldType.NORMAL);
            }
            try {
                world = creator.createWorld();
            } catch (RuntimeException failure) {
                log.error(failure, "The draft world '{}' could not be loaded or made.", settings.world());
                return null;
            }
            if (world == null) {
                log.error("The server would not load or make the draft world '{}'.", settings.world());
                return null;
            }
        }
        if (settings.still()) {
            keepStill(world);
        }
        return world;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void keepStill(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.DO_VINES_SPREAD, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
    }
}
