# Auto Omen

A small client-side Fabric mod for Minecraft 26.2. While you AFK at a raid farm, it drinks an Ominous Bottle from your inventory so the next raid starts on its own.

## What it does

- Every tick it checks: the mod is on, you have neither Bad Omen nor Raid Omen, the timing setting allows it, your health is above the limit, no chest-like screen is open, and you carry an Ominous Bottle.
- Then it selects the bottle, drinks it and selects your previous hotbar slot again. A bottle in the main inventory is first swapped onto a free hotbar slot with a normal inventory click. A bottle in the offhand is drunk from the offhand.
- It never clicks the block you are looking at: the bottle is used directly.
- If two bottles in a row do not start a raid (raids turned off, not standing in a village, ...), it pauses itself and says so in chat instead of drinking all your bottles.

## Settings (Mod Menu, or `config/autoomen.json`)

| Setting | Values |
|---|---|
| Auto drink | on / off (also a toggle key, unbound by default) |
| When | **After the raid** (default): once the raid boss bar is gone. **At victory**: as soon as the bar shows "Raid - Victory" / "Raid - Defeat"; the next raid then starts about 30 s sooner. |
| Bottle | lowest level first / highest level first / only one level (I-V) |
| Don't drink below | a health limit in hearts, or no limit |
| Chat message | one line in chat after each bottle |

Why "At victory" is the earliest moment that never wastes a bottle is explained (in Chinese) in [docs/MECHANICS.md](docs/MECHANICS.md): a Raid Omen gained during a raid that is still running is merged into that raid instead of starting a new one.

## Requirements

Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API. Mod Menu is optional.

## Building

Gradle has to run on Java 25 (Fabric Loom 1.18):

```
./gradlew build -Dorg.gradle.java.home=<path to JDK 25>
```

## License

GPL-3.0-only, see [LICENSE](LICENSE). Made by Autyism.
