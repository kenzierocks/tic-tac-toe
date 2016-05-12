package me.kenzierocks.ttt;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

import javafx.scene.control.Labeled;

public class Util {

    private static final Map<String, String> COLORS = new HashMap<>();
    static {
        COLORS.put("X", "red");
        COLORS.put("O", "green");
        COLORS.put("[  ]", "blue");
    }

    public static void attachColorChange(Labeled label) {
        label.textProperty().addListener((ob, oldV, newV) -> {
            String color = COLORS.get(newV);
            String style = "-fx-text-inner-color: " + color
                    + "; -fx-text-fill: " + color;
            label.setStyle(style);
        });
    }

    public static Map<String, String> readProperties(BufferedReader reader) {
        ImmutableMap.Builder<String, String> props = ImmutableMap.builder();
        reader.lines().forEach(s -> {
            s = s.trim();
            if (s.isEmpty()) {
                // don't care
                return;
            }
            if (s.startsWith("#")) {
                // comment
                return;
            } else if (s.contains("=")) {
                Iterator<String> parts =
                        Splitter.on('=').limit(2).split(s).iterator();
                // First part: key
                String key = parts.next();
                // Second part: value
                String value = parts.next();
                props.put(key, value);
            } else {
                throw new IllegalStateException("Illegal line '" + s + "'");
            }
        });
        return props.build();
    }

    public static String uppercaseFirstLetter(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

}
