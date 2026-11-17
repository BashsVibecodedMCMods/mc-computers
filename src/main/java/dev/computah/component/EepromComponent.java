package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;

import java.util.zip.CRC32;

/**
 * The {@code eeprom} component: the BIOS chip. It holds up to {@link #CODE_SIZE} bytes of Lua code that the machine
 * runs at boot, plus {@link #DATA_SIZE} bytes of data (by convention the boot drive address). See docs/EEPROM.md.
 */
public class EepromComponent extends Component {
    public static final int CODE_SIZE = 4096;
    public static final int DATA_SIZE = 256;

    /** Snapshot of an EEPROM's contents, stored in the item. */
    public record Contents(byte[] code, byte[] data, String label, boolean readOnly) {
        public static final Contents EMPTY = new Contents(new byte[0], new byte[0], "EEPROM", false);
    }

    /** Persists changes (called on the server thread). */
    public interface Store {
        void save(Contents contents);
    }

    private final String address;
    private final Store store;
    private volatile Contents contents;

    public EepromComponent(String address, Contents contents, Store store) {
        this.address = address;
        this.contents = contents;
        this.store = store;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "eeprom";
    }

    public Contents contents() {
        return contents;
    }

    private void update(Contents c) {
        contents = c;
        store.save(c);
    }

    public static String checksum(byte[] code) {
        CRC32 crc = new CRC32();
        crc.update(code);
        return String.format("%08x", crc.getValue());
    }

    @Callback(doc = "function():string -- The code stored on the EEPROM.")
    public Object[] get(Context ctx, Arguments args) {
        return new Object[]{contents.code()};
    }

    @Callback(direct = false, doc = "function(code:string) -- Replaces the code (up to 4096 bytes). Fails if read-only.")
    public Object[] set(Context ctx, Arguments args) throws ComponentException {
        Contents c = contents;
        if (c.readOnly()) throw new ComponentException("storage is readonly");
        byte[] code = args.isNil(0) ? new byte[0] : args.checkBytes(0);
        if (code.length > CODE_SIZE) throw new ComponentException("not enough space");
        update(new Contents(code, c.data(), c.label(), false));
        return new Object[0];
    }

    @Callback(doc = "function():string -- The data area (by convention the boot address).")
    public Object[] getData(Context ctx, Arguments args) {
        return new Object[]{contents.data()};
    }

    @Callback(direct = false, doc = "function(data:string) -- Replaces the data area (up to 256 bytes). Works even when read-only.")
    public Object[] setData(Context ctx, Arguments args) throws ComponentException {
        Contents c = contents;
        byte[] data = args.isNil(0) ? new byte[0] : args.checkBytes(0);
        if (data.length > DATA_SIZE) throw new ComponentException("not enough space");
        update(new Contents(c.code(), data, c.label(), c.readOnly()));
        return new Object[0];
    }

    @Callback(doc = "function():string -- The label of the EEPROM.")
    public Object[] getLabel(Context ctx, Arguments args) {
        return new Object[]{contents.label()};
    }

    @Callback(direct = false, doc = "function(label:string):string -- Sets the label (up to 24 characters). Fails if read-only.")
    public Object[] setLabel(Context ctx, Arguments args) throws ComponentException {
        Contents c = contents;
        if (c.readOnly()) throw new ComponentException("storage is readonly");
        String label = args.optString(0, "EEPROM").trim();
        if (label.isEmpty()) label = "EEPROM";
        if (label.length() > 24) label = label.substring(0, 24);
        update(new Contents(c.code(), c.data(), label, false));
        return new Object[]{label};
    }

    @Callback(doc = "function():number -- The code capacity in bytes.")
    public Object[] getSize(Context ctx, Arguments args) {
        return new Object[]{CODE_SIZE};
    }

    @Callback(doc = "function():number -- The data capacity in bytes.")
    public Object[] getDataSize(Context ctx, Arguments args) {
        return new Object[]{DATA_SIZE};
    }

    @Callback(doc = "function():string -- CRC32 of the code, as 8 hex digits.")
    public Object[] getChecksum(Context ctx, Arguments args) {
        return new Object[]{checksum(contents.code())};
    }

    @Callback(doc = "function():boolean -- Whether the code and label are locked.")
    public Object[] isReadOnly(Context ctx, Arguments args) {
        return new Object[]{contents.readOnly()};
    }

    @Callback(direct = false, doc = "function(checksum:string):boolean -- Locks the code and label forever. The checksum must match getChecksum().")
    public Object[] makeReadonly(Context ctx, Arguments args) throws ComponentException {
        Contents c = contents;
        if (!checksum(c.code()).equals(args.checkString(0))) throw new ComponentException("incorrect checksum");
        if (!c.readOnly()) update(new Contents(c.code(), c.data(), c.label(), true));
        return new Object[]{true};
    }

    @Override
    public java.util.Map<String, String> deviceInfo() {
        java.util.Map<String, String> m = super.deviceInfo();
        m.put("description", "EEPROM");
        m.put("product", contents.label());
        m.put("capacity", String.valueOf(CODE_SIZE));
        m.put("size", String.valueOf(contents.code().length));
        return m;
    }
}
