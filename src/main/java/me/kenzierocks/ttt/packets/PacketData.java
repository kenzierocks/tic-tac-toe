package me.kenzierocks.ttt.packets;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import me.kenzierocks.ttt.Util;

@AutoValue
public abstract class PacketData {

    public enum Pipe {
        SERVER_TO_CLIENT, CLIENT_TO_SERVER;
    }

    public static PacketData read(Path source, int id) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source)) {
            Map<String, String> props =
                    new HashMap<>(Util.readProperties(reader));
            String pipeText =
                    Optional.ofNullable(props.remove("pipe")).orElse("");
            Pipe pipe;
            try {
                pipe = Pipe.valueOf(
                        pipeText.toUpperCase(Locale.ENGLISH).replace('-', '_'));
            } catch (IllegalArgumentException noSuchEnum) {
                throw new IllegalArgumentException(
                        "The pipe '" + pipeText + "' does not exist.",
                        noSuchEnum);
            }
            ImmutableMap.Builder<String, PacketPart> fields =
                    ImmutableMap.builder();
            props.forEach((k, v) -> {
                PacketPart part;
                try {
                    part = PacketPart.getById(v);
                } catch (IllegalArgumentException e) {
                    try {
                        part = PacketPart.valueOf(v.toUpperCase());
                    } catch (IllegalArgumentException e2) {
                        e2.addSuppressed(e);
                        throw new IllegalArgumentException(
                                "Invalid PacketPart '" + v + "'", e2);
                    }
                }
                fields.put(k, part);
            });
            return new AutoValue_PacketData(source,
                    Util.noExtension(source.getFileName().toString()),
                    fields.build(), pipe, id);
        }
    }

    PacketData() {
    }

    public abstract Path getSource();

    public abstract String getPacketBaseName();

    public final String getPacketClassName() {
        return getPacketBaseName().concat("Packet");
    }

    public final String getPacketReaderClassName() {
        return getPacketBaseName().concat("PacketReader");
    }

    public final String getPackageSuffix() {
        return getDirectionPipe() == Pipe.CLIENT_TO_SERVER ? "c2s" : "s2c";
    }

    public abstract Map<String, PacketPart> getFields();

    public abstract Pipe getDirectionPipe();

    public abstract int getId();

}
