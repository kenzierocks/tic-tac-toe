package me.kenzierocks.ttt.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public class NetworkManager {

    private final Socket socket;
    private final DataInputStream socketIn;
    private final DataOutputStream socketOut;
    private final Queue<Packet> readQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet> writeQueue = new ConcurrentLinkedQueue<>();

    public NetworkManager(String address, int port) throws IOException {
        this(new Socket(address, port));
    }

    public NetworkManager(Socket sock) throws IOException {
        this.socket = sock;
        this.socketIn = new DataInputStream(this.socket.getInputStream());
        this.socketOut = new DataOutputStream(this.socket.getOutputStream());
        Thread readThread = new Thread(getReadRunnable(), "Reading Thread");
        Thread writeThread = new Thread(getWriteRunnable(), "Writing Thread");
        Stream.of(readThread, writeThread).forEach(t -> {
            t.setDaemon(true);
            t.start();
        });
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

    public Runnable getWriteRunnable() {
        return () -> {
            try {
                while (!this.socket.isClosed()) {
                    while (writeQueue.isEmpty()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                        }
                    }
                    writePacket(writeQueue.poll());
                }
            } catch (IOException e) {
                // TODO report to user
                e.printStackTrace();
            }
        };
    };

    private void writePacket(Packet packet) throws IOException {
        int id = packet.getId();
        this.socketOut.writeInt(id);
        packet.write(this.socketOut);
    }

    private Packet readPacket() throws IOException {
        int id = this.socketIn.readInt();
        PacketReader<?> reader = PacketRegistry.getReaderById(id);
        Packet read = reader.read(this.socketIn);
        return read;
    }

    public Optional<Packet> getNextPacket() throws EOFException {
        if (this.socket.isClosed()) {
            throw new EOFException("no more packets from a closed socket");
        }
        return Optional.ofNullable(this.readQueue.poll());
    }

}
