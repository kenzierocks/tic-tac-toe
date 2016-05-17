package me.kenzierocks.ttt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Dialog;
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

    public static void saveProperties(Map<String, String> properties,
            Writer writer, @Nullable String comments) throws IOException {
        saveProperties(
                properties, (writer instanceof BufferedWriter)
                        ? (BufferedWriter) writer : new BufferedWriter(writer),
                comments, false);
    }

    public static void saveProperties(Map<String, String> properties,
            BufferedWriter bw, @Nullable String comments, boolean escUnicode)
            throws IOException {
        if (comments != null) {
            writeComments(bw, comments);
        }
        synchronized (properties) {
            for (Map.Entry<String, String> e : properties.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                key = saveConvert(key, true, escUnicode);
                /*
                 * No need to escape embedded and trailing spaces for value,
                 * hence pass false to flag.
                 */
                val = saveConvert(val, false, escUnicode);
                bw.write(key + "=" + val);
                bw.newLine();
            }
        }
        bw.flush();
    }

    /*
     * Converts unicodes to encoded &#92;uxxxx and escapes special characters
     * with a preceding slash
     */
    private static String saveConvert(String theString, boolean escapeSpace,
            boolean escapeUnicode) {
        int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        StringBuffer outBuffer = new StringBuffer(bufLen);

        for (int x = 0; x < len; x++) {
            char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\');
                    outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch (aChar) {
                case ' ':
                    if (x == 0 || escapeSpace)
                        outBuffer.append('\\');
                    outBuffer.append(' ');
                    break;
                case '\t':
                    outBuffer.append('\\');
                    outBuffer.append('t');
                    break;
                case '\n':
                    outBuffer.append('\\');
                    outBuffer.append('n');
                    break;
                case '\r':
                    outBuffer.append('\\');
                    outBuffer.append('r');
                    break;
                case '\f':
                    outBuffer.append('\\');
                    outBuffer.append('f');
                    break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\');
                    outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e))
                            & escapeUnicode) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >> 8) & 0xF));
                        outBuffer.append(toHex((aChar >> 4) & 0xF));
                        outBuffer.append(toHex(aChar & 0xF));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    /** A table of hex digits */
    private static final char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static void writeComments(BufferedWriter bw, String comments)
            throws IOException {
        bw.write("#");
        int len = comments.length();
        int current = 0;
        int last = 0;
        char[] uu = new char[6];
        uu[0] = '\\';
        uu[1] = 'u';
        while (current < len) {
            char c = comments.charAt(current);
            if (c > '\u00ff' || c == '\n' || c == '\r') {
                if (last != current)
                    bw.write(comments.substring(last, current));
                if (c > '\u00ff') {
                    uu[2] = toHex((c >> 12) & 0xf);
                    uu[3] = toHex((c >> 8) & 0xf);
                    uu[4] = toHex((c >> 4) & 0xf);
                    uu[5] = toHex(c & 0xf);
                    bw.write(new String(uu));
                } else {
                    bw.newLine();
                    if (c == '\r' && current != len - 1
                            && comments.charAt(current + 1) == '\n') {
                        current++;
                    }
                    if (current == len - 1
                            || (comments.charAt(current + 1) != '#'
                                    && comments.charAt(current + 1) != '!'))
                        bw.write("#");
                }
                last = current + 1;
            }
            current++;
        }
        if (last != current)
            bw.write(comments.substring(last, current));
        bw.newLine();
    }

    public static String noExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public static ThreadFactory newDaemonThreadFactory(String baseId) {
        AtomicInteger threadId = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, baseId + "-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    private static final Path CONFIG_DIR = Paths
            .get(System.getProperty("user.home"), ".config", "tic-tac-toe");

    public static Path getConfigDir() {
        if (!Files.exists(CONFIG_DIR)) {
            try {
                Files.createDirectories(CONFIG_DIR);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return CONFIG_DIR;
    }

    public static <T> void addListenerFireInitial(ObservableValue<T> value,
            ChangeListener<T> listener) {
        listener.changed(value, null, value.getValue());
        value.addListener(listener);
    }

    public static <T> void runLaterLoop(Predicate<T> condition,
            Callable<T> runLaterCode) {
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                try {
                    if (condition.test(runLaterCode.call())) {
                        Platform.runLater(this);
                    }
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }

        });
    }

    public static <T extends Dialog<E>, E> T configureDialog(T dialog) {
        dialog.initOwner(Main.PRIMARY_STAGE);
        dialog.setOnShown(e -> {
            Main.PRIMARY_STAGE.setAlwaysOnTop(true);
            Platform.runLater(() -> Main.PRIMARY_STAGE.setAlwaysOnTop(false));
        });
        return dialog;
    }

    public static <T> Dialog<T> newStandardDialog() {
        return configureDialog(new Dialog<>());
    }

    public static Dialog<Boolean> newOkCancelDialog() {
        Dialog<Boolean> dialog = newStandardDialog();
        dialog.setResultConverter(button -> {
            if (button == ButtonType.CANCEL) {
                return false;
            }
            if (button == ButtonType.OK) {
                return true;
            }
            throw new IllegalArgumentException();
        });
        List<ButtonType> buttons = dialog.getDialogPane().getButtonTypes();
        buttons.clear();
        buttons.add(ButtonType.OK);
        buttons.add(ButtonType.CANCEL);
        return dialog;
    }

    public static Alert newStandardAlert(AlertType alertType) {
        Alert alert = configureDialog(new Alert(alertType));
        alert.getButtonTypes().clear();
        alert.getButtonTypes().add(ButtonType.OK);
        return alert;
    }

}
