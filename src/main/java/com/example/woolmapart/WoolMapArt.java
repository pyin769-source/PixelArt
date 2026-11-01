package com.example.woolmapart;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Registry;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /mapart <file> [x y z] [shaded]
 * Converts an image from the "mapart" folder (game/server directory) to wool and
 * builds it in the 128x128 map-aligned area (scale 0) containing the player.
 * "shaded" uses a staircase for 3 tones per wool colour; otherwise the build is flat.
 */
public class WoolMapArt implements ModInitializer {
	private static final int SIZE = 128;
	private static final int PIXELS_PER_BLOCK = 100;
	private static final double DITHER_STRENGTH = 0.16;
	private static final double MAX_DITHER_ERROR = 36.0;

	private static final Block[] MATERIALS = {
		Blocks.WOOL.white(), Blocks.WOOL.orange(), Blocks.WOOL.magenta(), Blocks.WOOL.lightBlue(),
		Blocks.WOOL.yellow(), Blocks.WOOL.lime(), Blocks.WOOL.pink(), Blocks.WOOL.gray(),
		Blocks.WOOL.lightGray(), Blocks.WOOL.cyan(), Blocks.WOOL.purple(), Blocks.WOOL.blue(),
		Blocks.WOOL.brown(), Blocks.WOOL.green(), Blocks.WOOL.red(), Blocks.WOOL.black(),
		Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.magenta(),
		Blocks.DYED_TERRACOTTA.lightBlue(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.lime(),
		Blocks.DYED_TERRACOTTA.pink(), Blocks.DYED_TERRACOTTA.gray(), Blocks.DYED_TERRACOTTA.lightGray(),
		Blocks.DYED_TERRACOTTA.cyan(), Blocks.DYED_TERRACOTTA.purple(), Blocks.DYED_TERRACOTTA.blue(),
		Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.green(), Blocks.DYED_TERRACOTTA.red(),
		Blocks.DYED_TERRACOTTA.black(),
		Blocks.CONCRETE.white(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.magenta(),
		Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.yellow(), Blocks.CONCRETE.lime(),
		Blocks.CONCRETE.pink(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.lightGray(),
		Blocks.CONCRETE.cyan(), Blocks.CONCRETE.purple(), Blocks.CONCRETE.blue(),
		Blocks.CONCRETE.brown(), Blocks.CONCRETE.green(), Blocks.CONCRETE.red(), Blocks.CONCRETE.black()
	};
	// Palette colors in the same order as MATERIALS above.
	private static final int[] BASE = {
		0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
		0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21,
		0xD2B1A1, 0x9D5123, 0x94566C, 0x766F8C, 0xB88221, 0x677535, 0xA65151, 0x3A2B24,
		0x876B62, 0x565C5C, 0x7B4A59, 0x4A3B5B, 0x4F3524, 0x4E552C, 0x8D3A2D, 0x251710,
		0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
		0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
	};
	// 0: lower than north neighbour (dark), 1: same height (normal), 2: higher (bright)
	private static final int[] SHADES = {180, 220, 255};
	public static final int[] PIXEL_COLORS = createPixelPalette();
	public static final PixelBlock PIXEL_BLOCK = registerPixelBlock();
	public static final PaintingTableBlock PAINTING_TABLE = registerPaintingTable();
	public static final net.minecraft.world.level.block.entity.BlockEntityType<PaintingTableBlockEntity>
		PAINTING_TABLE_BLOCK_ENTITY = registerPaintingTableBlockEntity();
	public static final BetterMapItem BETTER_MAP = registerBetterMap();
	public static final BetterMapItem FILLED_BETTER_MAP = registerFilledBetterMap();
	public static final Item BLANK_CANVAS = registerSimpleItem("blank_canvas", 16);
	public static final PaintedCanvasItem PAINTED_CANVAS = registerPaintedCanvas();
	public static final MenuType<PaintingTableMenu> PAINTING_TABLE_MENU = registerPaintingTableMenu();
	private static final Map<PaintingTaskKey, PaintingTask> PAINTING_TASKS = new HashMap<>();
	public static final ResourceKey<CreativeModeTab> PIXELART_TAB_KEY = ResourceKey.create(
		BuiltInRegistries.CREATIVE_MODE_TAB.key(),
		Identifier.fromNamespaceAndPath("woolmapart", "pixelart")
	);
	public static final CreativeModeTab PIXELART_TAB = net.fabricmc.fabric.api.creativetab.v1
		.FabricCreativeModeTab.builder()
		.icon(() -> new ItemStack(PAINTED_CANVAS))
		.title(Component.translatable("creativeTab.woolmapart.pixelart"))
		.alignedRight()
		.displayItems((parameters, output) -> {
			output.accept(PIXEL_BLOCK);
			output.accept(PAINTING_TABLE);
			output.accept(BETTER_MAP);
			output.accept(FILLED_BETTER_MAP);
			output.accept(BLANK_CANVAS);
			output.accept(PAINTED_CANVAS);
		})
		.build();

	private static PixelBlock registerPixelBlock() {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", "pixel_block");
		ResourceKey<Block> key = ResourceKey.create(BuiltInRegistries.BLOCK.key(), id);
		ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		PixelBlock block = Registry.register(
			BuiltInRegistries.BLOCK,
			id,
			new PixelBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(state -> BetterMapItem.mapColorFor(
					PIXEL_COLORS[state.getValue(PixelBlock.COLOR)]))
				.strength(0.8F))
		);
		Registry.register(
			BuiltInRegistries.ITEM,
			id,
			new BlockItem(block, new Item.Properties().setId(itemKey))
		);
		return block;
	}

	private static BetterMapItem registerBetterMap() {
		return registerMapItem("better_map", true);
	}

	private static BetterMapItem registerFilledBetterMap() {
		return registerMapItem("filled_better_map", false);
	}

	private static Item registerSimpleItem(String path, int stackSize) {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", path);
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		return Registry.register(BuiltInRegistries.ITEM, id,
			new Item(new Item.Properties().setId(key).stacksTo(stackSize)));
	}

	private static PaintedCanvasItem registerPaintedCanvas() {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", "painted_canvas");
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		return Registry.register(BuiltInRegistries.ITEM, id,
			new PaintedCanvasItem(new Item.Properties().setId(key)));
	}

	private static BetterMapItem registerMapItem(String path, boolean captures) {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", path);
		ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		BetterMapItem item = new BetterMapItem(new Item.Properties().setId(itemKey), captures);
		return Registry.register(
			BuiltInRegistries.ITEM,
			id,
			item
		);
	}

	private static PaintingTableBlock registerPaintingTable() {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", "painting_table");
		ResourceKey<Block> blockKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), id);
		ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		PaintingTableBlock block = Registry.register(
			BuiltInRegistries.BLOCK,
			id,
			new PaintingTableBlock(BlockBehaviour.Properties.of().setId(blockKey).strength(2.5F))
		);
		Registry.register(
			BuiltInRegistries.ITEM,
			id,
			new BlockItem(block, new Item.Properties().setId(itemKey))
		);
		return block;
	}

