package dev.radiation.vault;

import dev.radiation.RadiationMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Fallout-style vault blocks: the cog door and its console, sliding doors, wall and floor panels, lamps. */
public final class VaultBlocks {
	private VaultBlocks() {
	}

	/** Every vault item, in creative-tab order. */
	public static final List<Item> TAB_ITEMS = new ArrayList<>();

	// --- sounds ---
	public static final SoundEvent VAULT_DOOR_OPEN = sound("vault_door_open");
	public static final SoundEvent VAULT_DOOR_CLOSE = sound("vault_door_close");
	public static final SoundEvent SLIDING_DOOR_OPEN = sound("sliding_door_open");
	public static final SoundEvent SLIDING_DOOR_CLOSE = sound("sliding_door_close");
	public static final SoundEvent VAULT_CONSOLE_BEEP = sound("vault_console_beep");
	public static final SoundEvent VAULT_ALARM = sound("vault_alarm");

	private static BlockBehaviour.Properties steel() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
	}

	// --- door ---
	public static final Block VAULT_DOOR = block("vault_door", VaultDoorBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(50.0F, 3600000.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE).requiresCorrectToolForDrops().dynamicShape());
	public static final Block VAULT_DOOR_PART = block("vault_door_part", VaultDoorPartBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(50.0F, 3600000.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	public static final Block VAULT_CONSOLE = block("vault_console", VaultConsoleBlock::new, steel().noOcclusion());
	public static final Block SLIDING_DOOR = block("vault_sliding_door", SlidingDoorBlock::new, steel().noOcclusion()
			.pushReaction(PushReaction.POPPED).dynamicShape());

	// --- building blocks ---
	public static final Block VAULT_WALL = block("vault_wall", Block::new, steel());
	public static final Block VAULT_WALL_STRIPE = block("vault_wall_stripe", Block::new, steel());
	public static final Block VAULT_WALL_PIPES = block("vault_wall_pipes", Block::new, steel());
	public static final Block VAULT_FLOOR = block("vault_floor", Block::new, steel());
	public static final Block VAULT_GRATE = block("vault_grate", Block::new, steel().noOcclusion());
	public static final Block VAULT_HAZARD = block("vault_hazard_stripes", Block::new, steel().mapColor(MapColor.COLOR_YELLOW));
	public static final Block VAULT_DOOR_FRAME = block("vault_door_frame", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(25.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());

	// --- lamps ---
	public static final Block VAULT_LIGHT_PANEL = block("vault_light_panel", VaultLampBlock::panel, lamp(15));
	public static final Block NEON_BLUE = block("vault_neon_blue", VaultLampBlock::tube, lamp(13));
	public static final Block NEON_YELLOW = block("vault_neon_yellow", VaultLampBlock::tube, lamp(13));
	public static final Block NEON_WHITE = block("vault_neon_white", VaultLampBlock::tube, lamp(14));
	public static final Block ALARM_LIGHT = block("vault_alarm_light", VaultAlarmLightBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.METAL).noOcclusion()
			.lightLevel(state -> state.getValue(VaultAlarmLightBlock.LIT) ? 12 : 0));

	private static BlockBehaviour.Properties lamp(int light) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.6F).sound(SoundType.GLASS).noOcclusion().noCollision()
				.lightLevel(state -> light);
	}

	// --- items ---
	public static final Item VAULT_DOOR_ITEM = blockItem(VAULT_DOOR, Rarity.EPIC, "block.radiation.vault_door.desc");
	public static final Item VAULT_CONSOLE_ITEM = blockItem(VAULT_CONSOLE, Rarity.UNCOMMON, "block.radiation.vault_console.desc");
	public static final Item SLIDING_DOOR_ITEM = blockItem(SLIDING_DOOR, Rarity.COMMON, "block.radiation.vault_sliding_door.desc");
	public static final Item ALARM_LIGHT_ITEM = blockItem(ALARM_LIGHT, Rarity.COMMON, "block.radiation.vault_alarm_light.desc");
	static {
		for (Block b : List.of(VAULT_WALL, VAULT_WALL_STRIPE, VAULT_WALL_PIPES, VAULT_FLOOR, VAULT_GRATE, VAULT_HAZARD, VAULT_DOOR_FRAME,
				VAULT_LIGHT_PANEL, NEON_BLUE, NEON_YELLOW, NEON_WHITE)) {
			blockItem(b, Rarity.COMMON, null);
		}
	}

	// --- block entities ---
	public static final BlockEntityType<VaultDoorBlockEntity> VAULT_DOOR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			RadiationMod.id("vault_door"), FabricBlockEntityTypeBuilder.create(VaultDoorBlockEntity::new, VAULT_DOOR).build());
	public static final BlockEntityType<SlidingDoorBlockEntity> SLIDING_DOOR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			RadiationMod.id("vault_sliding_door"), FabricBlockEntityTypeBuilder.create(SlidingDoorBlockEntity::new, SLIDING_DOOR).build());

	public static final BlockEntityType<VaultAlarmLightBlockEntity> ALARM_LIGHT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			RadiationMod.id("vault_alarm_light"), FabricBlockEntityTypeBuilder.create(VaultAlarmLightBlockEntity::new, ALARM_LIGHT).build());

	public static void init() {
	}

	public static void addToTab(CreativeModeTab.Output output) {
		TAB_ITEMS.forEach(output::accept);
	}

	private static SoundEvent sound(String name) {
		var id = RadiationMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, RadiationMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(Block block, Rarity rarity, String loreKey) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(rarity);
		if (loreKey != null) {
			props.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY))));
		}
		BlockItem item = new BlockItem(block, props);
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		TAB_ITEMS.add(item);
		return item;
	}
}
