# Changelog

## 0.1.0+26.3

- The same mod for Minecraft 26.3 (Fabric API 0.161.0+26.3, Mod Menu 21). No changes in behaviour; the 26.2 version stays available.

## 0.1.0

First release, for Minecraft 26.2.

- Drinks an Ominous Bottle from your inventory when the next raid can be started, then switches back to the hotbar slot you were holding.
- Bottles in the main inventory are swapped onto a free hotbar slot first; bottles in the offhand are drunk from the offhand.
- Two timings: "After the raid" (default, waits until the raid boss bar is gone) and "At victory" (drinks as soon as the bar says Victory / Defeat, so the next raid starts about 30 seconds sooner without wasting the bottle).
- Bottle priority: lowest level first, highest level first, or only one chosen level.
- Health limit, optional chat message after each bottle, toggle key (unbound by default).
- Stops by itself after two bottles in a row that did not start a raid, so a broken setup does not eat all your bottles.
- Settings screen through Mod Menu. English and Simplified Chinese.
