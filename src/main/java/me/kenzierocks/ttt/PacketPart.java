package me.kenzierocks.ttt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum PacketPart {

    INT("I", int.class), SHORT("S", short.class), LONG("J", long.class),
    BYTE("B", byte.class), FLOAT("F", float.class), DOUBLE("D", double.class),
    CHAR("C", char.class), BOOLEAN("Z", boolean.class),
    STRING("Ljava/lang/String;", String.class);

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

    private final Class<?> javaClass;
    private final String bytecodeId;

    PacketPart(String id, Class<?> javaClass) {
        this.bytecodeId = id;
        this.javaClass = javaClass;
    }

    public String getBytecodeId() {
        return bytecodeId;
    }

    public Class<?> getJavaType() {
        return javaClass;
    }

    @Override
    public String toString() {
        return this.bytecodeId;
    }

}
