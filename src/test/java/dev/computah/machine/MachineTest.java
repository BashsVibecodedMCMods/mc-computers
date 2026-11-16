package dev.computah.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MachineTest {
    private static Machine boot(TestMachines.Host host, TestMachines.Sink sink, String code) {
        Machine m = new Machine(host);
        m.setComponents(List.of(sink, TestMachines.eeprom(TestMachines.sinkLua(sink) + code)));
        assertTrue(m.start(), "start: " + m.crashMessage());
        return m;
    }

    @Test
    public void runsEepromAndCallsComponents() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, "sink.put(1 + 2, 'hé', computer.address() == component.list('computer')())\nsink.finish()\nwhile true do computer.pullSignal() end");
        TestMachines.run(m, () -> sink.done, 400);
        assertEquals(List.of(3, "hé", true), sink.values, "crash: " + m.crashMessage());
        m.stop();
        assertEquals(Machine.State.STOPPED, m.state());
    }

    @Test
    public void signalsAndTimeouts() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, """
                computer.pushSignal('hello', 42, 'x')
                local name, a, b = computer.pullSignal(1)
                sink.put(name, a, b)
                local t = computer.uptime()
                local r = computer.pullSignal(0.2)
                sink.put(r == nil, computer.uptime() - t >= 0.2)
                -- signals pass through user coroutines
                local co = coroutine.create(function()
                  computer.pushSignal('inner')
                  local n = computer.pullSignal()
                  coroutine.yield(n)
                  return 'done'
                end)
                sink.put(select(2, coroutine.resume(co)), select(2, coroutine.resume(co)))
                sink.finish()
                computer.pullSignal()
                """);
        TestMachines.run(m, () -> sink.done, 400);
        assertEquals(List.of("hello", 42, "x", true, true, "inner", "done"), sink.values, "crash: " + m.crashMessage());
        m.stop();
    }

    @Test
    public void indirectAndLimitedCalls() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, """
                sink.put(sink.indirect())
                local t = computer.uptime()
                for i = 1, 6 do sink.limited() end
                sink.put(computer.uptime() > t)
                sink.finish()
                computer.pullSignal()
                """);
        TestMachines.run(m, () -> sink.done, 400);
        assertEquals(List.of("server:false", true), sink.values, "crash: " + m.crashMessage());
        m.stop();
    }

    @Test
    public void errorsCrashTheMachine() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, "error('boom')");
        TestMachines.run(m, () -> false, 400);
        assertEquals(Machine.State.CRASHED, m.state());
        assertTrue(m.crashMessage().contains("boom"), m.crashMessage());
    }

    @Test
    public void haltsWhenBiosReturns() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, "return");
        TestMachines.run(m, () -> false, 400);
        assertEquals("computer halted", m.crashMessage());
    }

    @Test
    public void tooLongWithoutYielding() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, "local ok, e = pcall(function() while true do end end)\nsink.put(e)\nwhile true do end");
        TestMachines.run(m, () -> false, 2000);
        assertEquals(Machine.State.CRASHED, m.state());
        assertTrue(sink.values.get(0).toString().contains("too long without yielding"), sink.values.toString());
        assertTrue(m.crashMessage().contains("too long without yielding"), m.crashMessage());
    }

    @Test
    public void memoryLimit() throws Exception {
        var host = new TestMachines.Host();
        host.spec = new MachineSpec(256 * 1024, 32, 1, 5, 0);
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, """
                sink.put(computer.totalMemory(), computer.freeMemory() > 0)
                local t = {}
                for i = 1, 1e6 do t[i] = ('x'):rep(100) .. i; if i % 1000 == 0 then sink.put(computer.freeMemory()) computer.pullSignal(0) end end
                """);
        TestMachines.run(m, () -> false, 3000);
        assertEquals(Machine.State.CRASHED, m.state());
        assertEquals("not enough memory", m.crashMessage(), sink.values.toString());
        assertEquals(256 * 1024, sink.values.get(0));
    }

    @Test
    public void sandboxHidesUnsafeFunctions() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        Machine m = boot(host, sink, """
                sink.put(type(io), type(dofile), type(loadfile), type(string.dump), type(debug.sethook), type(print))
                sink.put(load(string.char(27) .. 'Lua') == nil)
                sink.put(unicode.len('héllo'), unicode.sub('héllo', 2, 3), os.date('!%Y', 0))
                sink.put(("abc"):upper())
                sink.finish()
                computer.pullSignal()
                """);
        TestMachines.run(m, () -> sink.done, 400);
        assertEquals(List.of("nil", "nil", "nil", "nil", "nil", "nil", true, 5, "él", "1970", "ABC"), sink.values, "crash: " + m.crashMessage());
        m.stop();
    }

    @Test
    public void componentEvents() throws Exception {
        var host = new TestMachines.Host();
        var sink = new TestMachines.Sink();
        var extra = TestMachines.eeprom("");
        Machine m = boot(host, sink, """
                sink.put('ready')
                local e, addr, t = computer.pullSignal(5)
                sink.put(e, t)
                sink.finish()
                computer.pullSignal()
                """);
        TestMachines.run(m, () -> !sink.values.isEmpty(), 400);
        var eeprom = m.components().stream().filter(c -> c.type().equals("eeprom")).findFirst().orElseThrow();
        m.setComponents(List.of(sink, eeprom, extra));
        TestMachines.run(m, () -> sink.done, 400);
        assertEquals(List.of("ready", "component_added", "eeprom"), sink.values, "crash: " + m.crashMessage());
        m.stop();
    }
}
