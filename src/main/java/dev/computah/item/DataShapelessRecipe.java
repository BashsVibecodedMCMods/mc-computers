package dev.computah.item;

import com.mojang.serialization.MapCodec;
import dev.computah.component.EepromComponent;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless recipe that changes the data of the part it is made from instead of making a new item: an EEPROM plus a
 * book gets the Lua BIOS, a blank floppy plus a book becomes the Computah OS floppy, and a floppy plus a dye takes the
 * dye's colour (keeping its files). The JSON format is the vanilla shapeless one; the part is the ingredient whose
 * item equals the result's item.
 */
public class DataShapelessRecipe extends ShapelessRecipe {
    public enum Kind {
        BIOS, OS_FLOPPY, FLOPPY_DYE
    }

    private final Kind kind;

    public DataShapelessRecipe(Kind kind, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private ItemStack part(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.getResultItem(registries);
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (!s.isEmpty() && s.is(result.getItem())) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Applies this recipe's change to a copy of the part, or returns EMPTY when the part can't take it. */
    public ItemStack transform(ItemStack part, HolderLookup.Provider registries) {
        if (part.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = part.copyWithCount(1);
        switch (kind) {
            case BIOS -> {
                ModComponents.EepromData d = part.get(ModComponents.EEPROM.get());
                EepromComponent.Contents c = d == null ? EepromComponent.Contents.EMPTY : d.contents();
                if (c.readOnly()) return ItemStack.EMPTY;
                PartItem.withBios(out);
            }
            case OS_FLOPPY -> {
                // Only a floppy that was never used (no files yet) can become the OS floppy.
                if (part.has(ModComponents.ADDRESS.get()) || PartItem.isOsFloppy(part)) return ItemStack.EMPTY;
                out.set(ModComponents.OS_DISK.get(), Unit.INSTANCE);
            }
            case FLOPPY_DYE -> {
                DyeColor color = super.getResultItem(registries).get(ModComponents.FLOPPY_COLOR.get());
                if (color == null || color == part.get(ModComponents.FLOPPY_COLOR.get())) return ItemStack.EMPTY;
                out.set(ModComponents.FLOPPY_COLOR.get(), color);
            }
        }
        return out;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && !transform(part(input, level.registryAccess()), level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return transform(part(input, registries), registries);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        // What the recipe book and JEI show.
        return switch (kind) {
            case BIOS -> PartItem.biosEeprom();
            case OS_FLOPPY -> PartItem.osFloppy();
            case FLOPPY_DYE -> super.getResultItem(registries);
        };
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return switch (kind) {
            case BIOS -> ModRecipes.BIOS_EEPROM.get();
            case OS_FLOPPY -> ModRecipes.OS_FLOPPY.get();
            case FLOPPY_DYE -> ModRecipes.FLOPPY_DYE.get();
        };
    }

    /** Reads and writes the vanilla shapeless format. */
    public static final class Serializer implements RecipeSerializer<DataShapelessRecipe> {
        private final MapCodec<DataShapelessRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, DataShapelessRecipe> stream;

        public Serializer(Kind kind) {
            this.codec = RecipeSerializer.SHAPELESS_RECIPE.codec().xmap(r -> wrap(kind, r), r -> r);
            this.stream = RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(r -> wrap(kind, r), r -> r);
        }

        private static DataShapelessRecipe wrap(Kind kind, ShapelessRecipe r) {
            // The raw result (not getResultItem, which subclasses override for display).
            ItemStack result = r instanceof DataShapelessRecipe d ? d.rawResult() : r.getResultItem(null);
            return new DataShapelessRecipe(kind, r.getGroup(), r.category(), result, r.getIngredients());
        }

        @Override
        public MapCodec<DataShapelessRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DataShapelessRecipe> streamCodec() {
            return stream;
        }
    }

    ItemStack rawResult() {
        return super.getResultItem(null);
    }
}
