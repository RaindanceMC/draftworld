package de.raindancer.modules.draftworld.rules;

import de.raindancer.modules.draftworld.rules.StayRule.Settle;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StayRuleTest {

    private final StayRule rule = new StayRule();

    @Test
    void whereSomebodyStandsAndWhatIsOnDiskAgreeOrGetFixed() {
        assertThat(rule.settle(true, true, true)).isEqualTo(Settle.NOTHING);
        assertThat(rule.settle(false, false, true)).isEqualTo(Settle.NOTHING);
        assertThat(rule.settle(false, false, false)).isEqualTo(Settle.NOTHING);
        // got in some other way (an operator's /tp): their own things go to disk, the draft side goes on
        assertThat(rule.settle(true, false, true)).isEqualTo(Settle.TAKE_IN);
        // got out some other way (/spawn, a death, a restart): their own things come back
        assertThat(rule.settle(false, true, true)).isEqualTo(Settle.BRING_OUT);
        assertThat(rule.settle(false, true, false)).isEqualTo(Settle.BRING_OUT);
        // inside without the right to be: out, and with their own things if they had swapped
        assertThat(rule.settle(true, false, false)).isEqualTo(Settle.SEND_OUT);
        assertThat(rule.settle(true, true, false)).isEqualTo(Settle.BRING_OUT_AND_SEND_OUT);
    }

    @Test
    void onlyBuildersGetInAndNotInAdminMode() {
        assertThat(rule.refusal(true, false)).isEmpty();
        assertThat(rule.refusal(false, false)).contains("draftworld.not-allowed");
        assertThat(rule.refusal(true, true)).contains("draftworld.leave-admin-mode-first");
        assertThat(rule.refusal(false, true)).isEqualTo(Optional.of("draftworld.not-allowed"));
    }

    @Test
    void gameModesInsideComeFromTheList() {
        List<String> creativeOnly = List.of("creative", "SPECTATOR");
        assertThat(rule.gameModeAllowed(GameMode.CREATIVE, creativeOnly)).isTrue();
        assertThat(rule.gameModeAllowed(GameMode.SPECTATOR, creativeOnly)).isTrue();
        assertThat(rule.gameModeAllowed(GameMode.SURVIVAL, creativeOnly)).isFalse();
        assertThat(rule.gameModeAllowed(GameMode.SURVIVAL, List.of())).isTrue();
    }
}
