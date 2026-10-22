package dev.radiation.thirst;

import dev.radiation.food.FoodContamination;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Water in a plastic bottle. Its name says what it is: irradiated water is always "Dirty Water"; clean water is "Water"
 * straight from a lake or river and "Purified Water" from the water purifier. (Purified water that takes up radiation in a
 * store turns into Dirty Water.) The item model switches the texture the same way, on the radiation:contamination component.
 */
public class BottledWaterItem extends Item {
	private final boolean purified;

	public BottledWaterItem(Properties properties, boolean purified) {
		super(properties);
		this.purified = purified;
	}

	@Override
	public Component getName(ItemStack stack) {
		if (FoodContamination.of(stack) > 0) {
			return Component.translatable("item.radiation.dirty_water");
		}
		return Component.translatable(this.purified ? "item.radiation.purified_water" : "item.radiation.water");
	}
}
