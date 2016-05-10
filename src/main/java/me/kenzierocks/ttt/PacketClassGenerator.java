package me.kenzierocks.ttt;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public final class PacketClassGenerator {

    private static final String SOURCE_DIR =
            "src/main/resources/me/kenzierocks/ttt/packetdefs";
    private static final Path TARGET_DIR = Paths.get("src/main/java");
    private static final String PACKAGE = "me.kenzierocks.ttt.packets";
    private static final String JAVADOC = "Generated from $L\n\t on "
            + ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    private static final String AUTO_CONSTRUCTOR_WARNING =
            "@deprecated This constructor is only for automatic creation. Do not call.\n";

    public static void main(String[] args) {
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(Paths.get(SOURCE_DIR),
                        path -> path.toString().endsWith(".packet"))) {
            stream.forEach(PacketClassGenerator::createPacketClass);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createPacketClass(Path source) {
        try {
            PacketData parsed = PacketData.read(source);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        TypeSpec.Builder type = TypeSpec
                .classBuilder(
                        noExtension(source.getFileName().toString()) + "Packet")
                .addModifiers(PUBLIC, FINAL);

        type.addMethod(MethodSpec.constructorBuilder()
                .addJavadoc(AUTO_CONSTRUCTOR_WARNING)
                .addAnnotation(Deprecated.class).addModifiers(PUBLIC)
                .addCode("super();\n").build());

        JavaFile fileWriter = JavaFile.builder(PACKAGE, type.build())
                .indent("....".replace('.', ' ')).skipJavaLangImports(true)
                .addFileComment(JAVADOC, source.toString()).build();
        try {
            fileWriter.writeTo(TARGET_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String noExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

}
