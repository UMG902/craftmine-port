# Craftmine (26.3 port)

A Fabric mod that ports Minecraft's April Fools snapshot **25w14craftmine ("Craftmine")** to **26.3**.

Craft your own mines from ingredients in the Mine Crafter, mix ~110 world effects (eternal night, soul link, water world, explosive traps...), fight special-mine bosses, adopt pets and grind unlocks in the hub — rebuilt faithfully on the modern version.

> mostly vibe coded, but human tested

> Requires 25w14craftmine to be installed to work

## Download

The Modrinth page is under review — the download link will be added here once it is approved.

## What's inside

- **Mine crafting** — combine ingredients (world types, biomes, mob sets, effects, exits) in the Mine Crafter to generate a custom mine dimension
- **~110 world effects** and **9 special mines**, faithful to the snapshot
- **Pets, unlocks and advancements** with persistence across mine trips and restarts
- **Bug fixes** over the original snapshot (see below)

## Fixes over the original snapshot

Includes the fix pack from [BugMine](https://github.com/Mikarific/BugMine) by Mikarific (MIT; see THIRD_PARTY_NOTICES.md), always on:

- vanilla biome tags are written into crafted mines, so structures spawn as intended and the Dry Land ingredient works
- no more client freeze / softlock from Ctrl+Q, throwing or hotbar-swapping ingredients out of the Mine Crafter
- the Rabbits ingredient spawns rabbits (not parrots); the Wither Skeletons ingredient uses the right icon
- No Drops gets its 5%-per-kill unlock condition; In It Together is completable in any mine, not just special mines
- the dragon fire unlock is obtainable through the dragon's breath cloud, not only direct fireball hits
- soul link is usable when your singleplayer world is open to LAN

## Building from source

Requirements: **JDK 25**. Then:

```
./gradlew build
```

The jar lands in `build/libs/`. In-game you need Fabric Loader 0.19.5+ for 26.3 and Fabric API 0.163.0+26.3. Any launcher that can run Fabric 26.3 works (tested with SKLauncher).

## License

This project is under the **CRAFTMINE PORT LICENSE** — see [LICENSE](LICENSE). All rights reserved; do not redistribute or reupload without permission.

Minecraft is a trademark of Mojang Studios. This is an unofficial fan project, not affiliated with, endorsed by, or supported by Mojang Studios or Microsoft.

## Credits

- [Mikarific](https://github.com/Mikarific/BugMine) — BugMine (MIT), whose bug fixes are included in this port, see THIRD_PARTY_NOTICES.md
- Mojang Studios — for the April Fools snapshot this port is based on
