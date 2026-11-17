package dev.computah.test;

import dev.computah.Computah;
import dev.computah.Config;
import dev.computah.api.Component;
import dev.computah.block.*;
import dev.computah.component.FilesystemComponent;
import dev.computah.display.DisplayBuffer;
import dev.computah.fs.FileSystem;
import dev.computah.host.HostCore;
import dev.computah.item.PartKind;
import dev.computah.item.ServerItem;
import dev.computah.link.LinkSavedData;
import dev.computah.machine.Machine;
import dev.computah.registry.ModBlocks;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * In-world tests with real computers. Each test that relies on config sets every value it uses and has its own
 * batch, because tests in one batch run at the same time. Lua results are written to a file on the computer's
 * hard drive and read back from Java.
 */
@GameTestHolder(Computah.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComputahGameTests {
    private static final int TIMEOUT = 1200;

    /** Lua that writes `value` to /result.txt on the first non-tmpfs drive. */
    private static final String WRITE_RESULT = """
            local function result(value)
              for a in component.list("filesystem") do
                if a ~= computer.tmpAddress() and not component.invoke(a, "isReadOnly") then
                  local h = component.invoke(a, "open", "/result.txt", "w")
                  component.invoke(a, "write", h, tostring(value))
                  component.invoke(a, "close", h)
                  return
                end
              end
            end
            """;

    private static void configure() {
        Config.POWER_ENABLED.set(false);
        Config.TIMEOUT.set(5.0);
        Config.LINK_RANGE.set(64);
        Config.WIRELESS_RANGE.set(400);
        Config.MAX_LINKS.set(32);
        // Instant drives keep tests fast; driveSpeedIsSimulated turns simulation on for itself.
        Config.DRIVE_SIMULATE.set(false);
        Config.DRIVE_SPEED.set(1.0);
        Config.RAM_KIB.set(List.of(512, 1024, 2048, 4096));
        Config.HDD_KIB.set(List.of(1024, 2048, 4096));
        Config.FLOPPY_KIB.set(512);
        Config.TMPFS_KIB.set(64);
    }

    private static ItemStack eeprom(String code) {
        ItemStack s = new ItemStack(ModItems.EEPROM.get());
        s.set(ModComponents.EEPROM.get(), ModComponents.EepromData.ofCode(WRITE_RESULT + code, "test"));
        return s;
    }

    /** Fills a part inventory: parts go into the first free slot of their type. */
    private static void install(HostCore core, Item... items) {
        for (Item item : items) installStack(core, new ItemStack(item));
    }

    private static void installStack(HostCore core, ItemStack stack) {
        var inv = core.inventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty() && inv.canPlaceItem(i, stack)) {
                inv.setItem(i, stack);
                return;
            }
        }
        throw new GameTestAssertException("no slot for " + stack);
    }

    private static CaseBlockEntity computer(GameTestHelper helper, BlockPos pos, String code, Item... cards) {
        helper.setBlock(pos, ModBlocks.CASE3.get().defaultBlockState());
        CaseBlockEntity be = (CaseBlockEntity) helper.getBlockEntity(pos);
        install(be.core(), ModItems.CPU3.get(), ModItems.RAM4.get(), ModItems.HDD1.get());
        install(be.core(), cards);
        installStack(be.core(), eeprom(code));
        return be;
    }

    private static String readResult(Machine m) {
        for (Component c : m.components()) {
            if (c instanceof FilesystemComponent fs && !c.address().equals(m.tmpAddress()) && fs.fileSystem().exists("result.txt")) {
                try (FileSystem.Handle h = fs.fileSystem().open("result.txt", FileSystem.Mode.READ)) {
                    byte[] data = h.read(4096);
                    return data == null ? "" : new String(data, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The result as a number, or -1 while it is being written. */
    private static long number(String r) {
        try {
            return r == null ? -1 : Long.parseLong(r.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void waitForResult(GameTestHelper helper, Supplier<Machine> machine, String expected, Runnable then) {
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> machine.get().start())
                .thenWaitUntil(() -> {
                    Machine m = machine.get();
                    String r = readResult(m);
                    if (m.state() == Machine.State.CRASHED) throw new GameTestAssertException("crashed: " + m.crashMessage());
                    if (!expected.equals(r)) throw new GameTestAssertException("result is " + r + ", expected " + expected);
                })
                .thenExecute(then)
                .thenSucceed();
    }

    private static String screenText(DisplayBuffer b) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < b.rows(); y++) {
            for (int x = 0; x < b.cols(); x++) sb.appendCodePoint(b.charAt(x, y));
            sb.append('\n');
        }
        return sb.toString();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_boot")
    public static void computerBootsAndRunsEeprom(GameTestHelper helper) {
        configure();
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), "result('booted ' .. computer.totalMemory()) while true do computer.pullSignal() end");
        waitForResult(helper, () -> be.core().machine(), "booted 4194304", () -> {
            helper.assertBlockProperty(new BlockPos(1, 1, 1), CaseBlock.RUNNING, true);
            be.core().stop();
        });
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_screen")
    public static void screenAndKeyboardOverCable(GameTestHelper helper) {
        configure();
        BlockPos screenPos = new BlockPos(4, 1, 1);
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.CABLE.get());
        helper.setBlock(screenPos, ModBlocks.SCREEN3.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(screenPos.above(), ModBlocks.KEYBOARD.get().defaultBlockState().setValue(KeyboardBlock.FACING, Direction.UP));
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), """
                local gpu = component.list("gpu")()
                local screen = component.list("screen")()
                component.invoke(gpu, "bind", screen)
                component.invoke(gpu, "set", 1, 1, "hello screen")
                local kb = component.invoke(screen, "getKeyboards")[1]
                result("ready " .. tostring(kb ~= nil))
                while true do
                  local name, address, char = computer.pullSignal()
                  if name == "key_down" and address == kb then
                    component.invoke(gpu, "set", 1, 2, "key " .. char)
                    component.invoke(gpu, "setPixel", 5, 5, 200)
                  end
                end
                """, ModItems.GPU3.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> be.core().start())
                .thenWaitUntil(() -> helper.assertTrue("ready true".equals(readResult(be.core().machine())),
                        "result " + readResult(be.core().machine()) + " / " + be.core().machine().crashMessage()))
                .thenExecute(() -> {
                    ScreenBlockEntity screen = (ScreenBlockEntity) helper.getBlockEntity(screenPos);
                    helper.assertTrue(screenText(screen.buffer()).startsWith("hello screen"), "screen shows the text");
                    KeyboardBlockEntity kb = (KeyboardBlockEntity) helper.getBlockEntity(screenPos.above());
                    kb.component().signalAll("key_down", kb.component().address(), 65, 65, "tester");
                })
                .thenWaitUntil(() -> {
                    ScreenBlockEntity screen = (ScreenBlockEntity) helper.getBlockEntity(screenPos);
                    helper.assertTrue(screenText(screen.buffer()).contains("key 65"), "key reached the computer");
                    helper.assertTrue(screen.buffer().pixel(4, 4) == 200, "pixel drawn");
                    helper.assertTrue(screen.screenOn(), "screen is lit while the computer runs");
                })
                .thenExecute(() -> be.core().stop())
                .thenExecute(() -> {
                    ScreenBlockEntity screen = (ScreenBlockEntity) helper.getBlockEntity(screenPos);
                    helper.assertFalse(screen.screenOn(), "screen goes dark when the computer is off");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_drives")
    public static void driveSpeedIsSimulated(GameTestHelper helper) {
        configure();
        Config.DRIVE_SIMULATE.set(true);
        Config.HDD_READ.set(64.0);
        Config.HDD_WRITE.set(48.0);
        Config.HDD_SEEK.set(5);
        // 32 KiB written at 48 KiB/s and read back at 64 KiB/s on a tier 1 drive: at least 1.1 s of game time.
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), """
                local fs
                for a in component.list("filesystem") do
                  if a ~= computer.tmpAddress() then fs = a end
                end
                local chunk = string.rep("x", 4096)
                local t0 = computer.uptime()
                local h = component.invoke(fs, "open", "/big.bin", "w")
                for i = 1, 8 do component.invoke(fs, "write", h, chunk) end
                component.invoke(fs, "close", h)
                h = component.invoke(fs, "open", "/big.bin", "r")
                local n = 0
                while true do
                  local d = component.invoke(fs, "read", h, 4096)
                  if not d then break end
                  n = n + #d
                end
                component.invoke(fs, "close", h)
                local ms = math.floor((computer.uptime() - t0) * 1000)
                result(n == 32768 and ms or -2)
                while true do computer.pullSignal() end
                """);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> be.core().start())
                .thenWaitUntil(() -> {
                    Machine m = be.core().machine();
                    if (m.state() == Machine.State.CRASHED) throw new GameTestAssertException("crashed: " + m.crashMessage());
                    long ms = number(readResult(m));
                    if (ms < 0) throw new GameTestAssertException("no result yet: " + readResult(m));
                    helper.assertTrue(ms >= 1100, "32 KiB took only " + ms + " ms");
                })
                .thenExecute(() -> {
                    be.core().stop();
                    Config.DRIVE_SIMULATE.set(false);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_fit")
    public static void monitorFitResolution(GameTestHelper helper) {
        configure();
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.CABLE.get());
        for (int x = 4; x < 8; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), ModBlocks.SCREEN3.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        }
        int[] fit = dev.computah.display.ScreenGeometry.fitResolution(4, 1, 160, 50);
        String expected = fit[0] + "x" + fit[1];
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), """
                local gpu = component.list("gpu")()
                local screen
                repeat
                  computer.pullSignal(0.1)
                  screen = component.list("screen")()
                until screen and component.invoke(screen, "getAspectRatio") == 4
                component.invoke(gpu, "bind", screen)
                local w, h = component.invoke(gpu, "getFitResolution")
                component.invoke(gpu, "setResolution", w, h)
                local cw, ch = component.invoke(gpu, "getResolution")
                result(cw .. "x" .. ch)
                while true do computer.pullSignal() end
                """, ModItems.GPU3.get());
        waitForResult(helper, () -> be.core().machine(), expected, () -> be.core().stop());
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "computah_recipes")
    public static void dataRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("bios_eeprom", "os_floppy", "floppy_red", "floppy_light_blue")) {
            var r = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, id));
            helper.assertTrue(r.isPresent() && r.get().value() instanceof dev.computah.item.DataShapelessRecipe, "recipe " + id + " loaded");
        }
        var bios = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "bios_eeprom")).orElseThrow();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                List.of(new ItemStack(ModItems.EEPROM.get()), new ItemStack(net.minecraft.world.item.Items.BOOK)));
        helper.assertTrue(bios.value() instanceof dev.computah.item.DataShapelessRecipe d && d.matches(input, helper.getLevel())
                && d.assemble(input, helper.getLevel().registryAccess()).has(ModComponents.EEPROM.get()), "EEPROM + book makes the BIOS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "computah_multiblock")
    public static void screensMergeIntoMultiblocks(GameTestHelper helper) {
        BlockState s = ModBlocks.SCREEN2.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        // A 3x2 monitor.
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) helper.setBlock(new BlockPos(1 + x, 1 + y, 1), s);
        // A row of 10 splits into 8 + 2.
        for (int x = 0; x < 10; x++) helper.setBlock(new BlockPos(1 + x, 1, 4), s);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    List<String> shapes = new ArrayList<>();
                    for (int x = 0; x < 3; x++) {
                        ScreenBlockEntity be = (ScreenBlockEntity) helper.getBlockEntity(new BlockPos(1 + x, 2, 1));
                        ScreenBlockEntity o = be.origin();
                        helper.assertTrue(o != null && o.width() == 3 && o.height() == 2, "3x2 monitor, got " + (o == null ? null : o.width() + "x" + o.height()));
                    }
                    int[] widths = new int[10];
                    for (int x = 0; x < 10; x++) widths[x] = ((ScreenBlockEntity) helper.getBlockEntity(new BlockPos(1 + x, 1, 4))).width();
                    for (int w : widths) shapes.add(String.valueOf(w));
                    java.util.Set<ScreenBlock.Part> parts = new java.util.HashSet<>();
                    for (int x = 0; x < 3; x++) {
                        BlockState st = helper.getBlockState(new BlockPos(1 + x, 2, 1));
                        parts.add(st.getValue(ScreenBlock.HORIZONTAL));
                        helper.assertTrue(st.getValue(ScreenBlock.VERTICAL) == ScreenBlock.Part.FIRST, "top row is FIRST vertically");
                    }
                    helper.assertTrue(parts.equals(java.util.EnumSet.of(ScreenBlock.Part.FIRST, ScreenBlock.Part.MIDDLE, ScreenBlock.Part.LAST)),
                            "bezel parts across the top row: " + parts);
                    helper.assertTrue(widths[0] == 8 && widths[9] == 2 || widths[0] == 2 && widths[9] == 8, "row split 8+2, got " + shapes);
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR))
                .thenIdle(3)
                .thenExecute(() -> {
                    ScreenBlockEntity be = (ScreenBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
                    helper.assertTrue(be.origin() != null && be.origin().height() == 1, "monitor split after breaking a block");
                })
                .thenSucceed();
    }

    private static final String RECEIVER = """
            local modem = component.list("modem")()
            component.invoke(modem, "open", 42)
            result("listening")
            while true do
              local name, _, from, port, distance, message = computer.pullSignal()
              if name == "modem_message" and port == 42 then result("got " .. message) end
            end
            """;

    private static final String SENDER = """
            local modem = component.list("modem")()
            while true do
              component.invoke(modem, "broadcast", 42, "ping")
              computer.pullSignal(0.2)
            end
            """;

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_wired")
    public static void networkCardsTalkOverCables(GameTestHelper helper) {
        configure();
        for (int x = 2; x < 6; x++) helper.setBlock(new BlockPos(x, 1, 1), ModBlocks.CABLE.get());
        helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.RELAY.get());
        CaseBlockEntity a = computer(helper, new BlockPos(1, 1, 1), RECEIVER, ModItems.NETWORK_CARD.get());
        CaseBlockEntity b = computer(helper, new BlockPos(6, 1, 1), SENDER, ModItems.NETWORK_CARD.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    a.core().start();
                    b.core().start();
                })
                .thenWaitUntil(() -> helper.assertTrue("got ping".equals(readResult(a.core().machine())),
                        "receiver: " + readResult(a.core().machine()) + " " + a.core().machine().crashMessage() + " / " + b.core().machine().crashMessage()))
                .thenExecute(() -> {
                    a.core().stop();
                    b.core().stop();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_wireless")
    public static void wirelessCardsTalk(GameTestHelper helper) {
        configure();
        CaseBlockEntity a = computer(helper, new BlockPos(1, 1, 1), RECEIVER, ModItems.WIRELESS_CARD1.get());
        CaseBlockEntity b = computer(helper, new BlockPos(9, 1, 6), SENDER, ModItems.WIRELESS_CARD1.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    a.core().start();
                    b.core().start();
                })
                .thenWaitUntil(() -> helper.assertTrue("got ping".equals(readResult(a.core().machine())), "receiver: " + readResult(a.core().machine())))
                .thenExecute(() -> {
                    a.core().stop();
                    b.core().stop();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_rack")
    public static void rackServerRuns(GameTestHelper helper) {
        configure();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.RACK.get());
        RackBlockEntity rack = (RackBlockEntity) helper.getBlockEntity(pos);
        ItemStack server = new ItemStack(ModItems.SERVER.get());
        List<ItemStack> parts = new ArrayList<>();
        for (PartKind.SlotType ignored : PartKind.SERVER) parts.add(ItemStack.EMPTY);
        parts.set(3, new ItemStack(ModItems.CPU2.get()));
        parts.set(4, new ItemStack(ModItems.RAM2.get()));
        parts.set(7, new ItemStack(ModItems.HDD1.get()));
        parts.set(10, eeprom("result('server ok') while true do computer.pullSignal() end"));
        ServerItem.setParts(server, parts);
        rack.servers().setItem(2, server);
        waitForResult(helper, () -> rack.core(2).machine(), "server ok", () -> {
            rack.core(2).stop();
            // Parts written back into the server item.
            helper.assertTrue(!ServerItem.parts(rack.servers().getItem(2)).get(7).isEmpty(), "hdd still in the server item");
        });
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_links")
    public static void linkedChestIsAComponent(GameTestHelper helper) {
        configure();
        BlockPos chestPos = new BlockPos(8, 1, 6);
        helper.setBlock(chestPos, Blocks.CHEST);
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), """
                while true do
                  local inv = component.list("inventory")()
                  local ok, size = false, nil
                  if inv then ok, size = pcall(component.invoke, inv, "getInventorySize") end
                  if ok then
                    result("inventory " .. size)
                  else
                    result("none")
                  end
                  computer.pullSignal(0.1)
                end
                """, ModItems.WIRELESS_CARD2.get());
        GlobalPos host = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)));
        GlobalPos chest = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(chestPos));
        LinkSavedData links = LinkSavedData.get(helper.getLevel().getServer());
        links.clear(host);
        links.toggle(host, chest, 32);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> be.core().start())
                .thenWaitUntil(() -> helper.assertTrue("inventory 27".equals(readResult(be.core().machine())), "result " + readResult(be.core().machine())
                        + " state " + be.core().machine().state() + " " + be.core().machine().crashMessage()))
                .thenExecute(() -> helper.setBlock(chestPos, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue("none".equals(readResult(be.core().machine())), "result " + readResult(be.core().machine())
                        + " state " + be.core().machine().state() + " " + be.core().machine().crashMessage()))
                .thenExecute(() -> {
                    be.core().stop();
                    links.clear(host);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_power")
    public static void powerIsNeededWhenEnabled(GameTestHelper helper) {
        configure();
        Config.POWER_ENABLED.set(true);
        Config.COMPUTER_COST.set(10.0);
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), "result('on') while true do computer.pullSignal() end");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(be.core().machine().start(), "no energy: should not start");
                    helper.assertTrue("not enough energy".equals(be.core().machine().crashMessage()), "message " + be.core().machine().crashMessage());
                    be.energyStorage().receiveEnergy(300, false);
                    helper.assertTrue(be.core().machine().start(), "starts with energy");
                })
                .thenWaitUntil(() -> helper.assertTrue(be.core().machine().state() == Machine.State.CRASHED, "runs out of energy"))
                .thenExecute(() -> {
                    helper.assertTrue("not enough energy".equals(be.core().machine().crashMessage()), "message " + be.core().machine().crashMessage());
                    Config.POWER_ENABLED.set(false);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_hardware")
    public static void removingMemoryStopsTheComputer(GameTestHelper helper) {
        configure();
        CaseBlockEntity be = computer(helper, new BlockPos(1, 1, 1), "result('on') while true do computer.pullSignal() end");
        waitForResult(helper, () -> be.core().machine(), "on", () -> {
            var inv = be.core().inventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.layout()[i] == PartKind.SlotType.RAM) inv.setItem(i, ItemStack.EMPTY);
            }
            be.core().tick();
            helper.assertTrue(be.core().machine().state() == Machine.State.CRASHED, "crashed after removing RAM");
        });
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_persist")
    public static void runningComputerSurvivesReload(GameTestHelper helper) {
        configure();
        BlockPos pos = new BlockPos(1, 1, 1);
        CaseBlockEntity be = computer(helper, pos, """
                local n = 0
                local co = coroutine.wrap(function()
                  while true do
                    n = n + 1
                    result(n)
                    computer.pullSignal(0.05)
                  end
                end)
                co()
                """);
        long[] savedCount = new long[1];
        double[] savedUptime = new double[1];
        CaseBlockEntity[] fresh = new CaseBlockEntity[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> be.core().start())
                .thenWaitUntil(() -> {
                    String r = readResult(be.core().machine());
                    helper.assertTrue(number(r) >= 5, "counting: " + r);
                })
                .thenExecute(() -> {
                    // Save the block entity like a chunk save, then replace it with a freshly loaded copy.
                    var registries = helper.getLevel().registryAccess();
                    var tag = be.saveWithFullMetadata(registries);
                    savedCount[0] = number(readResult(be.core().machine()));
                    savedUptime[0] = be.core().machine().uptime();
                    helper.assertTrue(savedCount[0] >= 5, "count before saving");
                    BlockPos abs = helper.absolutePos(pos);
                    CaseBlockEntity copy = new CaseBlockEntity(abs, helper.getLevel().getBlockState(abs));
                    copy.loadWithComponents(tag, registries);
                    helper.getLevel().setBlockEntity(copy);
                    fresh[0] = copy;
                    helper.assertTrue(be.core().machine().state() != Machine.State.RUNNING, "old copy stopped");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    // A rebooted computer would start again at uptime 0.
                    Machine m = fresh[0].core().machine();
                    helper.assertTrue(m.isOn(), "restored machine runs: " + m.state() + " " + m.crashMessage());
                    helper.assertTrue(m.uptime() >= savedUptime[0], "uptime carried over: " + m.uptime() + " vs " + savedUptime[0]);
                })
                .thenWaitUntil(() -> {
                    Machine m = fresh[0].core().machine();
                    String r = readResult(m);
                    helper.assertTrue(number(r) > savedCount[0], "continues counting: " + r + " after " + savedCount[0]);
                })
                .thenExecute(() -> {
                    fresh[0].core().stop();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT, batch = "computah_redstone")
    public static void redstoneCardDrivesOutputs(GameTestHelper helper) {
        configure();
        BlockPos pos = new BlockPos(1, 1, 1);
        CaseBlockEntity be = computer(helper, pos, """
                local rs = component.list("redstone")()
                for side = 0, 5 do component.invoke(rs, "setOutput", side, 15) end
                result("set")
                while true do computer.pullSignal() end
                """, ModItems.REDSTONE_CARD.get());
        helper.setBlock(pos.east(), Blocks.REDSTONE_LAMP);
        waitForResult(helper, () -> be.core().machine(), "set", () -> {
            helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(pos), Direction.WEST) == 15, "case outputs redstone");
            be.core().stop();
        });
    }
}
