import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;

/** JDK 25 bytecode experiment. No instrumentation of JDK or host classes. */
public final class SpikeLoader extends URLClassLoader {
    private final URL logConfig;
    private static final ClassDesc HOOK = ClassDesc.of("SpikeHooks");
    private static final ClassDesc FILE = ClassDesc.of("SpikeHooks$ServerFile");

    public SpikeLoader(URL[] urls, Path logConfig) throws IOException {
        super(urls, new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                return name.equals("SpikeHooks") || name.startsWith("SpikeHooks$")
                    ? Embed.class.getClassLoader().loadClass(name) : super.loadClass(name, resolve);
            }
        });
        this.logConfig = logConfig.toUri().toURL();
    }

    @Override
    public URL findResource(String name) {
        return name.equals("log4j2.xml") ? logConfig : super.findResource(name);
    }

    @Override
    public java.util.Enumeration<URL> findResources(String name) throws IOException {
        return name.equals("log4j2.xml") ? java.util.Collections.enumeration(java.util.List.of(logConfig))
            : super.findResources(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = super.findResource(name.replace('.', '/') + ".class");
        if (resource == null) throw new ClassNotFoundException(name);
        try {
            var connection = resource.openConnection();
            byte[] bytes;
            try (var input = connection.getInputStream()) { bytes = input.readAllBytes(); }
            var cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing(this).orElse(
                    ClassHierarchyResolver.ofResourceParsing(Embed.class.getClassLoader()))));
            var model = cf.parse(bytes);
            boolean fileSubclass = model.superclass().map(c -> c.asInternalName().equals("java/io/File")).orElse(false);
            byte[] transformed = cf.transformClass(model,
                ClassTransform.transformingMethodBodies((code, element) -> {
                    if (element instanceof NewObjectInstruction object
                            && object.className().asInternalName().equals("java/io/File")) {
                        code.new_(FILE);
                        return;
                    }
                    if (element instanceof InvokeInstruction call) {
                        String owner = call.owner().asInternalName();
                        String method = call.name().stringValue();
                        var type = call.typeSymbol();
                        if (!fileSubclass && owner.equals("java/io/File") && method.equals("<init>")) {
                            code.invokespecial(FILE, method, type);
                            return;
                        }
                        if ((owner.equals("java/nio/file/Path") && method.equals("of"))
                                || (owner.equals("java/nio/file/Paths") && method.equals("get"))) {
                            code.invokestatic(HOOK, "path", type);
                            return;
                        }
                        if (owner.equals("java/nio/file/FileSystem") && method.equals("getPath")) {
                            code.invokestatic(HOOK, "path", type.insertParameterTypes(0, call.owner().asSymbol()));
                            return;
                        }
                        if (owner.equals("java/lang/System") && (method.equals("exit") || method.equals("getProperty"))) {
                            code.invokestatic(HOOK, method.equals("exit") ? "exit" : "property", type);
                            return;
                        }
                        if (owner.equals("java/lang/Runtime") && java.util.Set.of(
                                "exit", "halt", "addShutdownHook", "removeShutdownHook").contains(method)) {
                            code.invokestatic(HOOK, method, type.insertParameterTypes(0, call.owner().asSymbol()));
                            return;
                        }
                    }
                    code.with(element);
                }));
            int dot = name.lastIndexOf('.');
            URL source = resource;
            if (connection instanceof JarURLConnection jar) {
                source = jar.getJarFileURL();
                if (dot > 0 && getDefinedPackage(name.substring(0, dot)) == null) {
                    var manifest = jar.getManifest();
                    if (manifest == null) definePackage(name.substring(0, dot), null, null, null, null, null, null, null);
                    else definePackage(name.substring(0, dot), manifest, source);
                }
            }
            return defineClass(name, transformed, 0, transformed.length,
                new CodeSource(source, (Certificate[]) null));
        } catch (IOException error) {
            throw new ClassNotFoundException(name, error);
        }
    }
}
