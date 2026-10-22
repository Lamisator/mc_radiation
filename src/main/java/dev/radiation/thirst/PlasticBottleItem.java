package dev.radiation.thirst;

import dev.radiation.food.FoodContamination;
import dev.radiation.purifier.PurifierBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * An empty plastic bottle. Right-click water to fill it: that gives Dirty Water, whatever the water - only the water
 * purifier makes Purified Water. Water taken where there is radiation carries it.
 */
public class PlasticBottleItem extends Item {
	public PlasticBottleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockPos water = WaterSources.lookingAtWater(player, ClipContext.Fluid.SOURCE_ONLY);
		if (water == null || !level.mayInteract(player, water)) {
			return InteractionResult.PASS;
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.gameEvent(player, GameEvent.FLUID_PICKUP, water);
		ItemStack filled = new ItemStack(PurifierBlocks.DIRTY_WATER);
		if (level instanceof ServerLevel server) {
			FoodContamination.set(filled, Thirst.waterRads(server, water));
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(stack, player, filled));
	}
}
