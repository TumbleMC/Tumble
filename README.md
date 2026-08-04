# Tumble

Tumble is a Minecraft server fork based on [Lophine](https://github.com/LophineLabs/Lophine).
It uses the Paper/Comfreyweight patch workflow and currently targets Minecraft 26.2.

## Features
- Purpur Anvil Setting.
- Configuration to bypass anvil "Too Expensive!" limit.
- Carpet TIS Addition LargeBarrel Support.

## Requirements

- Git
- JDK 25 or newer

## Building

```bash
./gradlew applyAllPatches
./gradlew createPaperclipJar
```

The runnable server JAR is generated in `tumble-server/build/libs`.

## Development & Contribution

TumbleMC welcomes everyone to contribute. Follow the instructions below to contribute your code!  

Apply the patch stack before editing sources:

```bash
./gradlew applyAllPatches
```

Edit API code in `paper-api` or fork-specific API code in `lophine-api`. Edit server code in
`paper-server`, `lophine-server`, or `tumble-server/src/minecraft`. Commit changes in the generated
Git repository, then rebuild the corresponding patches. Common tasks include:

```bash
./gradlew rebuildPaperApiFeaturePatches
./gradlew :tumble-server:rebuildPaperServerFeaturePatches
./gradlew :tumble-server:rebuildMinecraftFeaturePatches
./gradlew rebuildLophineSingleFilePatches
```

Do not edit generated `.patch` files manually.

## Upstream

The Lophine upstream is pinned in `gradle.properties` so builds are reproducible. Update
`lophineRef`, apply the patches, resolve any conflicts in the generated source trees, and rebuild
the affected patches when updating upstream.

## License

Tumble inherits the GNU General Public License version 3 from Lophine, Folia, Paper, Spigot,
Bukkit, and CraftBukkit. Some upstream contributions are available under more permissive terms;
see `LICENSE.md` and the files under `licenses`.
