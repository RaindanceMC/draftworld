package de.raindancer.modules.draftworld.rules;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which commands work inside the draft world. A list of what works rather than of what does not: a command nobody
 * thought of — a new shop, a new way to mail items — must not be the hole creative items walk out through.
 */
public final class CommandRule {

    /**
     * Never inside, whatever the lists say: switching admin mode swaps inventories too, and two swaps in each other's
     * middle would put the draft world's things in somebody's survival life.
     */
    private static final Set<String> NEVER = Set.of("admin", "adminmode", "staffmode");

    /** {@code /minecraft:tp Steve} → {@code tp}; WorldEdit's {@code //set} → {@code /set}. Lower case. */
    public static String label(String typed) {
        String text = typed == null ? "" : typed.strip();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        String first = space < 0 ? text : text.substring(0, space);
        int colon = first.indexOf(':');
        if (colon >= 0 && !first.startsWith("/")) {
            first = first.substring(colon + 1);
        }
        return first.toLowerCase(Locale.ROOT);
    }

    public static boolean neverInside(String label) {
        return NEVER.contains(label);
    }

    /**
     * @param ownedByAllowedPlugin whether a label belongs to a plugin all of whose commands work inside (WorldEdit)
     */
    public boolean allowed(String typed, Collection<String> allowedLabels, Predicate<String> ownedByAllowedPlugin) {
        String label = label(typed);
        if (label.isEmpty()) {
            return true;
        }
        if (neverInside(label)) {
            return false;
        }
        if (label.startsWith("/")) {
            return true;
        }
        for (String allowed : allowedLabels) {
            if (allowed != null && allowed.strip().equalsIgnoreCase(label)) {
                return true;
            }
        }
        return ownedByAllowedPlugin != null && ownedByAllowedPlugin.test(label);
    }
}
