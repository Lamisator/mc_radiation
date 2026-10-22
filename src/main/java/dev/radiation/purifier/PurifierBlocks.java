package dev.radiation.purifier;

import dev.radiation.RadiationMod;
import dev.radiation.thirst.CanteenItem;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

/** Drinking water: the canteen and the water purifier. */
public final class PurifierBlocks {
	private PurifierBlocks() {
	}

	public static final Block WATER_PURIFIER = register(ResourceKey.create(Registries.BLOCK, RadiationMod.id("water_purifier")));

	private static Block register(ResourceKey<Block> key) {
		return Registry.register(BuiltInRegistries.BLOCK, key, new WaterPurifierBlock(BlockBehaviour.Properties.of().setId(key)
				.mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
	}

	public static final Item WATER_PURIFIER_ITEM = item("water_purifier", key -> new BlockItem(WATER_PURIFIER, new Item.Properties().setId(key)
			.useBlockDescriptionPrefix().component(DataComponents.LORE, lore("block.radiation.water_purifier.desc"))));

	public static final Item CANTEEN = item("canteen", key -> new CanteenItem(new Item.Properties().setId(key).stacksTo(1)
			.component(CanteenItem.WATER, 0).component(DataComponents.LORE, lore("item.radiation.canteen.desc"))));

	/** An empty plastic bottle; filled at water it gives Dirty Water, in the purifier Purified Water. */
	public static final Item PLASTIC_BOTTLE = item("plastic_bottle", key -> new dev.radiation.thirst.PlasticBottleItem(new Item.Properties().setId(key)
			.stacksTo(16).component(DataComponents.LORE, lore("item.radiation.plastic_bottle.desc"))));
	public static final Item DIRTY_WATER = item("dirty_water", key -> new Item(water(key).component(DataComponents.LORE, lore("item.radiation.dirty_water.desc"))));
	public static final Item PURIFIED_WATER = item("purified_water", key -> new Item(water(key).component(DataComponents.LORE, lore("item.radiation.purified_water.desc"))));

	/** A plastic bottle of water: drunk like a potion (one serving), the empty bottle stays. */
	private static Item.Properties water(ResourceKey<Item> key) {
		return new Item.Properties().setId(key).stacksTo(16).usingConvertsTo(PLASTIC_BOTTLE)
				.component(DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable.builder()
						.consumeSeconds(1.6f)
						.animation(net.minecraft.world.item.ItemUseAnimation.DRINK)
						.sound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK)
						.hasConsumeParticles(false)
						.build())
				.component(dev.radiation.thirst.WaterSources.HYDRATION, new dev.radiation.thirst.WaterSources.Hydration(1));
	}

	public static final BlockEntityType<WaterPurifierBlockEntity> PURIFIER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			RadiationMod.id("water_purifier"), FabricBlockEntityTypeBuilder.create(WaterPurifierBlockEntity::new, WATER_PURIFIER).build());

	public static final MenuType<WaterPurifierMenu> PURIFIER_MENU = Registry.register(BuiltInRegistries.MENU, RadiationMod.id("water_purifier"),
			new MenuType<>(WaterPurifierMenu::new, FeatureFlags.VANILLA_SET));

	public static void init() {
		PurifierPower.init();
	}

	private static Item item(String name, java.util.function.Function<ResourceKey<Item>, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RadiationMod.id(name));
		Item item = factory.apply(key);
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static ItemLore lore(String key) {
		return new ItemLore(List.of(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
	}
}
