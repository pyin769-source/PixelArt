# Modrinth publishing guide

This file contains the prepared information for publishing PixelArt on Modrinth.

## Project information

- **Project name:** PixelArt
- **Suggested slug:** `pixelart`
- **Mod ID:** `woolmapart`
- **Project type:** Mod
- **License:** Choose the license that matches the author's intended distribution terms before publishing.
- **Supported game version:** Minecraft Java `26.3`
- **Loader:** Fabric
- **Environment:** Client and server
- **Java requirement:** Java 25 or newer

## Dependencies

Mark these as required dependencies:

- Fabric Loader `0.20.1` or newer
- Fabric API `0.165.0+26.3` or the matching Fabric API release for Minecraft 26.3

## Version upload

Use the built file:

```text
build/libs/woolmapart-1.0.0.jar
```

Suggested Modrinth version settings:

- **Version number:** `1.0.0`
- **Version name:** `PixelArt 1.0.0`
- **Release channel:** Release
- **Game versions:** `26.3`
- **Loaders:** Fabric
- **Primary file:** `woolmapart-1.0.0.jar`

## Project description

PixelArt converts images into Minecraft pixel art for Java Edition 26.3.

In Creative mode, `/pixelart` quickly creates a detailed 128x128 image using full-color Pixel Blocks and can be used with the Better Map workflow.

In Survival mode, craft a Painting Table, Blank Canvas, and Pixel Blocks. Place the materials in the table, select an image from the `mapart` folder, and let the table progressively create a Painted Canvas. A full 128x128 image requires 164 Pixel Blocks.

The project includes English and Simplified Chinese documentation. In-game Chinese localization is planned separately.

## Player installation flow

After publication, players can install PixelArt directly through a Modrinth-compatible launcher:

1. Search for **PixelArt**.
2. Select Minecraft `26.3`.
3. Select the Fabric loader.
4. Install PixelArt.
5. Allow the launcher to install required dependencies.
6. Launch the Fabric profile.

Players still need to create the `mapart` folder in the Minecraft game directory when they want to use custom images.

## Release checklist

- [ ] Confirm the project license.
- [ ] Upload `build/libs/woolmapart-1.0.0.jar`.
- [ ] Add Fabric Loader and Fabric API as required dependencies.
- [ ] Set Minecraft version to `26.3`.
- [ ] Select Fabric as the loader.
- [ ] Upload a PixelArt icon.
- [ ] Add the project description above.
- [ ] Add `README.md` and `README_zh-CN.md` links.
- [ ] Test installation through a Modrinth-compatible launcher.
- [ ] Test `/pixelart` in Creative mode.
- [ ] Test the Painting Table workflow in Survival mode.
