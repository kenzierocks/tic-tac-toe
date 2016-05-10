package me.kenzierocks.ttt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum PacketPart {

    INT("I"), SHORT("S"), LONG("J"), BYTE("B"), FLOAT("F"), DOUBLE("D"),
    CHAR("C"), BOOLEAN("Z");

    private static final Map<String, PacketPart> PART_MAP;
    static {
        Map<String, PacketPart> map = new HashMap<>();
        for (PacketPart part : values()) {
            map.put(part.getBytecodeId(), part);
        }
        PART_MAP = Collections.unmodifiableMap(map);
    }

    public static PacketPart getById(String id) {
        PacketPart part = PART_MAP.get(id);
        if (part == null) {
            throw new IllegalArgumentException("No such part: " + id);
        }
        return part;
    }

    private final String bytecodeId;

    PacketPart(String id) {
        this.bytecodeId = id;
    }

    public String getBytecodeId() {
        return bytecodeId;
    }

    @Override
    public String toString() {
        return this.bytecodeId;
    }

}
