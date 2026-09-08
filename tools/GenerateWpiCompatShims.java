import java.io.InputStream;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class GenerateWpiCompatShims {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java GenerateWpiCompatShims.java [wpimath-jar] [target-dir-1] [target-dir-2...]");
            System.exit(1);
        }

        Path wpimathJar = Paths.get(args[0]);
        if (!Files.exists(wpimathJar)) {
            System.err.println("wpimath jar not found at: " + wpimathJar);
            System.exit(1);
        }

        List<Path> targets = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path targetDir = Paths.get(args[i]);
            Files.createDirectories(targetDir.resolve("org/wpilib/math/geometry"));
            targets.add(targetDir);
        }

        ClassFile cf = ClassFile.of();

        try (JarFile jar = new JarFile(wpimathJar.toFile())) {
            patchTranslation2d(cf, jar, targets);
            patchRotation2d(cf, jar, targets);
            patchPose2d(cf, jar, targets);
        }

        // Copy extra bridge classes from primary target (build/classes/java/main) to secondary targets (e.g. bin/default, bin/main)
        if (targets.size() > 1) {
            Path primary = targets.get(0);
            String[] extraClasses = new String[] {
                "org/wpilib/math/util/Pair.class",
                "org/wpilib/driverstation/Alert.class",
                "org/wpilib/driverstation/Alert$Level.class"
            };
            for (String rel : extraClasses) {
                Path src = primary.resolve(rel);
                if (Files.exists(src)) {
                    byte[] bytes = Files.readAllBytes(src);
                    writeToTargets(targets, rel, bytes);
                }
            }
        }

        System.out.println("[WpiCompat] Successfully generated compatibility shims for Translation2d, Rotation2d, and Pose2d.");
    }

    private static byte[] readEntry(JarFile jar, String entryName) throws Exception {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null) {
            throw new IllegalArgumentException("Entry not found in jar: " + entryName);
        }
        try (InputStream is = jar.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    private static void writeToTargets(List<Path> targets, String relativePath, byte[] classBytes) throws Exception {
        for (Path target : targets) {
            Path out = target.resolve(relativePath);
            Files.createDirectories(out.getParent());
            Files.write(out, classBytes);
        }
    }

    private static void patchTranslation2d(ClassFile cf, JarFile jar, List<Path> targets) throws Exception {
        byte[] orig = readEntry(jar, "org/wpilib/math/geometry/Translation2d.class");
        ClassModel model = cf.parse(orig);

        ClassDesc cdTranslation2d = ClassDesc.of("org.wpilib.math.geometry.Translation2d");
        ClassDesc cdRotation2d = ClassDesc.of("org.wpilib.math.geometry.Rotation2d");
        ClassDesc cdOptional = ClassDesc.of("java.util.Optional");
        ClassDesc cdObject = ClassDesc.of("java.lang.Object");

        MethodTypeDesc mtdOptionalGetAngle = MethodTypeDesc.of(cdOptional);
        MethodTypeDesc mtdRotation2dGetAngle = MethodTypeDesc.of(cdRotation2d);
        MethodTypeDesc mtdOrElse = MethodTypeDesc.of(cdObject, cdObject);

        ClassTransform transform = ClassTransform.ofStateful(() -> new ClassTransform() {
            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                if (element instanceof MethodModel mm && mm.methodName().stringValue().equals("<clinit>")) {
                    builder.withMethod("<clinit>", mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> {
                        for (MethodElement me : mm) {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cb -> {
                                    for (CodeElement ce : cm) {
                                        if (ce instanceof ReturnInstruction ri && ri.opcode() == Opcode.RETURN) {
                                            cb.getstatic(cdTranslation2d, "ZERO", cdTranslation2d)
                                              .putstatic(cdTranslation2d, "kZero", cdTranslation2d)
                                              .return_();
                                        } else {
                                            cb.accept(ce);
                                        }
                                    }
                                });
                            } else {
                                mb.accept(me);
                            }
                        }
                    });
                } else {
                    builder.accept(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                builder.withField("kZero", cdTranslation2d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
                builder.withMethod("getAngle", mtdRotation2dGetAngle, ClassFile.ACC_PUBLIC, mb -> {
                    mb.withCode(cb -> {
                        cb.aload(0)
                          .invokevirtual(cdTranslation2d, "getAngle", mtdOptionalGetAngle)
                          .getstatic(cdRotation2d, "ZERO", cdRotation2d)
                          .invokevirtual(cdOptional, "orElse", mtdOrElse)
                          .checkcast(cdRotation2d)
                          .areturn();
                    });
                });
            }
        });

        byte[] patched = cf.transformClass(model, transform);
        writeToTargets(targets, "org/wpilib/math/geometry/Translation2d.class", patched);
    }

    private static void patchRotation2d(ClassFile cf, JarFile jar, List<Path> targets) throws Exception {
        byte[] orig = readEntry(jar, "org/wpilib/math/geometry/Rotation2d.class");
        ClassModel model = cf.parse(orig);

        ClassDesc cdRotation2d = ClassDesc.of("org.wpilib.math.geometry.Rotation2d");

        ClassTransform transform = ClassTransform.ofStateful(() -> new ClassTransform() {
            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                if (element instanceof MethodModel mm && mm.methodName().stringValue().equals("<clinit>")) {
                    builder.withMethod("<clinit>", mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> {
                        for (MethodElement me : mm) {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cb -> {
                                    for (CodeElement ce : cm) {
                                        if (ce instanceof ReturnInstruction ri && ri.opcode() == Opcode.RETURN) {
                                            cb.getstatic(cdRotation2d, "ZERO", cdRotation2d)
                                              .putstatic(cdRotation2d, "kZero", cdRotation2d)
                                              .getstatic(cdRotation2d, "PI", cdRotation2d)
                                              .putstatic(cdRotation2d, "kPi", cdRotation2d)
                                              .getstatic(cdRotation2d, "CW_90DEG", cdRotation2d)
                                              .putstatic(cdRotation2d, "kCW_90deg", cdRotation2d)
                                              .return_();
                                        } else {
                                            cb.accept(ce);
                                        }
                                    }
                                });
                            } else {
                                mb.accept(me);
                            }
                        }
                    });
                } else {
                    builder.accept(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                builder.withField("kZero", cdRotation2d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
                builder.withField("kPi", cdRotation2d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
                builder.withField("kCW_90deg", cdRotation2d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }
        });

        byte[] patched = cf.transformClass(model, transform);
        writeToTargets(targets, "org/wpilib/math/geometry/Rotation2d.class", patched);
    }

    private static void patchPose2d(ClassFile cf, JarFile jar, List<Path> targets) throws Exception {
        byte[] orig = readEntry(jar, "org/wpilib/math/geometry/Pose2d.class");
        ClassModel model = cf.parse(orig);

        ClassDesc cdPose2d = ClassDesc.of("org.wpilib.math.geometry.Pose2d");

        ClassTransform transform = ClassTransform.ofStateful(() -> new ClassTransform() {
            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                if (element instanceof MethodModel mm && mm.methodName().stringValue().equals("<clinit>")) {
                    builder.withMethod("<clinit>", mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> {
                        for (MethodElement me : mm) {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cb -> {
                                    for (CodeElement ce : cm) {
                                        if (ce instanceof ReturnInstruction ri && ri.opcode() == Opcode.RETURN) {
                                            cb.getstatic(cdPose2d, "ZERO", cdPose2d)
                                              .putstatic(cdPose2d, "kZero", cdPose2d)
                                              .return_();
                                        } else {
                                            cb.accept(ce);
                                        }
                                    }
                                });
                            } else {
                                mb.accept(me);
                            }
                        }
                    });
                } else {
                    builder.accept(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                builder.withField("kZero", cdPose2d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }
        });

        byte[] patched = cf.transformClass(model, transform);
        writeToTargets(targets, "org/wpilib/math/geometry/Pose2d.class", patched);
    }
}
