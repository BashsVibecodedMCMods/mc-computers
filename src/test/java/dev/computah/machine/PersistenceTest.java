package dev.computah.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A running machine saved and restored into a fresh Machine continues exactly where it was. */
public class PersistenceTest {
    private static final String PROGRAM = """
            local counter = 0
            local doubled = setmetatable({}, {__index = function(_, k) return k * 2 end})
            local co = coroutine.wrap(function()
              while true do
                local ok, err = pcall(function()
                  counter = counter + 1
                  local name, n = computer.pullSignal()
                  if name == "boom" then error("boom " .. counter) end
                  sink.put(name, n, counter, doubled[counter], computer.uptime() > 0)
                end)
                if not ok then sink.put("caught", err:match("boom %d+")) end
              end
            end)
            co()
            """;

    @Test
    public void runningProgramSurvivesSaveAndRestore() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        var eeprom = TestMachines.eeprom(TestMachines.sinkLua(sink) + PROGRAM);
        Machine m = new Machine(host, "11111111-2222-3333-4444-555555555555");
        m.setComponents(List.of(sink, eeprom));
        assertTrue(m.start());
        TestMachines.run(m, () -> false, 20);
        m.signal("a", 1);
        TestMachines.run(m, () -> sink.values.size() >= 5, 200);
        assertEquals(List.of("a", 1, 1, 2, true), sink.values, "crash: " + m.crashMessage());

        // A signal still queued when the machine is saved is delivered after restoring.
        m.signal("b", 2);
        byte[] saved = m.saveState(1000);
        assertNotNull(saved);
        m.stop();

        Machine m2 = new Machine(host, "11111111-2222-3333-4444-555555555555");
        m2.setComponents(List.of(sink, eeprom));
        assertTrue(m2.restoreState(saved), "restore failed");
        assertEquals(Machine.State.RUNNING, m2.state());
        sink.values.clear();
        TestMachines.run(m2, () -> sink.values.size() >= 5, 200);
        assertEquals(List.of("b", 2, 2, 4, true), sink.values, "crash: " + m2.crashMessage());

        // pcall frames still work after restoring.
        sink.values.clear();
        m2.signal("boom");
        TestMachines.run(m2, () -> sink.values.size() >= 2, 200);
        assertEquals(List.of("caught", "boom 3"), sink.values, "crash: " + m2.crashMessage());

        // And it can be saved again.
        byte[] again = m2.saveState(1000);
        assertNotNull(again);
        m2.stop();
    }

    @Test
    public void indirectCallInProgressIsResumed() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        var eeprom = TestMachines.eeprom(TestMachines.sinkLua(sink) + "sink.put(sink.indirect()) sink.finish() computer.pullSignal()");
        Machine m = new Machine(host, "aaaaaaaa-2222-3333-4444-555555555555");
        m.setComponents(List.of(sink, eeprom));
        assertTrue(m.start());
        // Without ticking, the machine boots and then waits for the indirect call to be performed.
        Thread.sleep(200);
        byte[] saved = m.saveState(1000);
        assertNotNull(saved);
        assertTrue(sink.values.isEmpty());
        m.stop();
        Machine m2 = new Machine(host, "aaaaaaaa-2222-3333-4444-555555555555");
        m2.setComponents(List.of(sink, eeprom));
        assertTrue(m2.restoreState(saved));
        TestMachines.run(m2, () -> sink.done, 200);
        assertEquals(List.of("server:false"), sink.values, "crash: " + m2.crashMessage());
        m2.stop();
    }
}
