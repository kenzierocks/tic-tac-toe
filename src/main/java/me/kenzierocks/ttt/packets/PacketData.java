package me.kenzierocks.ttt.packets;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

import me.kenzierocks.ttt.Util;

public final class PacketData {

    public enum Pipe {
        SERVER_TO_CLIENT, CLIENT_TO_SERVER;
    }

    public static PacketData read(Path source) throws IOException {
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
            return new PacketData(fields.build(), pipe);
        }
    }

    private final Map<String, PacketPart> fields;
    private final Pipe directionPipe;

    private PacketData(Map<String, PacketPart> fields, Pipe pipe) {
        this.fields = fields;
        this.directionPipe = pipe;
    }

    public Map<String, PacketPart> getFields() {
        return fields;
    }

    public Pipe getDirectionPipe() {
        return directionPipe;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("fields", fields)
                .add("directionPipe", this.directionPipe).toString();
    }

}
