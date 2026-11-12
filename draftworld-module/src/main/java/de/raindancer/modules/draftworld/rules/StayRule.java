package de.raindancer.modules.draftworld.rules;

import org.bukkit.GameMode;

import java.util.List;
import java.util.Optional;

/** Who may be in the draft world, and what to do when where somebody stands and what is on disk disagree. */
public final class StayRule {

    /** What to do about one player. */
    public enum Settle {
        NOTHING,
        /** Inside without having swapped: their own things to disk, the draft side on. */
        TAKE_IN,
        /** Outside with their own things still on disk: put them back on. */
        BRING_OUT,
        /** Inside without the right to be, and never swapped: only move them out. */
        SEND_OUT,
        /** Inside without the right to be, swapped: their own things back on, then out. */
        BRING_OUT_AND_SEND_OUT
    }

    /**
     * @param inDraftWorld whether they stand in it
     * @param ownSideSaved whether their own side is on disk — the one truth about having gone in
     * @param mayBeThere   whether they may be in it at all
     */
    public Settle settle(boolean inDraftWorld, boolean ownSideSaved, boolean mayBeThere) {
        if (!inDraftWorld) {
            return ownSideSaved ? Settle.BRING_OUT : Settle.NOTHING;
        }
        if (!mayBeThere) {
            return ownSideSaved ? Settle.BRING_OUT_AND_SEND_OUT : Settle.SEND_OUT;
        }
        return ownSideSaved ? Settle.NOTHING : Settle.TAKE_IN;
    }

    /**
     * Why somebody may not go in, as a message key; empty when they may. Admin mode is a second inventory swap of its
     * own, and the two must not be stacked.
     */
    public Optional<String> refusal(boolean mayEnter, boolean inAdminMode) {
        if (!mayEnter) {
            return Optional.of("draftworld.not-allowed");
        }
        return inAdminMode ? Optional.of("draftworld.leave-admin-mode-first") : Optional.empty();
    }

    /** An empty list allows every game mode. */
    public boolean gameModeAllowed(GameMode mode, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return mode != null && allowed.stream().anyMatch(name -> name != null && name.strip().equalsIgnoreCase(mode.name()));
    }
}
