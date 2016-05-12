package me.kenzierocks.ttt.packets;

import java.io.DataOutputStream;
import java.io.IOException;

public interface Packet {

    interface Client extends Packet {
    }

    interface Server extends Packet {
    }

    void write(DataOutputStream stream) throws IOException;

}
