package dev.radiation.registry;

import com.mojang.serialization.Codec;
import dev.radiation.RadiationMod;
import dev.radiation.block.NuclearWasteBarrelBlock;
import dev.radiation.config.RadiationConfig;
import dev.radiation.effect.RadAwayEffect;
import dev.radiation.effect.SimpleEffect;
import dev.radiation.item.GeigerCounterItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ModRegistry {
	private ModRegistry() {
	}

	// --- player data: accumulated rads, saved with the player, reset on death ---
	public static final AttachmentType<Float> RADS = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.initializer(() -> 0f)
			.buildAndRegister(RadiationMod.id("rads"));

	// --- sounds ---
	public static final SoundEvent GEIGER_CLICK = sound("geiger_click");
	public static final SoundEvent RAD_WARNING = sound("rad_warning");

	// --- damage ---
	public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RadiationMod.id("radiation"));

	// --- effects ---
	public static final Holder<MobEffect> RADIATION_SICKNESS = effect("radiation_sickness", new SimpleEffect(MobEffectCategory.HARMFUL, 0x7FBF2F));
	public static final Holder<MobEffect> RAD_RESISTANCE = effect("rad_resistance", new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x3C78D2));
	public static final Holder<MobEffect> RADAWAY = effect("radaway", new RadAwayEffect(MobEffectCategory.BENEFICIAL, 0xD67828));

	// --- armor ---
	public static final ResourceKey<EquipmentAsset> HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, RadiationMod.id("hazmat"));
	public static final ArmorMaterial HAZMAT_MATERIAL = new ArmorMaterial(
			12, defense(1, 2, 3, 1, 3), 10, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f, ItemTags.WOOL, HAZMAT_ASSET);

	// --- blocks ---
	public static final Block NUCLEAR_WASTE_BARREL = block("nuclear_waste_barrel", NuclearWasteBarrelBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_YELLOW)
					.strength(2.5f, 6f)
					.sound(SoundType.METAL)
					.lightLevel(state -> 5)
					.requiresCorrectToolForDrops());

	// --- items ---
	public static final Item RADAWAY_ITEM = item("radaway", Item::new, new Item.Properties()
			.stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumable.builder()
					.consumeSeconds(1.6f)
					.animation(ItemUseAnimation.DRINK)
					.sound(SoundEvents.GENERIC_DRINK)
					.hasConsumeParticles(false)
					.onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(RADAWAY, RadiationConfig.get().radAwayDurationSeconds * 20, 0)))
					.build())
			.component(DataComponents.LORE, lore("item.radiation.radaway.desc")));

	public static final Item RAD_X = item("rad_x", Item::new, new Item.Properties()
			.stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumable.builder()
					.consumeSeconds(0.8f)
					.animation(ItemUseAnimation.EAT)
					.sound(SoundEvents.GENERIC_EAT)
					.onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(RAD_RESISTANCE, RadiationConfig.get().radXDurationSeconds * 20, 0)))
					.build())
			.component(DataComponents.LORE, lore("item.radiation.rad_x.desc")));

	public static final Item GEIGER_COUNTER = item("geiger_counter", GeigerCounterItem::new, new Item.Properties()
			.stacksTo(1)
			.component(DataComponents.LORE, lore("item.radiation.geiger_counter.desc")));

	public static final Item HAZMAT_HELMET = armor("hazmat_helmet", ArmorType.HELMET);
	public static final Item HAZMAT_CHESTPLATE = armor("hazmat_chestplate", ArmorType.CHESTPLATE);
	public static final Item HAZMAT_LEGGINGS = armor("hazmat_leggings", ArmorType.LEGGINGS);
	public static final Item HAZMAT_BOOTS = armor("hazmat_boots", ArmorType.BOOTS);

	public static final Item NUCLEAR_WASTE_BARREL_ITEM = item("nuclear_waste_barrel",
			props -> new BlockItem(NUCLEAR_WASTE_BARREL, props),
			new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON)
					.component(DataComponents.LORE, lore("block.radiation.nuclear_waste_barrel.desc")));

	// --- creative tab ---
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, RadiationMod.id("radiation"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.radiation"))
					.icon(() -> new ItemStack(GEIGER_COUNTER))
					.displayItems((params, output) -> {
						output.accept(GEIGER_COUNTER);
						output.accept(RADAWAY_ITEM);
						output.accept(RAD_X);
						output.accept(HAZMAT_HELMET);
						output.accept(HAZMAT_CHESTPLATE);
						output.accept(HAZMAT_LEGGINGS);
						output.accept(HAZMAT_BOOTS);
						output.accept(NUCLEAR_WASTE_BARREL_ITEM);
						dev.radiation.vault.VaultBlocks.addToTab(output);
					})
					.build());

	public static void init() {
		// Static initialisers do the work; this method just forces class loading.
	}

	private static SoundEvent sound(String name) {
		var id = RadiationMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Holder<MobEffect> effect(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, RadiationMod.id(name), effect);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, RadiationMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RadiationMod.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static Item armor(String name, ArmorType type) {
		return item(name, Item::new, new Item.Properties().humanoidArmor(HAZMAT_MATERIAL, type)
				.component(DataComponents.LORE, lore("item.radiation.hazmat.desc")));
	}

	private static ItemLore lore(String key) {
		return new ItemLore(List.of(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
	}

	private static Map<ArmorType, Integer> defense(int boots, int leggings, int chestplate, int helmet, int body) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.LEGGINGS, leggings);
		map.put(ArmorType.CHESTPLATE, chestplate);
		map.put(ArmorType.HELMET, helmet);
		map.put(ArmorType.BODY, body);
		return map;
	}
}
