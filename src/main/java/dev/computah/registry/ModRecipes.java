package dev.computah.registry;

import dev.computah.Computah;
import dev.computah.item.DataShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe serializers: recipes that change a part's data (see {@link DataShapelessRecipe}). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Computah.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, DataShapelessRecipe.Serializer> BIOS_EEPROM =
            REGISTER.register("bios_eeprom", () -> new DataShapelessRecipe.Serializer(DataShapelessRecipe.Kind.BIOS));
    public static final DeferredHolder<RecipeSerializer<?>, DataShapelessRecipe.Serializer> OS_FLOPPY =
            REGISTER.register("os_floppy", () -> new DataShapelessRecipe.Serializer(DataShapelessRecipe.Kind.OS_FLOPPY));
    public static final DeferredHolder<RecipeSerializer<?>, DataShapelessRecipe.Serializer> FLOPPY_DYE =
            REGISTER.register("floppy_dye", () -> new DataShapelessRecipe.Serializer(DataShapelessRecipe.Kind.FLOPPY_DYE));

    private ModRecipes() {
    }
}
