package dev.computah.machine;

import dev.computah.component.*;
import dev.computah.display.DisplayBuffer;
import dev.computah.fs.OsFiles;
import dev.computah.fs.RamFileSystem;
import dev.computah.item.PartItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Boots the real BIOS and Computah OS headless, with a GPU, screen and keyboard, and types commands. */
public class OsBootTest {
    @BeforeAll
    public static void loadOs() throws Exception {
        Path init = Path.of(OsBootTest.class.getResource("/data/computah/lua/os/init.lua").toURI());
        OsFiles.loadDirectory(init.getParent());
    }

    static final class Rig {
        final TestMachines.Host host = new TestMachines.Host();
        final DisplayBuffer display = new DisplayBuffer(DisplayBuffer.Tier.of(3));
        final KeyboardComponent keyboard = new KeyboardComponent();
        final ScreenComponent screen = new ScreenComponent(UUID.randomUUID().toString(), display, () -> List.of(keyboard.address()));
        final GpuComponent gpu = new GpuComponent(UUID.randomUUID().toString(), 3);
        final FilesystemComponent os = new FilesystemComponent(OsFiles.fileSystem(), "Computah OS");
        final FilesystemComponent hdd = new FilesystemComponent(new RamFileSystem(1024 * 1024), "hdd");
        final EepromComponent eeprom = new EepromComponent(UUID.randomUUID().toString(),
                new EepromComponent.Contents(PartItem.biosCode().getBytes(StandardCharsets.UTF_8), new byte[0], "BIOS", false), c -> {
        });
        Machine machine;

        Rig(long memory) {
            host.spec = new MachineSpec(memory, 16, 2, 5, 64 * 1024);
            machine = new Machine(host);
            machine.setComponents(List.of(screen, keyboard, gpu, os, hdd, eeprom));
            assertTrue(machine.start());
        }

        /** Saves the running machine and restores it into a new Machine with a new GPU object, like a reload. */
        void reload() throws Exception {
            byte[] saved = machine.saveState(2000);
            assertNotNull(saved);
            String address = machine.address();
            machine.stop();
            GpuComponent newGpu = new GpuComponent(gpu.address(), 3);
            machine = new Machine(host, address);
            machine.setComponents(List.of(screen, keyboard, newGpu, os, hdd, eeprom));
            assertTrue(machine.restoreState(saved), "restore failed");
        }

        String screenText() {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < display.rows(); y++) {
                StringBuilder line = new StringBuilder();
                for (int x = 0; x < display.cols(); x++) line.appendCodePoint(display.charAt(x, y));
                String l = line.toString().replaceAll("\\s+$", "");
                if (!l.isEmpty()) sb.append(l).append('\n');
            }
            return sb.toString();
        }

        void run(BooleanSupplier until, int ticks) throws InterruptedException {
            for (int i = 0; i < ticks && !until.getAsBoolean(); i++) {
                if (machine.state() != Machine.State.RUNNING) fail("machine stopped: " + machine.crashMessage() + "\n" + screenText());
                machine.update();
                Thread.sleep(2);
            }
        }

