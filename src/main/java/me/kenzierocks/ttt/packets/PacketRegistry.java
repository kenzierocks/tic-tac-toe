// Generated from scratch
// 	 on Fri, 13 May 2016 06:06:16 GMT
package me.kenzierocks.ttt.packets;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import me.kenzierocks.ttt.packets.c2s.ClientHandshakePacketReader;
import me.kenzierocks.ttt.packets.s2c.ServerHandshakePacketReader;

public final class PacketRegistry {
    private static final Map<Integer, PacketReader<?>> packetReaderIdMap;

    static {
        ImmutableMap.Builder<Integer, PacketReader<?>> immutableBuilder = ImmutableMap.builder();
        immutableBuilder.put(0, new ClientHandshakePacketReader());
        immutableBuilder.put(1, new ServerHandshakePacketReader());
        packetReaderIdMap = immutableBuilder.build();
    }

    public static PacketReader<?> getReaderById(int id) {
        return packetReaderIdMap.get(id);
    }
}
