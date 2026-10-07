package dev.radiation.mixin;

import dev.radiation.food.FoodContamination;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** What is made of contaminated food is contaminated: crafting (bread, cake, stew...) and cooking (furnace, smoker, campfire). */
public final class RecipeContaminationMixin {
	private RecipeContaminationMixin() {
	}

	@Mixin(ShapedRecipe.class)
	public abstract static class Shaped {
		@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
		private void radiation$contaminate(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
			FoodContamination.processed(cir.getReturnValue(), input.items());
		}
	}

	@Mixin(ShapelessRecipe.class)
	public abstract static class Shapeless {
		@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
		private void radiation$contaminate(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
			FoodContamination.processed(cir.getReturnValue(), input.items());
		}
	}

	@Mixin(SingleItemRecipe.class)
	public abstract static class Single {
		@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
		private void radiation$contaminate(SingleRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
			FoodContamination.processed(cir.getReturnValue(), List.of(input.item().copyWithCount(1)));
		}
	}
}