	private static MenuType<PaintingTableMenu> registerPaintingTableMenu() {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", "painting_table");
		return Registry.register(BuiltInRegistries.MENU, id,
			new ExtendedMenuType<>(PaintingTableMenu::new, BlockPos.STREAM_CODEC));
	}

	private static net.minecraft.world.level.block.entity.BlockEntityType<PaintingTableBlockEntity>
	registerPaintingTableBlockEntity() {
		Identifier id = Identifier.fromNamespaceAndPath("woolmapart", "painting_table");
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
			new net.minecraft.world.level.block.entity.BlockEntityType<>(
				PaintingTableBlockEntity::new, java.util.Set.of(PAINTING_TABLE)));
	}

	private static int[] createPixelPalette() {
		int[] palette = new int[256];
		int index = 0;
		for (int r = 0; r < 6; r++) {
			for (int g = 0; g < 6; g++) {
				for (int b = 0; b < 6; b++) {
					palette[index++] = (r * 51 << 16) | (g * 51 << 8) | (b * 51);
				}
			}
		}
		int[] extraColors = {
			0xFFF1E6, 0xF8D5C2, 0xF2C2A8, 0xE8B08F, 0xD99A78, 0xC78363,
			0xB66D50, 0x9B523F, 0x7A3F32, 0xF6E2C8, 0xEAC5A2, 0xC98B62,
			0xA86A45, 0x8A4F32, 0xF2F2F2, 0xD9D9D9, 0xBFBFBF, 0xA6A6A6,
			0x8C8C8C, 0x737373, 0x595959, 0x404040, 0x2B2B2B, 0x151515,
			0xFFE8D6, 0xF6C6A8, 0xE4A47E, 0xC97956, 0xA75A43, 0x8D4939,
			0x6F332B, 0x4D251F, 0xFFF8F0, 0xEDD6C3, 0xD8B7A0, 0xBE9179,
			0x986A58, 0x765044, 0x52382F, 0x34241F
		};
		for (int color : extraColors) {
			palette[index++] = color;
		}
		return palette;
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PIXELART_TAB_KEY, PIXELART_TAB);
		PaintingTablePayloads.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> tickPaintingTasks(server));
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			var command = Commands.literal("mapart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			command.executes(c -> listImages(c.getSource()));
			var file = Commands.argument("name", StringArgumentType.string()).suggests(WoolMapArt::suggestImages);
			file.executes(c -> runAtPlayer(c.getSource(), StringArgumentType.getString(c, "name"), false));
			file.then(Commands.argument("shaded", BoolArgumentType.bool())
				.executes(c -> runAtPlayer(c.getSource(), StringArgumentType.getString(c, "name"),
					BoolArgumentType.getBool(c, "shaded"))));

			var x = Commands.argument("x", IntegerArgumentType.integer());
			var y = Commands.argument("y", IntegerArgumentType.integer());
			var z = Commands.argument("z", IntegerArgumentType.integer());
			z.executes(c -> run(c.getSource(), StringArgumentType.getString(c, "name"),
				IntegerArgumentType.getInteger(c, "x"),
				IntegerArgumentType.getInteger(c, "y"),
				IntegerArgumentType.getInteger(c, "z"), false));
			z.then(Commands.argument("shaded", BoolArgumentType.bool())
				.executes(c -> run(c.getSource(), StringArgumentType.getString(c, "name"),
					IntegerArgumentType.getInteger(c, "x"),
					IntegerArgumentType.getInteger(c, "y"),
					IntegerArgumentType.getInteger(c, "z"),
					BoolArgumentType.getBool(c, "shaded"))));
			y.then(z);
			x.then(y);
			file.then(x);
			command.then(file);
			dispatcher.register(command);

			var pixelCommand = Commands.literal("pixelart")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			var pixelFile = Commands.argument("name", StringArgumentType.string())
				.suggests(WoolMapArt::suggestImages);
			pixelFile.executes(c -> runPixelAtPlayer(c.getSource(),
				StringArgumentType.getString(c, "name")));
			var pixelX = Commands.argument("x", IntegerArgumentType.integer());
			var pixelY = Commands.argument("y", IntegerArgumentType.integer());
			var pixelZ = Commands.argument("z", IntegerArgumentType.integer());
			pixelZ.executes(c -> runPixel(c.getSource(), StringArgumentType.getString(c, "name"),
				IntegerArgumentType.getInteger(c, "x"),
				IntegerArgumentType.getInteger(c, "y"),
				IntegerArgumentType.getInteger(c, "z")));
			pixelY.then(pixelZ);
			pixelX.then(pixelY);
			pixelFile.then(pixelX);
			pixelCommand.then(pixelFile);
			dispatcher.register(pixelCommand);
		});
	}

	static void selectPainting(ServerPlayer player, PaintingTableMenu menu, String filename) {
		try {
			pixelColors(filename);
			menu.setSelectedFilename(filename);
			int available = countPixelBlocks(player, menu.tablePos());
			PaintingTablePayloads.sendStatus(player, requiredPixelBlocks(),
				Math.max(0, requiredPixelBlocks() - available),
				"Image is ready. The table contains " + available + " pixel_block items.");
			player.sendSystemMessage(Component.literal("Selected " + filename + "."));
		} catch (Exception e) {
			PaintingTablePayloads.sendStatus(player, requiredPixelBlocks(), requiredPixelBlocks(),
				"Error: " + e.getMessage());
		}
	}

	static void startPainting(Player player, BlockPos tablePos) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) return;
		if (!level.getBlockState(tablePos).is(PAINTING_TABLE)) return;
		PaintingTableMenu menu = player.containerMenu instanceof PaintingTableMenu paintingMenu
			? paintingMenu : null;
		String filename = menu == null ? "" : menu.selectedFilename();
		if (filename.isBlank()) {
			PaintingTablePayloads.sendStatus(serverPlayer, requiredPixelBlocks(), requiredPixelBlocks(),
				"Check an image filename before creating the canvas.");
			return;
		}
		PaintingTableBlockEntity table = tableEntity(serverPlayer, tablePos);
		if (table == null || !table.hasBlankCanvas()) {
			PaintingTablePayloads.sendStatus(serverPlayer, requiredPixelBlocks(), requiredPixelBlocks(),
				"Insert a Blank Canvas into the canvas slot before creating the canvas.");
			return;
		}
		if (!table.getPaintedCanvas().isEmpty()) {
			PaintingTablePayloads.sendStatus(serverPlayer, requiredPixelBlocks(), requiredPixelBlocks(),
				"Remove the finished Painted Canvas before creating another one.");
			return;
		}
		try {
			int[][] colors = pixelColors(filename);
			PaintingTaskKey key = new PaintingTaskKey(level, tablePos);
			if (!PAINTING_TASKS.containsKey(key)) {
				PAINTING_TASKS.put(key, new PaintingTask(level, tablePos, serverPlayer.getUUID(), colors));
			}
			PaintingTablePayloads.sendStatus(serverPlayer, requiredPixelBlocks(), requiredPixelBlocks(),
				"Canvas construction started. It pauses when the table runs out of pixel_block items.");
		} catch (Exception e) {
			PaintingTablePayloads.sendStatus(serverPlayer, requiredPixelBlocks(), requiredPixelBlocks(),
				"Error: " + e.getMessage());
		}
	}

	private static int requiredPixelBlocks() {
		return (SIZE * SIZE + PIXELS_PER_BLOCK - 1) / PIXELS_PER_BLOCK;
	}

	public static int mapCenter(int coordinate) {
		return Math.floorDiv(coordinate, SIZE) * SIZE + SIZE / 2;
	}

	private static int remainingPixelBlocks(int pixelIndex) {
		int remainingPixels = SIZE * SIZE - pixelIndex;
		return (remainingPixels + PIXELS_PER_BLOCK - 1) / PIXELS_PER_BLOCK;
	}

	private static int[][] pixelColors(String name) throws Exception {
		BufferedImage image = loadImage(name);
		int[][] colors = new int[SIZE][SIZE];
		quantizePixelPalette(image, colors);
		return colors;
	}

	private static PaintingTableBlockEntity tableEntity(ServerPlayer player, BlockPos tablePos) {
		if (tablePos == null || !(player.level() instanceof ServerLevel level)) return null;
		return level.getBlockEntity(tablePos) instanceof PaintingTableBlockEntity table ? table : null;
	}

	private static int countPixelBlocks(ServerPlayer player, BlockPos tablePos) {
		PaintingTableBlockEntity table = tableEntity(player, tablePos);
		return table == null ? 0 : table.countPixelBlocks();
	}

	private static void tickPaintingTasks(net.minecraft.server.MinecraftServer server) {
		Iterator<Map.Entry<PaintingTaskKey, PaintingTask>> iterator = PAINTING_TASKS.entrySet().iterator();
		while (iterator.hasNext()) {
			PaintingTask task = iterator.next().getValue();
			if (!task.tick(server)) iterator.remove();
		}
	}

	private record PaintingTaskKey(ServerLevel level, BlockPos pos) {
	}

	private static final class PaintingTask {
		private final ServerLevel level;
		private final BlockPos tablePos;
		private final UUID playerId;
		private final int[][] colors;
		private int index;
		private boolean waiting;

		private PaintingTask(ServerLevel level, BlockPos tablePos, UUID playerId, int[][] colors) {
			this.level = level;
			this.tablePos = tablePos;
			this.playerId = playerId;
			this.colors = colors;
		}

		private boolean tick(net.minecraft.server.MinecraftServer server) {
			if (!level.getBlockState(tablePos).is(PAINTING_TABLE)) return false;
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) return true;
			int placed = 0;
			while (index < SIZE * SIZE && placed < 64) {
				if (index % PIXELS_PER_BLOCK == 0 && !consumePixelBlock(player, tablePos)) {
					if (!waiting) {
						player.sendSystemMessage(Component.literal("Painting Table paused: insert more pixel_block items into the table."));
						waiting = true;
					}
					PaintingTablePayloads.sendStatus(player, requiredPixelBlocks(),
						remainingPixelBlocks(index),
						"Paused: need more pixel_block items in the table.");
					return true;
				}
				waiting = false;
				int x = index % SIZE;
				int z = index / SIZE;
				int centerX = mapCenter(tablePos.getX());
				int centerZ = mapCenter(tablePos.getZ());
				BlockPos target = new BlockPos(
					centerX - 64 + x, tablePos.getY() - 1, centerZ - 64 + z);
				level.setBlock(target, PIXEL_BLOCK.defaultBlockState()
					.setValue(PixelBlock.COLOR, colors[x][z]), 3);
				index++;
				placed++;
			}
			if (index == SIZE * SIZE) {
				PaintingTableBlockEntity table = tableEntity(player, tablePos);
				if (table == null || !table.hasBlankCanvas() || !table.getPaintedCanvas().isEmpty()) {
					PaintingTablePayloads.sendStatus(player, requiredPixelBlocks(), 0,
						"Canvas built. Keep the Blank Canvas and an empty output slot available.");
					return true;
				}
				int[] snapshot = new int[SIZE * SIZE];
				for (int z = 0; z < SIZE; z++) {
					for (int x = 0; x < SIZE; x++) snapshot[z * SIZE + x] = PIXEL_COLORS[colors[x][z]];
				}
				table.setBlankCanvas(ItemStack.EMPTY);
				table.setPaintedCanvas(PaintedCanvasItem.create(level,
					mapCenter(tablePos.getX()), mapCenter(tablePos.getZ()), snapshot));
			}
			PaintingTablePayloads.sendStatus(player, requiredPixelBlocks(), remainingPixelBlocks(index),
				index == SIZE * SIZE ? "Canvas complete." : "Building canvas...");
			return index < SIZE * SIZE;
		}

		private static boolean consumePixelBlock(ServerPlayer player, BlockPos tablePos) {
			PaintingTableBlockEntity table = tableEntity(player, tablePos);
			return table != null && table.consumePixelBlock();
		}
	}

	private static int listImages(CommandSourceStack src) {
		File[] images = imageFiles();
		if (images.length == 0) {
			src.sendFailure(Component.literal("No PNG or JPG images found in the 'mapart' folder."));
			return 0;
		}
		String names = String.join(", ", java.util.Arrays.stream(images).map(File::getName).toList());
		src.sendSuccess(() -> Component.literal("Available map art images: " + names
			+ ". Type /mapart <image> and press Tab."), false);
		return images.length;
	}

	private static CompletableFuture<Suggestions> suggestImages(
		CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		for (File image : imageFiles()) {
			builder.suggest(image.getName());
		}
		return builder.buildFuture();
	}

	private static File[] imageFiles() {
		File dir = new File("mapart");
		File[] files = dir.listFiles(file -> file.isFile()
			&& (file.getName().toLowerCase().endsWith(".png")
			|| file.getName().toLowerCase().endsWith(".jpg")
			|| file.getName().toLowerCase().endsWith(".jpeg")));
		return files == null ? new File[0] : files;
	}

	private static int runAtPlayer(CommandSourceStack src, String name, boolean shaded) {
		try {
			ServerPlayer player = src.getPlayerOrException();
			BlockPos p = player.blockPosition();
			int x = Math.floorDiv(p.getX() + 64, SIZE) * SIZE;
			int z = Math.floorDiv(p.getZ() + 64, SIZE) * SIZE;
			return run(src, name, x, p.getY() - 1, z, shaded);
		} catch (Exception e) {
			src.sendFailure(Component.literal("Failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int run(
		CommandSourceStack src, String name, int centerX, int baseY, int centerZ, boolean shaded) {
		try {
			File dir = new File("mapart").getCanonicalFile();
			File f = new File(dir, name).getCanonicalFile();
			if (!f.toPath().startsWith(dir.toPath()) || !f.isFile()) {
				src.sendFailure(Component.literal("Put images in the 'mapart' folder of the game/server directory."));
				return 0;
			}
			BufferedImage raw = ImageIO.read(f);
			if (raw == null) {
				src.sendFailure(Component.literal("Not a supported image."));
				return 0;
			}
			BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(raw, 0, 0, SIZE, SIZE, null);
			g.dispose();
			correctPhotoLighting(img);

			// x/z are map centres: a scale-0 map covers [center - 64, center + 64).
			int x0 = centerX - 64;
			int z0 = centerZ - 64;

			ServerLevel level = src.getLevel();
			int[][] colours = new int[SIZE][SIZE];
			int[][] shades = new int[SIZE][SIZE];
			quantizeWithDithering(img, shaded, colours, shades, true);
			for (int x = 0; x < SIZE; x++) {
				int[] ys = new int[SIZE];
				int y = 0, min = 0;
				for (int z = 0; z < SIZE; z++) {
					if (z > 0) y += shades[x][z] == 2 ? 1 : shades[x][z] == 0 ? -1 : 0;
					ys[z] = y;
					min = Math.min(min, y);
				}
				for (int z = 0; z < SIZE; z++) {
					BlockPos pos = new BlockPos(x0 + x, baseY + ys[z] - min, z0 + z);
					level.setBlock(pos, MATERIALS[colours[x][z]].defaultBlockState(), 3);
					// Clear obstructions above so the map sees the wool.
					for (int up = 1; up <= 3; up++) {
						BlockPos above = pos.above(up);
						if (!level.getBlockState(above).isAir()) level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
			src.sendSuccess(() -> Component.literal("Built wool map art centered at X " + centerX + ", Y " + baseY
				+ ", Z " + centerZ + ". Craft a fresh map (scale 0) and use it inside that area."), true);
			return 1;
		} catch (Exception e) {
			src.sendFailure(Component.literal("Failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int runPixelAtPlayer(CommandSourceStack src, String name) {
		try {
			ServerPlayer player = src.getPlayerOrException();
			BlockPos p = player.blockPosition();
			int centerX = Math.floorDiv(p.getX() + 64, SIZE) * SIZE;
			int centerZ = Math.floorDiv(p.getZ() + 64, SIZE) * SIZE;
			return runPixel(src, name, centerX, p.getY() - 1, centerZ);
		} catch (Exception e) {
			src.sendFailure(Component.literal("Failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int runPixel(
		CommandSourceStack src, String name, int centerX, int baseY, int centerZ) {
		try {
			BufferedImage image = loadImage(name);
			int[][] colours = new int[SIZE][SIZE];
			quantizePixelPalette(image, colours);
			ServerLevel level = src.getLevel();
			int x0 = centerX - 64;
			int z0 = centerZ - 64;
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					BlockPos pos = new BlockPos(x0 + x, baseY, z0 + z);
					BlockState state = PIXEL_BLOCK.defaultBlockState()
						.setValue(PixelBlock.COLOR, colours[x][z]);
					level.setBlock(pos, state, 3);
					if (!level.getBlockState(pos.above()).isAir()) {
						level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
			src.sendSuccess(() -> Component.literal("Built 256-color pixel art centered at X "
				+ centerX + ", Y " + baseY + ", Z " + centerZ + "."), true);
			return 1;
		} catch (Exception e) {
			src.sendFailure(Component.literal("Failed: " + e.getMessage()));
			return 0;
		}
	}

	private static BufferedImage loadImage(String name) throws Exception {
		File dir = new File("mapart").getCanonicalFile();
		File file = new File(dir, name).getCanonicalFile();
		if (!file.toPath().startsWith(dir.toPath()) || !file.isFile()) {
			throw new IllegalArgumentException("Put images in the 'mapart' folder.");
		}
		BufferedImage raw = ImageIO.read(file);
		if (raw == null) throw new IllegalArgumentException("Not a supported image.");
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.drawImage(raw, 0, 0, SIZE, SIZE, null);
		graphics.dispose();
		correctPhotoLighting(image);
		return image;
	}

	private static void quantizePixelPalette(BufferedImage image, int[][] colours) {
		double[][][] errors = new double[SIZE][SIZE][3];
		double backgroundR = 0;
		double backgroundG = 0;
		double backgroundB = 0;
		int backgroundPixels = 0;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (x < 8 || x >= SIZE - 8 || z < 8 || z >= SIZE - 8) {
					int rgb = image.getRGB(x, z);
					backgroundR += (rgb >> 16) & 255;
					backgroundG += (rgb >> 8) & 255;
					backgroundB += rgb & 255;
					backgroundPixels++;
				}
			}
		}
		backgroundR /= backgroundPixels;
		backgroundG /= backgroundPixels;
		backgroundB /= backgroundPixels;

		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int source = image.getRGB(x, z);
				double sourceR = (source >> 16) & 255;
				double sourceG = (source >> 8) & 255;
				double sourceB = source & 255;
				double backgroundDistance = Math.sqrt(
					(sourceR - backgroundR) * (sourceR - backgroundR)
						+ (sourceG - backgroundG) * (sourceG - backgroundG)
						+ (sourceB - backgroundB) * (sourceB - backgroundB));
				boolean backgroundLike = backgroundDistance < 30.0;
				double ditherStrength = backgroundLike ? 0.0 : 0.10;
				double r = clamp(sourceR + (backgroundLike ? 0 : errors[x][z][0]), 0, 255);
				double g = clamp(sourceG + (backgroundLike ? 0 : errors[x][z][1]), 0, 255);
				double b = clamp(sourceB + (backgroundLike ? 0 : errors[x][z][2]), 0, 255);
				int best = 0;
				double bestDistance = Double.MAX_VALUE;
				for (int i = 0; i < PIXEL_COLORS.length; i++) {
					int color = PIXEL_COLORS[i];
					double dr = r - ((color >> 16) & 255);
					double dg = g - ((color >> 8) & 255);
					double db = b - (color & 255);
					double distance = dr * dr * 0.9 + dg * dg * 1.15 + db * db * 0.75;
					if (distance < bestDistance) {
						bestDistance = distance;
						best = i;
					}
				}
				colours[x][z] = best;
				int quantized = PIXEL_COLORS[best];
				double errorR = clamp(r - ((quantized >> 16) & 255), -32, 32);
				double errorG = clamp(g - ((quantized >> 8) & 255), -32, 32);
				double errorB = clamp(b - (quantized & 255), -32, 32);
				diffusePixelError(errors, x + 1, z, errorR, errorG, errorB, 7.0 / 16.0, ditherStrength);
				diffusePixelError(errors, x - 1, z + 1, errorR, errorG, errorB, 3.0 / 16.0, ditherStrength);
				diffusePixelError(errors, x, z + 1, errorR, errorG, errorB, 5.0 / 16.0, ditherStrength);
				diffusePixelError(errors, x + 1, z + 1, errorR, errorG, errorB, 1.0 / 16.0, ditherStrength);
			}
		}
	}

	private static void diffusePixelError(
		double[][][] errors, int x, int z, double r, double g, double b,
		double amount, double ditherStrength) {
		if (x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
			errors[x][z][0] += r * amount * ditherStrength;
			errors[x][z][1] += g * amount * ditherStrength;
			errors[x][z][2] += b * amount * ditherStrength;
		}
	}

	private static void quantizeWithDithering(
		BufferedImage image, boolean shaded, int[][] colours, int[][] shades, boolean photo) {
		double[][][] errors = new double[SIZE][SIZE][3];
		double ditherStrength = photo ? DITHER_STRENGTH : DITHER_STRENGTH * 0.35;
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int source = image.getRGB(x, z);
				double r = clamp(((source >> 16) & 255) + errors[x][z][0], 0, 255);
				double g = clamp(((source >> 8) & 255) + errors[x][z][1], 0, 255);
				double b = clamp((source & 255) + errors[x][z][2], 0, 255);
				int rgb = ((int) r << 16) | ((int) g << 8) | (int) b;

				int best = 0;
				int bestShade = 1;
				long bestDistance = Long.MAX_VALUE;
				for (int i = 0; i < BASE.length; i++) {
					for (int shade = shaded ? 0 : 1; shade < (shaded ? 3 : 2); shade++) {
						long distance = dist(rgb, BASE[i], SHADES[shade]);
						if (distance < bestDistance) {
							bestDistance = distance;
							best = i;
							bestShade = shade;
						}
					}
				}
				colours[x][z] = best;
				shades[x][z] = bestShade;

				int quantized = BASE[best];
				double qr = ((quantized >> 16) & 255) * SHADES[bestShade] / 255.0;
				double qg = ((quantized >> 8) & 255) * SHADES[bestShade] / 255.0;
				double qb = (quantized & 255) * SHADES[bestShade] / 255.0;
				double errorR = clamp(r - qr, -MAX_DITHER_ERROR, MAX_DITHER_ERROR);
				double errorG = clamp(g - qg, -MAX_DITHER_ERROR, MAX_DITHER_ERROR);
				double errorB = clamp(b - qb, -MAX_DITHER_ERROR, MAX_DITHER_ERROR);
				diffuse(errors, x + 1, z, errorR, errorG, errorB, 7.0 / 16.0, ditherStrength);
				diffuse(errors, x - 1, z + 1, errorR, errorG, errorB, 3.0 / 16.0, ditherStrength);
				diffuse(errors, x, z + 1, errorR, errorG, errorB, 5.0 / 16.0, ditherStrength);
				diffuse(errors, x + 1, z + 1, errorR, errorG, errorB, 1.0 / 16.0, ditherStrength);
			}
		}
	}

	private static void diffuse(
		double[][][] errors, int x, int z, double r, double g, double b,
		double amount, double ditherStrength) {
		if (x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
			errors[x][z][0] += r * amount * ditherStrength;
			errors[x][z][1] += g * amount * ditherStrength;
			errors[x][z][2] += b * amount * ditherStrength;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static long dist(int rgb, int base, int mul) {
		double r = ((rgb >> 16) & 255) / 255.0;
		double g = ((rgb >> 8) & 255) / 255.0;
		double b = (rgb & 255) / 255.0;
		double pr = ((base >> 16) & 255) * mul / (255.0 * 255.0);
		double pg = ((base >> 8) & 255) * mul / (255.0 * 255.0);
		double pb = (base & 255) * mul / (255.0 * 255.0);

		// Weight green and luminance enough to avoid turning warm fur into red/orange.
		double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		double paletteLuminance = 0.2126 * pr + 0.7152 * pg + 0.0722 * pb;
		double chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
		double paletteChroma = Math.max(pr, Math.max(pg, pb)) - Math.min(pr, Math.min(pg, pb));
		double dr = r - pr;
		double dg = g - pg;
		double db = b - pb;
		double dl = luminance - paletteLuminance;
		double dc = chroma - paletteChroma;
		boolean warmSkin = r > 0.25 && r > g * 1.01 && g > b * 1.005;
		double warmSkinPenalty = warmSkin
			? (paletteChroma < 0.06 ? 0.035 : pb > pg ? 0.055 : 0.0)
			: 0.0;
		return Math.round((0.9 * dr * dr + 1.15 * dg * dg + 0.75 * db * db
			+ 0.8 * dl * dl + 0.55 * dc * dc + warmSkinPenalty) * 1_000_000);
	}

	private static void correctPhotoLighting(BufferedImage image) {
		double luminanceSum = 0;
		double backgroundRSum = 0;
		double backgroundGSum = 0;
		double backgroundBSum = 0;
		int backgroundPixels = 0;
		int highlights = 0;
		int pixels = SIZE * SIZE;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				int rgb = image.getRGB(x, z);
				double r = ((rgb >> 16) & 255) / 255.0;
				double g = ((rgb >> 8) & 255) / 255.0;
				double b = (rgb & 255) / 255.0;
				double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
				luminanceSum += luminance;
				if (luminance >= 0.88) highlights++;
				if (x < 8 || x >= SIZE - 8 || z < 8 || z >= SIZE - 8) {
					backgroundRSum += r;
					backgroundGSum += g;
					backgroundBSum += b;
					backgroundPixels++;
				}
			}
		}

		double average = luminanceSum / pixels;
		double highlightRatio = (double) highlights / pixels;
		double backgroundR = backgroundRSum / backgroundPixels;
		double backgroundG = backgroundGSum / backgroundPixels;
		double backgroundB = backgroundBSum / backgroundPixels;
		double gamma = 1.0
			+ Math.min(0.36, Math.max(0.0, average - 0.42) * 0.78 + highlightRatio * 0.45);
		double highlightScale = 1.0
			- Math.min(0.10, Math.max(0.0, average - 0.55) * 0.14 + highlightRatio * 0.16);

		// Darken bright camera exposures while retaining the original hue.
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				int rgb = image.getRGB(x, z);
				double r = ((rgb >> 16) & 255) / 255.0;
				double g = ((rgb >> 8) & 255) / 255.0;
				double b = (rgb & 255) / 255.0;
				boolean warm = r > g && g > b && r - g > 0.035 && g - b > 0.015;
				double backgroundDistance = Math.sqrt(
					(r - backgroundR) * (r - backgroundR)
						+ (g - backgroundG) * (g - backgroundG)
						+ (b - backgroundB) * (b - backgroundB));
				double foreground = clamp((backgroundDistance - 0.08) / 0.32, 0, 1);
				double correctionFactor = (warm ? 0.58 : 1.0) * (1.0 - foreground * 0.55);
				double localGamma = 1.0 + (gamma - 1.0) * correctionFactor;
				double scaleFactor = 1.0 - foreground * 0.65;
				double localScale = warm
					? 1.0 - (1.0 - highlightScale) * scaleFactor * 0.35
					: 1.0 - (1.0 - highlightScale) * scaleFactor;
				int correctedR = correctChannel((int) Math.round(r * 255), localGamma, localScale);
				int correctedG = correctChannel((int) Math.round(g * 255), localGamma, localScale);
				int correctedB = correctChannel((int) Math.round(b * 255), localGamma, localScale);
				image.setRGB(x, z, (correctedR << 16) | (correctedG << 8) | correctedB);
			}
		}
	}

	private static int correctChannel(int channel, double gamma, double highlightScale) {
		return (int) Math.round(255.0 * Math.pow(channel / 255.0, gamma) * highlightScale);
	}
}
