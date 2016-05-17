package me.kenzierocks.ttt;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;

import me.kenzierocks.ttt.event.EventBuses;
import me.kenzierocks.ttt.event.IdentChangeEvent;

public final class Identification {

    private static final Path ID_FILE =
            Util.getConfigDir().resolve("idfile.txt");

    private static final Identification INSTANCE = new Identification();

    public static Identification getInstance() {
        return INSTANCE;
    }

    private volatile UUID id = null;

    private Identification() {
        reloadUuid(UUID.randomUUID());
    }

    public UUID getId() {
        return id;
    }

    public void newUuid() {
        try {
            Files.delete(ID_FILE);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        reloadUuid(UUID.randomUUID());
    }

    private void reloadUuid(UUID defaultId) {
        UUID toSet = defaultId;
        try {
            if (Files.exists(ID_FILE)) {
                try (Reader reader = Files.newBufferedReader(ID_FILE)) {
                    toSet = UUID.fromString(CharStreams.toString(reader));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            id = toSet;
            EventBuses.DEFAULT.post(new IdentChangeEvent(id));
        }
        if (!Files.exists(ID_FILE)) {
            try (Writer writer = Files.newBufferedWriter(ID_FILE)) {
                writer.write(getId().toString());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
