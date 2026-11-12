package de.raindancer.modules.draftworld.model;

/** The two lives a builder has, as profile names in the loadout store. */
public final class Side {

    /** Their own life on the server. On disk exactly while they are in the draft world. */
    public static final String OWN = "own";

    /** What they carry and are in the draft world, kept between visits. */
    public static final String DRAFT = "draft";

    private Side() {
    }
}
