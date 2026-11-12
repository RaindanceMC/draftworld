# RaindanceMC/draftworld

- **`draftworld-module`** — Rain's Flexible Modules — Draft World: a separate world for builders. `/draftworld` takes them in with an inventory, ender chest, experience and game mode of its own, and back out to where they were. Every crossing swaps the two (the command, `/tp`, homes, warps, a death, a restart), so nothing made in creative walks out. Only listed commands work inside; admin mode and portals never do. Nobody without `rainsdraftworld.use` gets in. Being a world on the same server, WorldEdit's clipboard and schematics work across both.
- **`draftworld-standalone`** — Rain's Draft World, as a plugin of its own: the module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`). The real-server scenario is `DraftWorldScenarioTest` in `RaindanceMC/e2e`.

## Plugins

- `draftworld-standalone/target/RainsDraftWorld-0.1.0.jar` — also published as `de.raindancer.modules:draftworld-standalone:0.1.0`

## Permissions

- `rainsdraftworld.use` — go in and be inside (default: op). Give it to the builder rank.
- `rainsdraftworld.anycommand` — every command works inside (default: nobody). Admin mode still never does.

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
