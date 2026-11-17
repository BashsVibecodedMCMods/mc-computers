package dev.computah.item;

import dev.computah.api.Context;
import dev.computah.api.DelayException;
import dev.computah.client.BuzzerWave;
import dev.computah.display.ScreenGeometry;
import dev.computah.fs.DriveTiming;
import dev.computah.machine.Machine;
import dev.computah.machine.TestMachines;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class PartsTest {
    @Test
    public void fitResolutionMatchesMonitorShape() {
        assertArrayEquals(new int[]{100, 50}, ScreenGeometry.fitResolution(1, 1, 160, 50));
        assertArrayEquals(new int[]{160, 16}, ScreenGeometry.fitResolution(4, 1, 160, 50));
        assertArrayEquals(new int[]{145, 7}, ScreenGeometry.fitResolution(8, 1, 160, 50));
        assertArrayEquals(new int[]{13, 50}, ScreenGeometry.fitResolution(1, 6, 160, 50));
        int[] big = ScreenGeometry.fitResolution(8, 6, 160, 50);
        assertTrue(big[0] <= 160 && big[1] <= 50 && big[1] == 50, java.util.Arrays.toString(big));
        int[] t1 = ScreenGeometry.fitResolution(8, 1, 50, 16);
        assertTrue(t1[0] <= 50 && t1[1] >= 1, java.util.Arrays.toString(t1));
        // The fitted image fills the face: its aspect is within one cell of the display area's.
        int[] f = ScreenGeometry.fitResolution(5, 2, 160, 50);
        double area = (5 - 2 * ScreenGeometry.MARGIN) / (2 - 2 * ScreenGeometry.MARGIN);
        assertEquals(area, f[0] * 8.0 / (f[1] * 16.0), 8.0 / 16 / f[1] + 0.05);
    }

    @Test
    public void sizesHaveDefaults() {
        assertEquals(512 * 1024, PartItem.ramSize(1));
        assertEquals(4096 * 1024, PartItem.ramSize(4));
        assertEquals(1024 * 1024, PartItem.hddSize(1));
        assertEquals(4096 * 1024, PartItem.hddSize(3));
        assertEquals(512 * 1024, PartItem.floppySize());
        assertEquals(64 * 1024, PartItem.tmpSize());
    }

    @Test
    public void driveTimingAddsUpAndDelays() {
        DriveTiming hdd = DriveTiming.hdd(1);
        assertEquals(0.005, hdd.seekSeconds(), 1e-9);
        assertEquals(1.0, hdd.readSeconds(64 * 1024), 1e-9);
        assertEquals(0.25, DriveTiming.hdd(3).readSeconds(64 * 1024), 1e-9);
        assertEquals(1.0, DriveTiming.floppy().writeSeconds(12 * 1024), 1e-9);

        Machine m = new Machine(new TestMachines.Host());
        Context direct = new Context(m, false);
        // Seeks under a tick add up without delaying.
        hdd.seek(direct);
        hdd.seek(direct);
        DelayException e = assertThrows(DelayException.class, () -> hdd.read(direct, 32 * 1024));
        assertEquals(10, e.ticks()); // 0.015 + 0.5 s owed -> 10 whole ticks, the rest carried over
        // Calls that already waited are never charged.
        assertDoesNotThrow(() -> hdd.read(new Context(m, true), 1 << 20));
        // A floppy spins up first.
        DelayException spin = assertThrows(DelayException.class, () -> DriveTiming.floppy().seek(direct));
        assertTrue(spin.ticks() >= 15, "spin-up " + spin.ticks());
    }

    @Test
    public void buzzerIsASquareWaveAtTheExactFrequency() {
        BuzzerWave w = new BuzzerWave(1000, 0.1);
        assertEquals(2205, w.totalSamples());
        // Period 22.05 samples: high for the first half, low for the second (after the fade-in).
        assertEquals(BuzzerWave.AMPLITUDE, w.sample(221));
        assertTrue(w.sample(100) > 0 == (100 * 1000.0 / BuzzerWave.SAMPLE_RATE % 1.0 < 0.5));
        assertEquals(0, w.sample(0));
        ByteBuffer buf = ByteBuffer.allocate(w.totalSamples() * 2);
        assertEquals(2205, w.next(buf, 100000));
        assertEquals(0, w.remaining());
        for (int i = 0; i < w.totalSamples(); i++) assertTrue(Math.abs(w.sample(i)) <= BuzzerWave.AMPLITUDE);
        assertEquals(BuzzerWave.SAMPLE_RATE, new BuzzerWave(5, 1).totalSamples()); // frequency clamps to 20 Hz
    }

    private static DataShapelessRecipe recipe(DataShapelessRecipe.Kind kind, ItemStack result, Ingredient... in) {
        return new DataShapelessRecipe(kind, "", CraftingBookCategory.REDSTONE, result, NonNullList.of(Ingredient.EMPTY, in));
    }

    @Test
    public void dataRecipesChangeThePart() {
        ItemStack blank = new ItemStack(ModItems.EEPROM.get());
        assertNull(blank.get(ModComponents.EEPROM.get()), "EEPROMs are crafted blank");
        DataShapelessRecipe bios = recipe(DataShapelessRecipe.Kind.BIOS, new ItemStack(ModItems.EEPROM.get()),
                Ingredient.of(ModItems.EEPROM.get()), Ingredient.of(Items.BOOK));
        ItemStack flashed = bios.transform(blank, null);
        assertEquals(PartItem.biosCode(), new String(flashed.get(ModComponents.EEPROM.get()).contents().code(), StandardCharsets.UTF_8));
        assertEquals(PartItem.biosCode(), new String(PartItem.biosEeprom().get(ModComponents.EEPROM.get()).contents().code(), StandardCharsets.UTF_8));

        ItemStack floppy = new ItemStack(ModItems.FLOPPY.get());
        DataShapelessRecipe os = recipe(DataShapelessRecipe.Kind.OS_FLOPPY, new ItemStack(ModItems.FLOPPY.get()),
                Ingredient.of(ModItems.FLOPPY.get()), Ingredient.of(Items.BOOK));
        assertTrue(PartItem.isOsFloppy(os.transform(floppy, null)));
        assertTrue(PartItem.isOsFloppy(PartItem.osFloppy()));
        ItemStack used = floppy.copy();
        used.set(ModComponents.ADDRESS.get(), "abc");
        assertTrue(os.transform(used, null).isEmpty(), "a floppy with files can't become the OS disk");

        ItemStack red = new ItemStack(ModItems.FLOPPY.get());
        red.set(ModComponents.FLOPPY_COLOR.get(), DyeColor.RED);
        DataShapelessRecipe dye = recipe(DataShapelessRecipe.Kind.FLOPPY_DYE, red,
                Ingredient.of(ModItems.FLOPPY.get()), Ingredient.of(Items.RED_DYE));
        ItemStack dyed = dye.transform(used, null);
        assertEquals(DyeColor.RED, dyed.get(ModComponents.FLOPPY_COLOR.get()));
        assertEquals("abc", dyed.get(ModComponents.ADDRESS.get()), "dyeing keeps the files");
        assertEquals(DyeColor.BLUE, PartItem.dyedFloppy(DyeColor.BLUE).get(ModComponents.FLOPPY_COLOR.get()));
    }
}
