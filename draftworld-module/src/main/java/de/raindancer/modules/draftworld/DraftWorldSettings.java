package de.raindancer.modules.draftworld;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import de.raindancer.modules.draftworld.model.Terrain;
import org.bukkit.Material;

import java.util.List;

/** What an owner decides about the draft world. The schema of the file and of the /settings page. */
@Settings(id = "draftworld", topics = {
        @Topic(path = "draftworld", title = "Draft world", icon = Material.SCAFFOLDING),
        @Topic(path = "draftworld/inside", title = "Inside the draft world", icon = Material.COMMAND_BLOCK),
})
public record DraftWorldSettings(

        @In("draftworld") @Title("World name")
        @Describe("The world builders go to with /draftworld. Made the first time the server starts with this module; "
                + "a new name takes effect at the next restart and makes a new world (the old one stays on disk).")
        @Key("world")
        String world,

        @In("draftworld") @Title("Terrain of a new draft world")
        @Describe("FLAT: grass to the horizon. VOID: nothing at all. NORMAL: ordinary terrain. Only used when the "
                + "world is made, never on an existing one.")
        @Key("terrain")
        Terrain terrain,

        @In("draftworld") @Title("Keep the world still")
        @Describe("Always midday, no weather, no mobs spawning, no fire spreading, nothing growing or decaying, and "
                + "nothing lost on death — a world that stays exactly as it was built.")
        @Key("still")
        boolean still,

        @In("draftworld/inside") @Title("Game mode on a first visit")
        @Describe("What somebody is in on their very first visit. After that they come back in whatever they left in.")
        @Key("first-game-mode")
        String firstGameMode,

        @In("draftworld/inside") @Title("Game modes allowed inside")
        @Describe("Names, comma separated. Survival inside would let somebody level skills and jobs with what creative "
                + "gave them, so it is not on the list by default. Empty allows every game mode.")
        @Key("game-modes")
        List<String> gameModes,

        @In("draftworld/inside") @Title("Commands that work inside")
        @Describe("Names without the slash, comma separated. Everything else is refused inside the draft world, so "
                + "that no shop, mail or trade carries creative items out. WorldEdit's //commands always work; "
                + "admin mode never does.")
        @Key("commands")
        List<String> commands,

        @In("draftworld/inside") @Title("Plugins whose commands all work inside")
        @Describe("Plugin names, comma separated. Every command these plugins register works inside, under any of its "
                + "names.")
        @Key("command-plugins")
        List<String> commandPlugins) {

    public DraftWorldSettings {
        world = world == null || world.isBlank() ? "build" : world.strip();
        terrain = terrain == null ? Terrain.FLAT : terrain;
        firstGameMode = firstGameMode == null || firstGameMode.isBlank() ? "CREATIVE" : firstGameMode.strip();
        gameModes = gameModes == null ? List.of() : List.copyOf(gameModes);
        commands = commands == null ? List.of() : List.copyOf(commands);
        commandPlugins = commandPlugins == null ? List.of() : List.copyOf(commandPlugins);
    }

    public static final DraftWorldSettings DEFAULTS = new DraftWorldSettings("build", Terrain.FLAT, true, "CREATIVE",
            List.of("CREATIVE", "SPECTATOR"),
            List.of("draftworld", "gamemode", "gm", "gmc", "gmsp", "tp", "teleport", "tphere", "tppos", "time", "ptime",
                    "weather", "pweather", "speed", "flyspeed", "walkspeed", "fly", "top", "up", "jump", "ascend",
                    "descend", "thru", "give", "i", "item", "clear", "effect", "fill", "setblock", "clone", "fillbiome",
                    "place", "summon", "kill", "msg", "tell", "w", "whisper", "r", "reply", "me", "help", "list",
                    "spawn", "back", "home", "homes", "warp", "warps", "afk"),
            List.of("FastAsyncWorldEdit", "WorldEdit"));

    /** The game mode of a first visit, or creative for a name the game does not know. */
    public org.bukkit.GameMode firstMode() {
        try {
            return org.bukkit.GameMode.valueOf(firstGameMode.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return org.bukkit.GameMode.CREATIVE;
        }
    }
}
