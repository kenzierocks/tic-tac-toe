package me.kenzierocks.ttt;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.common.io.CharStreams;

public final class PacketData {
    
    public static PacketData read(Path source) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source)) {
            
        }
    }
    
    private static void expect(String val, BufferedReader reader) throws IOException {
        char[] data = val.toCharArray();
        char[] readData = new char[data.length];
        int complete = 0;
        while (complete < readData.length) {
            reader.read(readData, complete, readData.length - complete);
        }
        if ()
    }

    private final Map<String, PacketPart> clientFields;
    private final Map<String, PacketPart> serverFields;

    private PacketData() {
    }

}
