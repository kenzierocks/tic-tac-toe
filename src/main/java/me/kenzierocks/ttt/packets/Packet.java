package me.kenzierocks.ttt.packets;

import java.io.DataOutputStream;
import java.io.IOException;

public interface Packet {

    void write(DataOutputStream stream) throws IOException;

}
