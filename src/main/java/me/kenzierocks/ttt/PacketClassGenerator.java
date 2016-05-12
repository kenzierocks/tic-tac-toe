package me.kenzierocks.ttt;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import me.kenzierocks.ttt.PacketData.Pipe;
import me.kenzierocks.ttt.packets.Packet;
import me.kenzierocks.ttt.packets.PacketReader;

public final class PacketClassGenerator {

    private static final Path SOURCE_DIR =
            Paths.get("src/main/resources/me/kenzierocks/ttt/packetdefs");
    private static final Path TARGET_DIR = Paths.get("src/main/java");
    private static final String PACKAGE = "me.kenzierocks.ttt.packets";
    private static final String JAVADOC = "Generated from $L\n\t on "
            + ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);

    public static void main(String[] args) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SOURCE_DIR,
                path -> path.toString().endsWith(".packet"))) {
            stream.forEach(PacketClassGenerator::createPacketClass);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createPacketClass(Path source) {
        PacketData parsed;
        try {
            parsed = PacketData.read(source);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String appendPackage =
                parsed.getDirectionPipe() == Pipe.CLIENT_TO_SERVER ? "c2s"
                        : "s2c";
        String constructedPackage = PACKAGE + "." + appendPackage;
        Map<String, TypeName> fields = parsed.getFields().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> TypeName.get(e.getValue().getJavaType())));
        String sourceFile = SOURCE_DIR.relativize(source).toString();
        String nameBase = noExtension(source.getFileName().toString());
        writePacketClass(parsed.getDirectionPipe(), sourceFile, nameBase,
                fields, parsed.getFields(), constructedPackage);
        writePacketReader(sourceFile, nameBase, fields, parsed.getFields(),
                constructedPackage);
    }

    private static void writePacketClass(Pipe pipe, String sourceFile,
            String nameBase, Map<String, TypeName> fields,
            Map<String, PacketPart> fieldParts, String fullPackage) {
        /*
         * N.B. in the following method code we loop over `fields` a bunch, we
         * might want to combine them all into one loop for potentially faster
         * code
         */
        TypeSpec.Builder type = TypeSpec.classBuilder(nameBase + "Packet")
                .addModifiers(PUBLIC, FINAL);

        if (pipe == Pipe.CLIENT_TO_SERVER) {
            type.addSuperinterface(Packet.Client.class);
        } else {
            type.addSuperinterface(Packet.Server.class);
        }

        fields.forEach((name, fType) -> {
            type.addField(fType, name, PRIVATE, FINAL);
        });

        List<ParameterSpec> parameters = fields.entrySet().stream().map(e -> {
            return ParameterSpec.builder(e.getValue(), e.getKey()).build();
        }).collect(Collectors.toList());

        String dataStreamName = "dataStream";

        CodeBlock.Builder constrCode = CodeBlock.builder();
        CodeBlock.Builder writeCode = CodeBlock.builder();

        fields.forEach((name, fType) -> {
            constrCode.addStatement("this.$1L = $1L", name);
            writeCode.addStatement("$L.write$L(this.$L)", dataStreamName,
                    getDataStreamSuffix(fieldParts.get(name)), name);
        });

        type.addMethod(MethodSpec.constructorBuilder().addModifiers(PUBLIC)
                .addParameters(parameters).addCode(constrCode.build()).build());

        type.addMethod(MethodSpec.methodBuilder("write")
                .addAnnotation(Override.class).addModifiers(PUBLIC)
                .addParameter(DataOutputStream.class, dataStreamName)
                .addException(IOException.class).addCode(writeCode.build())
                .build());

        fields.forEach((name, fType) -> {
            type.addMethod(MethodSpec
                    .methodBuilder("get" + Util.uppercaseFirstLetter(name))
                    .addModifiers(PUBLIC).returns(fType)
                    .addCode("return $L;\n", name).build());
        });

        JavaFile fileWriter = JavaFile.builder(fullPackage, type.build())
                .indent("....".replace('.', ' ')).skipJavaLangImports(true)
                .addFileComment(JAVADOC, sourceFile).build();
        try {
            fileWriter.writeTo(TARGET_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writePacketReader(String sourceFile, String nameBase,
            Map<String, TypeName> fields, Map<String, PacketPart> fieldParts,
            String fullPackage) {
        TypeSpec.Builder type = TypeSpec.classBuilder(nameBase + "PacketReader")
                .addModifiers(PUBLIC, FINAL);

        ClassName packetClassName =
                ClassName.get(fullPackage, nameBase + "Packet");
        type.addSuperinterface(ParameterizedTypeName
                .get(ClassName.get(PacketReader.class), packetClassName));

        String dataStreamName = "dataStream";

        CodeBlock.Builder writeCode = CodeBlock.builder();

        fields.forEach((name, fType) -> {
            writeCode.addStatement("$T $L = $L.$L()", fType, name,
                    dataStreamName,
                    "read" + getDataStreamSuffix(fieldParts.get(name)));
        });
        writeCode.addStatement("return new $T($L)", packetClassName,
                String.join(", ", fields.keySet()));

        type.addMethod(
                MethodSpec.methodBuilder("read").addAnnotation(Override.class)
                        .addModifiers(PUBLIC).returns(packetClassName)
                        .addParameter(DataInputStream.class, dataStreamName)
                        .addException(IOException.class)
                        .addCode(writeCode.build()).build());

        JavaFile fileWriter = JavaFile.builder(fullPackage, type.build())
                .indent("....".replace('.', ' ')).skipJavaLangImports(true)
                .addFileComment(JAVADOC, sourceFile).build();
        try {
            fileWriter.writeTo(TARGET_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getDataStreamSuffix(PacketPart part) {
        if (part == PacketPart.STRING) {
            return "UTF";
        }
        return Util.uppercaseFirstLetter(part.name().toLowerCase());
    }

    private static String noExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

}