        void type(String line) throws InterruptedException {
            for (int cp : line.codePoints().toArray()) {
                keyboard.signalAll("key_down", keyboard.address(), cp, 0, "tester");
                run(() -> false, 2);
            }
            keyboard.signalAll("key_down", keyboard.address(), 13, 257, "tester");
            run(() -> false, 2);
        }
    }

    @Test
    public void bootsToShellAndRunsCommands() throws Exception {
        Rig rig = new Rig(1024 * 1024);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        assertTrue(rig.screenText().contains("/#"), rig.screenText());

        rig.type("echo hello world > /tmp/a.txt; cat /tmp/a.txt");
        rig.run(() -> rig.screenText().contains("hello world\n"), 2000);
        assertTrue(rig.screenText().contains("hello world\n"), rig.screenText());
        // The cursor is erased from lines that were entered: only the current prompt line may show it.
        int cursorRows = 0;
        for (int y = 0; y < rig.display.rows(); y++) {
            for (int x = 0; x < rig.display.cols(); x++) {
                if (rig.display.bgAt(x, y) == 0xFFFFFF) {
                    cursorRows++;
                    break;
                }
            }
        }
        assertTrue(cursorRows <= 1, "cursor left behind on " + cursorRows + " rows");

        rig.type("lua -e");
        rig.type("free");
        rig.run(() -> rig.screenText().contains("Total"), 2000);
        String text = rig.screenText();
        assertTrue(text.contains("Total"), text);
        System.out.println(text);

        // Background job and ps.
        rig.type("sleep 30 &");
        rig.type("ps");
        rig.run(() -> rig.screenText().contains("sleep (bg)"), 2000);
        assertTrue(rig.screenText().contains("sleep (bg)"), rig.screenText());

        // Pipes.
        rig.type("ls /bin | grep ^ed");
        rig.run(() -> rig.screenText().contains("edit.lua"), 2000);
        assertTrue(rig.screenText().contains("edit.lua"), rig.screenText());
        rig.machine.stop();
    }

    private static final String SCRIPT = """
            local thread = require("thread")
            local event = require("event")
            local serialization = require("serialization")
            local fs = require("filesystem")
            local results = {}
            local function check(name, ok) results[#results + 1] = (ok and "ok " or "FAIL ") .. name end
            -- threads see every signal and run alongside each other
            local seen = {}
            local t1 = thread.create(function() seen[1] = select(2, event.pull("ping")) end)
            local t2 = thread.create(function() seen[2] = select(2, event.pull("ping")) end)
            os.sleep(0.1)
            event.push("ping", "x")
            thread.waitForAll({t1, t2}, 2)
            check("threads", seen[1] == "x" and seen[2] == "x")
            -- listeners and timers
            local heard, fired = 0, 0
            local function l() heard = heard + 1 end
            event.listen("tick", l)
            event.push("tick")
            event.push("tick")
            local id = event.timer(0.05, function() fired = fired + 1 end, 3)
            os.sleep(0.5)
            event.ignore("tick", l)
            check("listeners", heard == 2)
            check("timers", fired == 3)
            -- kill and status
            local t3 = thread.create(function() os.sleep(100) end)
            os.sleep(0)
            t3:kill()
            check("kill", t3:status() == "dead")
            -- detached threads outlive their parent
            local child
            local parent = thread.create(function()
              child = thread.create(function() os.sleep(100) end)
              child:detach()
            end)
            parent:join(1)
            check("detach", child:status() ~= "dead")
            child:kill()
            -- io and files
            local f = io.open("/tmp/t.txt", "w")
            f:write("line1\\n", 42, "\\nline3")
            f:close()
            local lines = {}
            for line in io.lines("/tmp/t.txt") do lines[#lines + 1] = line end
            check("io", #lines == 3 and lines[2] == "42")
            check("serialization", serialization.unserialize(serialization.serialize({1, "a", x = {y = true}})).x.y == true)
            check("fs", fs.exists("/tmp/t.txt") and fs.size("/tmp/t.txt") > 0 and fs.isDirectory("/bin"))
            -- coroutines inside threads can still wait for signals
            local co = coroutine.wrap(function() return select(2, event.pull("inner")) end)
            thread.create(function() os.sleep(0.05) event.push("inner", 7) end)
            check("coroutines", co() == 7)
            print(table.concat(results, ", "))
            """;

    @Test
    public void librariesWork() throws Exception {
        Rig rig = new Rig(1024 * 1024);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        try (var h = rig.hdd.fileSystem().open("test.lua", dev.computah.fs.FileSystem.Mode.WRITE)) {
            h.write(SCRIPT.getBytes(StandardCharsets.UTF_8));
        }
        rig.type("lua /mnt/" + rig.hdd.address().substring(0, 3) + "/test.lua");
        rig.run(() -> rig.screenText().contains("coroutines"), 4000);
        String text = rig.screenText();
        assertTrue(text.contains("ok threads, ok listeners, ok timers, ok kill, ok detach, ok io, ok serialization, ok fs, ok coroutines"), text);
        rig.machine.stop();
    }

    @Test
    public void editorSavesFiles() throws Exception {
        Rig rig = new Rig(1024 * 1024);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        rig.type("edit /tmp/new.txt");
        rig.run(() -> rig.screenText().contains("Ctrl+S save"), 2000);
        for (int cp : "hello editor".codePoints().toArray()) {
            rig.keyboard.signalAll("key_down", rig.keyboard.address(), cp, 0, "tester");
            rig.run(() -> false, 2);
        }
        // Ctrl+S then Ctrl+Q: control key down, then the letter.
        rig.keyboard.signalAll("key_down", rig.keyboard.address(), 0, 341, "tester");
        rig.keyboard.signalAll("key_down", rig.keyboard.address(), 19, 83, "tester");
        rig.run(() -> rig.screenText().contains("saved"), 500);
        rig.keyboard.signalAll("key_down", rig.keyboard.address(), 17, 81, "tester");
        rig.keyboard.signalAll("key_up", rig.keyboard.address(), 0, 341, "tester");
        rig.run(() -> rig.screenText().contains("/#"), 1000);
        rig.type("cat /tmp/new.txt");
        rig.run(() -> rig.screenText().contains("hello editor\n"), 1000);
        assertTrue(rig.screenText().contains("hello editor\n"), rig.screenText());
        rig.machine.stop();
    }

    @Test
    public void runningOsSurvivesSaveAndRestore() throws Exception {
        Rig rig = new Rig(1024 * 1024);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        rig.type("sleep 1000 &");
        rig.type("lua");
        rig.run(() -> rig.screenText().contains("lua>"), 1000);
        rig.type("x = 6 * 7");
        rig.run(() -> false, 20);
        rig.reload();
        rig.type("=x .. ' ' .. computer.uptime()");
        rig.run(() -> rig.screenText().contains("\"42 "), 1000);
        String text = rig.screenText();
        assertTrue(text.contains("\"42 "), text);
        rig.type("exit()");
        rig.type("ps");
        rig.run(() -> rig.screenText().contains("sleep (bg)"), 1000);
        assertTrue(rig.screenText().contains("sleep (bg)"), rig.screenText());
        rig.machine.stop();
    }

    @Test
    public void installsToHardDrive() throws Exception {
        Rig rig = new Rig(1024 * 1024);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        rig.type("install " + rig.hdd.address().substring(0, 6));
        rig.run(() -> rig.screenText().contains("done ("), 4000);
        assertTrue(rig.screenText().contains("done ("), rig.screenText());
        assertTrue(rig.hdd.fileSystem().exists("init.lua"));
        assertTrue(rig.hdd.fileSystem().exists("bin/sh.lua"));
        rig.machine.stop();
    }

    @Test
    public void fitsInSmallestRam() throws Exception {
        Rig rig = new Rig(PartItem.RAM_SIZES[1]);
        rig.run(() -> rig.screenText().contains("/#"), 3000);
        rig.type("free");
        rig.run(() -> rig.screenText().contains("Total"), 2000);
        String text = rig.screenText();
        System.out.println(text);
        assertTrue(text.contains("Total"), text);
        rig.machine.stop();
    }
}
