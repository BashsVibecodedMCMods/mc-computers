package dev.computah.item;

/** The kinds of parts that go into computer cases and servers, and which slot type each needs. */
public enum PartKind {
    CPU(SlotType.CPU), RAM(SlotType.RAM), HDD(SlotType.HDD), FLOPPY(SlotType.FLOPPY), OS_FLOPPY(SlotType.FLOPPY),
    EEPROM(SlotType.EEPROM), GPU(SlotType.CARD), NETWORK_CARD(SlotType.CARD), WIRELESS_CARD(SlotType.CARD),
    REDSTONE_CARD(SlotType.CARD);

    public final SlotType slot;

    PartKind(SlotType slot) {
        this.slot = slot;
    }

    public enum SlotType {
        CARD("card"), CPU("cpu"), RAM("memory"), HDD("hdd"), FLOPPY("floppy"), EEPROM("eeprom");

        public final String icon;

        SlotType(String icon) {
            this.icon = icon;
        }
    }

    /** Slot layouts of the case tiers and the rack server. */
    public static final SlotType[] CASE_T1 = {SlotType.CARD, SlotType.CARD, SlotType.CPU, SlotType.RAM, SlotType.HDD, SlotType.EEPROM};
    public static final SlotType[] CASE_T2 = {SlotType.CARD, SlotType.CARD, SlotType.CPU, SlotType.RAM, SlotType.RAM, SlotType.HDD, SlotType.HDD, SlotType.EEPROM};
    public static final SlotType[] CASE_T3 = {SlotType.CARD, SlotType.CARD, SlotType.CARD, SlotType.CPU, SlotType.RAM, SlotType.RAM, SlotType.HDD, SlotType.HDD, SlotType.FLOPPY, SlotType.EEPROM};
    public static final SlotType[] SERVER = {SlotType.CARD, SlotType.CARD, SlotType.CARD, SlotType.CPU, SlotType.RAM, SlotType.RAM, SlotType.RAM, SlotType.HDD, SlotType.HDD, SlotType.HDD, SlotType.EEPROM};

    public static SlotType[] caseLayout(int tier) {
        return switch (tier) {
            case 1 -> CASE_T1;
            case 2 -> CASE_T2;
            default -> CASE_T3;
        };
    }
}
