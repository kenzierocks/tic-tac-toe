package me.kenzierocks.ttt.packets;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

import javafx.application.Platform;
import me.kenzierocks.ttt.Identification;
import me.kenzierocks.ttt.Main;
import me.kenzierocks.ttt.Util;
import me.kenzierocks.ttt.event.EventBuses;
import me.kenzierocks.ttt.event.IdentChangeEvent;

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
    static {
        try {
            MC_ADDR_4 = InetAddress.getByName("239.38.6.57");
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
    private static final byte[] DATA_BUFFER_BASE = new byte[512];
    private static final LanClientFinder INSTANCE = new LanClientFinder();

    public static LanClientFinder getInstance() {
        return INSTANCE;
    }

    @FunctionalInterface
    private interface IOConsumer<T> {

        void accept(T t) throws IOException;

    }

    private static byte[] captureData(byte id,
            IOConsumer<DataOutputStream> writer) {
        try {
            ByteArrayOutputStream capture = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(capture);

            writer.accept(stream);

            byte[] data = capture.toByteArray();
            int dataLength = data.length;
            // Write packet format...ID/LENGTH/DATA
            byte[] ret = new byte[dataLength + 5];
            ret[0] = id;
            ByteBuffer.wrap(ret, 1, 4).putInt(dataLength);
            System.arraycopy(data, 0, ret, 5, dataLength);
            return ret;
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
    private final AtomicReference<DatagramPacket> notifyPresensePacket =
            new AtomicReference<>();
    private final MulticastSocket socket;
    private final InetAddress localMcAddr;
    private InetAddress localAddress;

    private LanClientFinder() {
        try {
            this.socket = new MulticastSocket(MC_PORT);
            this.socket.setLoopbackMode(false);
            InetAddress choose;
            this.socket.joinGroup(
                    new InetSocketAddress(choose = MC_ADDR_4, MC_PORT),
                    NetworkInterfaceManager.getInstance().getSelected());
            this.localMcAddr = choose;
            bindInvalidations();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        Thread t = new Thread(this::updateClientsLoop, "read-updates");
        t.setDaemon(true);
        t.start();
        this.networkExec.scheduleAtFixedRate(this::sendUpdate, 0, 1,
                TimeUnit.SECONDS);
    }

    private void bindInvalidations() {
        Util.addListenerFireInitial(
                NetworkInterfaceManager.getInstance().selectedProperty(),
                (obs, o, n) -> {
                    invalidate(Identification.getInstance().getId());
                });
        EventBuses.DEFAULT.register(this);
    }

    @Subscribe
    public void onIdentChange(IdentChangeEvent event) {
        invalidate(event.newId);
    }

    private void invalidate(UUID id) {
        // Must invalidate all connections
        synchronized (clients) {
            this.clients.clear();
        }
        this.localAddress = NetworkInterfaceManager.getInstance().getSelected()
                .getInterfaceAddresses().stream()
                .map(InterfaceAddress::getAddress)
                .filter(ia -> ia instanceof Inet4Address).findFirst().get();
        // Notify data:
        /*
         * String id = ID; String address = / get localhost address /;
         */
        byte[] notifyData = captureData(NOTIFY_PRESENSE, stream -> {
            stream.writeUTF(id.toString());
            stream.writeUTF(localAddress.getHostAddress());
        });

        this.notifyPresensePacket.set(new DatagramPacket(notifyData,
                notifyData.length, this.localMcAddr, MC_PORT));
    }

    private void sendUpdate() {
        try {
            DatagramPacket datagramPacket = this.notifyPresensePacket.get();
            this.socket.send(datagramPacket);
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
            int length = ByteBuffer.wrap(data, 1, 4).getInt();
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
        String uuid = Identification.getInstance().getId().toString();
        switch (id) {
            case NOTIFY_PRESENSE:
                String client = data.readUTF();
                if (client.equals(uuid.toString())) {
                    // don't try to connect ourselves!
                    return;
                }
                String address = data.readUTF();
                synchronized (clients) {
                    if (this.clients.put(Maps.immutableEntry(client, address),
                            System.currentTimeMillis()) == null) {
                        System.err.println(
                                "Added client " + client + " at " + address);
                    }
                }
                break;
            case REQUEST_CONNECTION:
                String targetClient = data.readUTF();
                if (targetClient.equals(uuid.toString())) {
                    String addr = data.readUTF();
                    String clientFrom = this.clients.keySet().stream()
                            .filter(e -> e.getValue().equals(addr)).findFirst()
                            .get().getKey();
                    int port = data.readInt();
                    Platform.runLater(() -> {
                        Main.CONTROLLER.promptForConnection(clientFrom, addr,
                                port);
                    });
                }
                break;
            default:
                throw new IllegalStateException("no such packet " + id);
        }
    }

    private void removeOutdatedClients() {
        synchronized (clients) {
            Stream.Builder<Map.Entry<String, String>> removeClients =
                    Stream.builder();
            this.clients.forEach((k, v) -> {
                if (v + CLIENT_LIFETIME < System.currentTimeMillis()) {
                    removeClients.add(k);
                }
            });
            removeClients.build().forEach(this.clients::remove);
        }
    }

    public Map<Map.Entry<String, String>, Long> getClients() {
        return this.clientsView;
    }

    public CompletableFuture<Socket> negotiateConnection(String client) {
        synchronized (clients) {
            checkArgument(
                    this.clients.keySet().stream()
                            .anyMatch(e -> e.getKey().equals(client)),
                    "%s is not a known client from %s", client, this.clients);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (ServerSocket server = new ServerSocket()) {
                server.bind(new InetSocketAddress(this.localAddress, 0));

                byte[] data = captureData(REQUEST_CONNECTION, stream -> {
                    stream.writeUTF(client);
                    stream.writeUTF(server.getInetAddress().getHostAddress());
                    stream.writeInt(server.getLocalPort());
                });

                this.socket.send(new DatagramPacket(data, data.length,
                        this.localMcAddr, MC_PORT));

                return server.accept();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }, this.networkExec);
    }

}
