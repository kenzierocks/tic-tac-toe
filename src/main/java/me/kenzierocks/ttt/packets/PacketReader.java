package me.kenzierocks.ttt.packets;

import java.io.DataInputStream;
import java.io.IOException;

public interface PacketReader<R extends Packet> {
    
    R read(DataInputStream stream) throws IOException;

}
