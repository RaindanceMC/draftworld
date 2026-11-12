package de.raindancer.modules.draftworld.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRuleTest {

    private final CommandRule rule = new CommandRule();
    private final List<String> allowed = List.of("tp", "Gamemode", "draftworld");
    private final Set<String> fawe = Set.of("brush", "schem");

    private boolean allowed(String typed) {
        return rule.allowed(typed, allowed, fawe::contains);
    }

    @Test
    void theLabelIsTheFirstWordWithoutSlashOrNamespace() {
        assertThat(CommandRule.label("/tp Steve")).isEqualTo("tp");
        assertThat(CommandRule.label("/minecraft:tp Steve")).isEqualTo("tp");
        assertThat(CommandRule.label("//set stone")).isEqualTo("/set");
        assertThat(CommandRule.label("/GameMode creative")).isEqualTo("gamemode");
        assertThat(CommandRule.label("/")).isEmpty();
    }

    @Test
    void listedCommandsWorkWhateverTheCase() {
        assertThat(allowed("/tp 0 100 0")).isTrue();
        assertThat(allowed("/minecraft:gamemode creative")).isTrue();
        assertThat(allowed("/DRAFTWORLD")).isTrue();
    }

    @Test
    void worldEditsDoubleSlashCommandsAlwaysWork() {
        assertThat(allowed("//copy")).isTrue();
        assertThat(allowed("//paste -a")).isTrue();
    }

    @Test
    void commandsOfAllowedPluginsWork() {
        assertThat(allowed("/schem load house")).isTrue();
        assertThat(allowed("/brush sphere stone 3")).isTrue();
    }

    @Test
    void everythingElseIsRefused() {
        assertThat(allowed("/sell hand")).isFalse();
        assertThat(allowed("/shop")).isFalse();
        assertThat(allowed("/send Steve")).isFalse();
    }

    @Test
    void adminModeNeverSwitchesInsideEvenWhenListed() {
        assertThat(rule.allowed("/admin", List.of("admin"), label -> true)).isFalse();
        assertThat(rule.allowed("/rainsessentials:admin off", List.of("admin"), label -> true)).isFalse();
        assertThat(CommandRule.neverInside("admin")).isTrue();
    }
}
