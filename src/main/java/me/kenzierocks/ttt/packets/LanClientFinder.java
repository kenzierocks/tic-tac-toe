package me.kenzierocks.ttt.packets;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;

import me.kenzierocks.ttt.Util;

/**
 * Helper for finding clients on the local network. Uses multicast to negotiate
 * sockets.
 */
public class LanClientFinder {

    private static final long CLIENT_LIFETIME = TimeUnit.SECONDS.toMillis(5);
    private static final byte NOTIFY_PRESENSE = 0;
    private static final byte REQUEST_CONNECTION = 1;
    private static final int MC_PORT = 1337;
    private static final InetAddress MC_ADDR_4;
    private static final InetAddress MC_ADDR_6;
    static {
        try {
            MC_ADDR_4 = InetAddress.getByName("239.38.6.57");
            MC_ADDR_6 = InetAddress.getByName("FF02:0:0:0:38:6:57:2");
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
    private static final Path ID_FILE =
            Paths.get(System.getProperty("user.home"), ".config", "tic-tac-toe",
                    "idfile.txt");
    private static final UUID ID;
    static {
        UUID toSet = UUID.randomUUID();
        try {
            if (Files.exists(ID_FILE)) {
                try (Reader reader = Files.newBufferedReader(ID_FILE)) {
                    toSet = UUID.fromString(CharStreams.toString(reader));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ID = toSet;
        }
        if (Files.exists(ID_FILE)) {
            try (Writer writer = Files.newBufferedWriter(ID_FILE)) {
                writer.write(ID.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private static final byte[] DATA_BUFFER_BASE = new byte[512];
    private static final InetAddress LOCAL_ADDRESS;
    static {
        try {
            LOCAL_ADDRESS = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
    private static final LanClientFinder INSTANCE = new LanClientFinder();

    public static LanClientFinder getInstance() {
        return INSTANCE;
    }

    @FunctionalInterface
    private interface IOConsumer<T> {

        void accept(T t) throws IOException;

    }

    private static byte[] captureData(IOConsumer<DataOutputStream> writer,
            int length) {
        try {
            ByteArrayOutputStream capture = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(capture);

            writer.accept(stream);

            byte[] data = capture.toByteArray();
            checkState(data.length <= length, "written data exceeded length");
            /* expand to fill length */
            data = Arrays.copyOf(data, length);
            return data;
        } catch (IOException e) {
            throw new AssertionError("shouldn't happen normally", e);
        }
    }

    private final ScheduledExecutorService networkExec =
            Executors.newScheduledThreadPool(1,
                    Util.newDaemonThreadFactory("network-exec"));
    private final Map<Map.Entry<String, String>, Long> clients =
            new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, String>, Long> clientsView =
            Collections.unmodifiableMap(this.clients);
    private final DatagramPacket recievePacket = new DatagramPacket(
            DATA_BUFFER_BASE.clone(), DATA_BUFFER_BASE.length);
    private final DatagramPacket notifyPresensePacket;
    private final MulticastSocket socket;
    private final InetAddress localMcAddr;

    private LanClientFinder() {
        try {
            this.socket = new MulticastSocket(MC_PORT);
            InetAddress choose;
            try {
                this.socket.joinGroup(choose = MC_ADDR_4);
            } catch (SocketException e) {
                try {
                    this.socket.joinGroup(choose = MC_ADDR_6);
                } catch (IOException | RuntimeException ex) {
                    ex.addSuppressed(e);
                    throw ex;
                }
            }
            this.localMcAddr = choose;
            // Notify data:
            /*
             * String id = ID; String address = / get localhost address /;
             */
            byte[] notifyData = captureData(stream -> {
                stream.writeUTF(ID.toString());
                stream.writeUTF(LOCAL_ADDRESS.getHostAddress());
            }, DATA_BUFFER_BASE.length);

            this.notifyPresensePacket = new DatagramPacket(notifyData,
                    notifyData.length, this.localMcAddr, MC_PORT);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        Thread t = new Thread(this::updateClientsLoop, "read-updates");
        t.setDaemon(true);
        t.start();
        this.networkExec.scheduleAtFixedRate(this::sendUpdate, 0, 10,
                TimeUnit.MILLISECONDS);
    }

    private void sendUpdate() {
        try {
            this.socket.send(this.notifyPresensePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateClientsLoop() {
        while (true) {
            removeOutdatedClients();
            updateClients();
        }
    }

    private void updateClients() {
        try {
            this.socket.receive(this.recievePacket);
            byte[] data = Arrays.copyOf(this.recievePacket.getData(),
                    this.recievePacket.getLength());
            checkState(data.length >= 5,
                    "must have at least ID + length per packet");
            byte idByte = data[0];
            int length = ByteBuffer.wrap(data, 1, 4).getInt(0);
            checkState(data.length - 5 == length,
                    "expected length %s != to real length %s", length,
                    data.length - 5);
            byte[] packetData = Arrays.copyOfRange(data, 5, data.length);
            handlePacket(idByte,
                    new DataInputStream(new ByteArrayInputStream(packetData)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePacket(byte id, DataInputStream data)
            throws IOException {
        switch (id) {
            case NOTIFY_PRESENSE:
                String client = data.readUTF();
                String address = data.readUTF();
                this.clients.put(Maps.immutableEntry(client, address),
                        System.currentTimeMillis());
                break;
            case REQUEST_CONNECTION:
                String targetClient = data.readUTF();
                if (targetClient.equals(ID.toString())) {
                    String addr = data.readUTF();
                    int port = data.readInt();
                    System.err.println(
                            "CONNECTION REQUEST ON " + addr + ":" + port);
                }
                break;
            default:
                throw new IllegalStateException("no such packet " + id);
        }
    }

    private void removeOutdatedClients() {
        Stream.Builder<Map.Entry<String, String>> removeClients =
                Stream.builder();
        this.clients.forEach((k, v) -> {
            if (v + CLIENT_LIFETIME < System.currentTimeMillis()) {
                removeClients.add(k);
            }
        });
        removeClients.build().forEach(this.clients::remove);
    }

    public Map<Map.Entry<String, String>, Long> getClients() {
        return this.clientsView;
    }

    public CompletableFuture<Socket> negotiateConnection(String client) {
        checkArgument(
                this.clients.keySet().stream()
                        .anyMatch(e -> e.getKey().equals(client)),
                "%s is not a known client", client);
        return CompletableFuture.supplyAsync(() -> {
            try (ServerSocket server = new ServerSocket()) {
                server.bind(new InetSocketAddress(LOCAL_ADDRESS, 0));

                byte[] data = captureData(stream -> {
                    stream.writeUTF(client);
                    stream.writeUTF(server.getInetAddress().getHostAddress());
                    stream.writeInt(server.getLocalPort());
                }, DATA_BUFFER_BASE.length);

                this.socket.send(new DatagramPacket(data, data.length,
                        this.localMcAddr, MC_PORT));

                return server.accept();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }, this.networkExec);
    }

}
