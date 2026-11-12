package de.raindancer.modules.draftworld.model;

/** What a newly made draft world looks like. Only read when the world does not exist yet. */
public enum Terrain {
    /** Grass on a few layers of dirt, flat to the horizon — the classic build world. */
    FLAT,
    /** Nothing at all. */
    VOID,
    /** Ordinary generated terrain. */
    NORMAL
}
