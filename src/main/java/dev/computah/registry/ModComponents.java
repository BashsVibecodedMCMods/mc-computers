package dev.computah.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.computah.Computah;
import dev.computah.component.EepromComponent;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Item data components. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Computah.MOD_ID);

    /** The component address of a part (assigned when it is first installed). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ADDRESS = REGISTER.registerComponentType("address",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The label of a drive. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LABEL = REGISTER.registerComponentType("label",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public record EepromData(String code, String data, String label, boolean readOnly) {
        // Code and data are byte strings; they are stored base64-encoded so arbitrary bytes survive.
        public static final Codec<EepromData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("code").forGetter(EepromData::code),
                Codec.STRING.optionalFieldOf("data", "").forGetter(EepromData::data),
                Codec.STRING.optionalFieldOf("label", "EEPROM").forGetter(EepromData::label),
                Codec.BOOL.optionalFieldOf("readonly", false).forGetter(EepromData::readOnly)
        ).apply(i, EepromData::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, EepromData> STREAM = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, EepromData::code, ByteBufCodecs.STRING_UTF8, EepromData::data,
                ByteBufCodecs.STRING_UTF8, EepromData::label, ByteBufCodecs.BOOL, EepromData::readOnly, EepromData::new);

        public static EepromData of(EepromComponent.Contents c) {
            Base64.Encoder e = Base64.getEncoder();
            return new EepromData(e.encodeToString(c.code()), e.encodeToString(c.data()), c.label(), c.readOnly());
        }

        public static EepromData ofCode(String code, String label) {
            return of(new EepromComponent.Contents(code.getBytes(StandardCharsets.UTF_8), new byte[0], label, false));
        }

        public EepromComponent.Contents contents() {
            Base64.Decoder d = Base64.getDecoder();
            try {
                return new EepromComponent.Contents(d.decode(code), d.decode(data), label, readOnly);
            } catch (IllegalArgumentException e) {
                return EepromComponent.Contents.EMPTY;
            }
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EepromData>> EEPROM = REGISTER.registerComponentType("eeprom",
            b -> b.persistent(EepromData.CODEC).networkSynchronized(EepromData.STREAM));

    /** The host a linker has selected. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> SELECTED_HOST = REGISTER.registerComponentType("selected_host",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private ModComponents() {
    }
}
