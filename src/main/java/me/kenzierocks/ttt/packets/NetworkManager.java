package me.kenzierocks.ttt.packets;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkManager {

    private static final int LENGTH_OF_LENGTH_PACKET = Integer.BYTES;
    private static final ThreadLocal<DatagramPacket> LENGTH_PACKET =
            ThreadLocal.withInitial(() -> {
                return new DatagramPacket(new byte[LENGTH_OF_LENGTH_PACKET],
                        LENGTH_OF_LENGTH_PACKET);
            });

    private final MulticastSocket socket;
    private final Queue<Packet> readQueue = new ConcurrentLinkedQueue<>();

    public NetworkManager(InetAddress mcAddress, int mcPort)
            throws IOException {
        this.socket = new MulticastSocket(mcPort);
        this.socket.joinGroup(mcAddress);
    }

    public Runnable getReadRunnable() {
        return () -> {
            try {
                while (!this.socket.isClosed()) {
                    readQueue.add(readPacket());
                }
            } catch (IOException e) {
                // TODO report to user
                e.printStackTrace();
            }
        };
    };

    private Packet readPacket() throws IOException {
        // Datagram packets are weird...
        // We'll read a length, then read the packet based on the length
        DatagramPacket lengthPacket = LENGTH_PACKET.get();
        this.socket.receive(lengthPacket);
        ByteBuffer data = ByteBuffer.wrap(lengthPacket.getData());
        int length = data.getInt(0);
        // TODO length checks?
        DatagramPacket realPacket =
                new DatagramPacket(new byte[length], length);
        // First 4 bytes are the ID
        data = ByteBuffer.wrap(realPacket.getData());
        int id = data.getInt(0);
        PacketReader<?> reader = PacketRegistry.getReaderById(id);
        Packet read = reader.read(new DataInputStream(
                new ByteArrayInputStream(realPacket.getData(), 4, length - 4)));
        return read;
    }

    public Optional<Packet> getNextPacket() {
        return Optional.of(this.readQueue.poll());
    }

}
