package dev.computah.docs;

import dev.computah.api.Component;
import dev.computah.component.*;
import dev.computah.network.ModemComponent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** docs/COMPONENTS.md must list every component method (regenerate it with tools/components_doc.py). */
public class DocsTest {
    /** Every function documented as a heading "### lib.name(" in a doc file, as "lib.name". */
    public static java.util.List<String> documentedFunctions(String doc) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<>();
        var pattern = java.util.regex.Pattern.compile("\\b([a-z]+)\\.([A-Za-z_]+)\\(");
        for (String line : Files.readAllLines(projectFile(doc))) {
            if (!line.startsWith("### ")) continue;
            var m = pattern.matcher(line);
            while (m.find()) {
                String name = m.group(1) + "." + m.group(2);
                if (!out.contains(name)) out.add(name);
            }
        }
        return out;
    }

    @Test
    public void everyLibraryHasAManPage() throws Exception {
        java.util.Map<String, java.util.List<String>> byLib = new java.util.TreeMap<>();
        for (String f : documentedFunctions("docs/API.md")) byLib.computeIfAbsent(f.substring(0, f.indexOf('.')), k -> new java.util.ArrayList<>()).add(f);
        assertTrue(byLib.size() >= 15, "libraries found in API.md: " + byLib.keySet());
        Path man = projectFile("src/main/resources/data/computah/lua/os/usr/man");
        for (var e : byLib.entrySet()) {
            Path page = man.resolve(e.getKey());
            assertTrue(Files.exists(page), "no man page for " + e.getKey() + " (run python3 tools/api_man.py .)");
            String text = Files.readString(page);
            for (String f : e.getValue()) assertTrue(text.contains(f + "("), f + " missing from man " + e.getKey() + " (run python3 tools/api_man.py .)");
        }
    }

    public static Path projectFile(String name) throws Exception {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve(name))) p = p.getParent();
        assertNotNull(p, "can't find " + name);
        return p.resolve(name);
    }

    @Test
    public void componentReferenceIsComplete() throws Exception {
        String doc = Files.readString(projectFile("docs/COMPONENTS.md"));
        List<Class<? extends Component>> classes = List.of(EepromComponent.class, FilesystemComponent.class, GpuComponent.class,
                ScreenComponent.class, ModemComponent.class, RedstoneComponent.class, AdapterComponents.Inventory.class,
                AdapterComponents.Tank.class, AdapterComponents.Energy.class, dev.computah.machine.Machine.class);
        for (Class<? extends Component> c : classes) {
            for (var m : c.getMethods()) {
                var cb = m.getAnnotation(dev.computah.api.Callback.class);
                if (cb == null) continue;
                String name = cb.value().isEmpty() ? m.getName() : cb.value();
                assertTrue(doc.contains("`" + name + "("), c.getSimpleName() + "." + name + " is missing from docs/COMPONENTS.md");
            }
        }
    }
}
