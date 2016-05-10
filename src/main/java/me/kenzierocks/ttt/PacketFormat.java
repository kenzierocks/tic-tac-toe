package me.kenzierocks.ttt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PacketFormat {

    private final List<PacketPart> parts;

    public static PacketFormat fromString(String format) {
        return new PacketFormat(
                format.codePoints()
                        .mapToObj(c -> PacketPart
                                .getById(String.valueOf((char) c)))
                        .collect(Collectors.toList()));
    }

    public static PacketFormat fromList(List<PacketPart> parts) {
        return new PacketFormat(
                Collections.unmodifiableList(new ArrayList<>(parts)));
    }

    private PacketFormat(List<PacketPart> parts) {
        this.parts = parts;
    }

    @Override
    public String toString() {
        return parts.stream().map(String::valueOf)
                .collect(Collectors.joining());
    }

}
