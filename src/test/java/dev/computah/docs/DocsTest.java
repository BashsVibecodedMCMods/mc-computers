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
    private static Path projectFile(String name) throws Exception {
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
