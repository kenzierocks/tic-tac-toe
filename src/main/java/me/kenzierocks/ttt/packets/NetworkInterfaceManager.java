package me.kenzierocks.ttt.packets;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import me.kenzierocks.ttt.Util;

public final class NetworkInterfaceManager {

    private static final Path ID_FILE =
            Util.getConfigDir().resolve("nim_selected.txt");
    private static final NetworkInterfaceManager INSTANCE =
            new NetworkInterfaceManager();

    public static NetworkInterfaceManager getInstance() {
        return INSTANCE;
    }

    private final ObjectProperty<NetworkInterface> selected =
            new SimpleObjectProperty<>();

    private NetworkInterfaceManager() {
        selected.addListener((obs, o, n) -> {
            try (Writer writer = Files.newBufferedWriter(ID_FILE)) {
                writer.write(n.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        loadSelectedFromFile();
    }

    private void loadSelectedFromFile() {
        String toSet = null;
        try {
            if (Files.exists(ID_FILE)) {
                try (Reader reader = Files.newBufferedReader(ID_FILE)) {
                    toSet = CharStreams.toString(reader);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (toSet == null) {
            return;
        }
        for (NetworkInterface iface : getInterfaces()) {
            if (iface.getName().equals(toSet)) {
                select(iface);
                break;
            }
        }
    }

    public List<NetworkInterface> getInterfaces() {
        ImmutableList.Builder<NetworkInterface> ifaces =
                ImmutableList.builder();
        try {
            for (Enumeration<NetworkInterface> stuff =
                    NetworkInterface.getNetworkInterfaces(); stuff
                            .hasMoreElements();) {
                // return only multicast ifaces...
                NetworkInterface next = stuff.nextElement();
                if (next.supportsMulticast()) {
                    ifaces.add(next);
                }
            }
        } catch (SocketException wtf) {
            throw Throwables.propagate(wtf);
        }
        return ifaces.build();
    }

    public ObjectProperty<NetworkInterface> selectedProperty() {
        return this.selected;
    }

    public void select(NetworkInterface iface) {
        checkArgument(iface != null, "cannot select no interface");
        this.selected.set(iface);
    }

    public NetworkInterface getSelected() {
        NetworkInterface val = this.selected.get();
        checkState(val != null, "selection of interface needed");
        return val;
    }

}
