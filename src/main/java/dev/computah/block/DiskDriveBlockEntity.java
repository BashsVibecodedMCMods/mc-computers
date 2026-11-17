package dev.computah.block;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.Context;
import dev.computah.component.FilesystemComponent;
import dev.computah.fs.DirectoryFileSystem;
import dev.computah.fs.OsFiles;
import dev.computah.host.HostCore;
import dev.computah.item.PartItem;
import dev.computah.item.PartKind;
import dev.computah.menu.DiskDriveMenu;
import dev.computah.network.NetworkManager;
import dev.computah.registry.ModBlockEntities;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** A disk drive: holds one floppy and offers it (plus a {@code disk_drive} component) to its network. */
public class DiskDriveBlockEntity extends MemberBlockEntity implements SimpleEntityBlock.Hooks, MenuProvider {
    private final SimpleContainer slot = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof PartItem p && p.kind().slot == PartKind.SlotType.FLOPPY;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            mediaChanged = true;
            DiskDriveBlockEntity.this.setChanged();
        }
    };
    private String address = UUID.randomUUID().toString();
    private @Nullable DriveComponent drive;
    private @Nullable FilesystemComponent media;
    private @Nullable String mediaAddress;
    private boolean mediaChanged = true;
    private final AtomicBoolean activity = new AtomicBoolean();
    private final AtomicBoolean ejectRequested = new AtomicBoolean();
    private long ticks, lastSound;

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISK_DRIVE.get(), pos, state);
    }

    public Container container() {
        return slot;
    }

    /** The {@code disk_drive} component. */
    public class DriveComponent extends Component {
        @Override
        public String address() {
            return address;
        }

        @Override
        public String type() {
            return "disk_drive";
        }

        @Callback(doc = "function():boolean -- Whether the drive is empty.")
        public Object[] isEmpty(Context ctx, Arguments args) {
            return new Object[]{media == null};
        }

        @Callback(doc = "function():string -- The address of the floppy's filesystem, or nil.")
        public Object[] media(Context ctx, Arguments args) {
            FilesystemComponent m = media;
            return new Object[]{m == null ? null : m.address()};
        }

        @Callback(doc = "function():boolean -- Ejects the floppy. Returns whether there was one.")
        public Object[] eject(Context ctx, Arguments args) {
            boolean had = media != null;
            ejectRequested.set(true);
            return new Object[]{had};
        }
    }

    private DriveComponent drive() {
        if (drive == null) drive = new DriveComponent();
        return drive;
    }

    @Override
    public List<Component> networkComponents() {
        List<Component> out = new ArrayList<>();
        out.add(drive());
        if (media != null) out.add(media);
        return out;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        ticks++;
        if (ejectRequested.getAndSet(false)) eject();
        if (mediaChanged) {
            mediaChanged = false;
            updateMedia(server);
        }
        if (activity.getAndSet(false) && ticks - lastSound > 12) {
            lastSound = ticks;
            level.playSound(null, worldPosition, ModSounds.FLOPPY_ACCESS.get(), SoundSource.BLOCKS, 0.3f, 1.0f);
        }
    }

    private void updateMedia(ServerLevel server) {
        ItemStack stack = slot.getItem(0);
        String addr = stack.isEmpty() ? null : stack.get(ModComponents.ADDRESS.get());
        if (!stack.isEmpty() && addr == null) {
            addr = UUID.randomUUID().toString();
            stack.set(ModComponents.ADDRESS.get(), addr);
            setChanged();
        }
        if (java.util.Objects.equals(addr, mediaAddress)) return;
        if (media != null) media.close();
        media = null;
        mediaAddress = addr;
        if (addr != null && stack.getItem() instanceof PartItem p) {
            final String a = addr;
            FilesystemComponent.Label label = p.kind() == PartKind.OS_FLOPPY ? FilesystemComponent.fixedLabel("Computah OS") : new FilesystemComponent.Label() {
                @Override
                public String get() {
                    ItemStack s = slot.getItem(0);
                    return a.equals(s.get(ModComponents.ADDRESS.get())) ? s.get(ModComponents.LABEL.get()) : null;
                }

                @Override
                public boolean set(String l) {
                    ItemStack s = slot.getItem(0);
                    if (!a.equals(s.get(ModComponents.ADDRESS.get()))) return false;
                    if (l == null) s.remove(ModComponents.LABEL.get());
                    else s.set(ModComponents.LABEL.get(), l);
                    setChanged();
                    return true;
                }
            };
            media = new FilesystemComponent(p.kind() == PartKind.OS_FLOPPY ? OsFiles.fileSystem()
                    : new DirectoryFileSystem(HostCore.fsRoot(server).resolve(addr), PartItem.FLOPPY_SIZE), addr, label);
            media.setActivityListener(w -> activity.set(true));
        }
        if (ticks > 1) {
            level.playSound(null, worldPosition, addr != null ? ModSounds.FLOPPY_INSERT.get() : ModSounds.FLOPPY_EJECT.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        NetworkManager.bump(level);
    }

    private void eject() {
        ItemStack stack = slot.removeItemNoUpdate(0);
        slot.setChanged();
        if (stack.isEmpty() || level == null) return;
        var dir = getBlockState().getValue(SimpleEntityBlock.FACING);
        ItemEntity e = new ItemEntity(level, worldPosition.getX() + 0.5 + dir.getStepX() * 0.7, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5 + dir.getStepZ() * 0.7, stack);
        e.setDeltaMovement(dir.getStepX() * 0.1, 0.05, dir.getStepZ() * 0.1);
        level.addFreshEntity(e);
    }

    @Override
    public @Nullable Container dropContainer() {
        return slot;
    }

    @Override
    public void removed() {
        if (media != null) media.close();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("address", address);
        NonNullList<ItemStack> items = NonNullList.withSize(1, slot.getItem(0));
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("address")) address = tag.getString("address");
        NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        slot.setItem(0, items.get(0));
        mediaAddress = null;
        mediaChanged = true;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.computah.disk_drive");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DiskDriveMenu(id, inv, slot, worldPosition);
    }
}
