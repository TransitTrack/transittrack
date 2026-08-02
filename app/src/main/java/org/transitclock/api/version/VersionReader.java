/* (C)2026 */
package org.transitclock.api.version;

import org.transitclock.Application;
import org.transitclock.api.data.ApiVersion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Reads application version metadata from the runtime classpath (JAR manifest, Maven pom.properties,
 * git.properties, version.properties).
 */
final class VersionReader {

    private static final String GROUP_ID = "ro.vladvesa.transitclock";
    private static final String ARTIFACT_ID = "app";

    private VersionReader() {}

    static ApiVersion read() {
        Optional<Properties> gitProperties = readGitProperties();

        String version = readImplementationVersion();
        if (version == null) {
            version = readMavenPropertiesVersion();
        }
        if (version == null) {
            version = gitProperties
                    .map(git -> git.getProperty("git.build.version"))
                    .filter(value -> !isBlank(value))
                    .orElse(null);
        }
        if (version == null) {
            version = readVersionProperties();
        }
        if (version == null) {
            version = "unknown";
        }

        ApiVersion apiVersion = new ApiVersion(version);

        gitProperties.ifPresent(git -> {
            String commit = firstNonBlank(git.getProperty("git.commit.id.abbrev"), git.getProperty("git.commit.id"));
            if (commit != null) {
                apiVersion.setCommit(commit);
            }

            String buildTime = firstNonBlank(git.getProperty("git.build.time"), git.getProperty("git.commit.time"));
            if (buildTime != null) {
                apiVersion.setBuildTime(buildTime);
            }
        });

        if (apiVersion.getCommit() == null) {
            String commit = readManifestAttribute("Git-Commit");
            if (commit != null) {
                apiVersion.setCommit(commit);
            }
        }

        if (apiVersion.getBuildTime() == null) {
            String buildTime = readManifestAttribute("Build-Time");
            if (buildTime != null) {
                apiVersion.setBuildTime(buildTime);
            }
        }

        return apiVersion;
    }

    private static String readImplementationVersion() {
        String version = readManifestAttribute(Attributes.Name.IMPLEMENTATION_VERSION.toString());
        if (version != null) {
            return version;
        }

        Package pkg = Application.class.getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
            if (!isBlank(version)) {
                return version;
            }
        }

        return null;
    }

    private static String readVersionProperties() {
        return readPropertiesVersion("version.properties");
    }

    private static String readMavenPropertiesVersion() {
        String[] candidates = {
            "META-INF/maven/" + GROUP_ID + "/" + ARTIFACT_ID + "/pom.properties",
            "META-INF/maven/" + GROUP_ID + "/transitclock/pom.properties",
        };
        for (String candidate : candidates) {
            String version = readPropertiesVersion(candidate);
            if (version != null) {
                return version;
            }
        }

        return readMavenPropertiesVersionFromJar();
    }

    private static String readMavenPropertiesVersionFromJar() {
        try {
            CodeSource codeSource = Application.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }

            Path path = Path.of(codeSource.getLocation().toURI());
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
                return null;
            }

            String prefix = "META-INF/maven/" + GROUP_ID + "/";
            try (JarFile jarFile = new JarFile(path.toFile())) {
                return jarFile.stream()
                        .filter(entry -> entry.getName().startsWith(prefix) && entry.getName().endsWith("/pom.properties"))
                        .map(entry -> readPropertiesVersion(jarFile, entry))
                        .filter(version -> !isBlank(version))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readPropertiesVersion(String resourcePath) {
        String resource = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = Application.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            return isBlank(version) ? null : version;
        } catch (IOException e) {
            return null;
        }
    }

    private static String readPropertiesVersion(JarFile jarFile, JarEntry entry) {
        try (InputStream in = jarFile.getInputStream(entry)) {
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            return isBlank(version) ? null : version;
        } catch (IOException e) {
            return null;
        }
    }

    private static Optional<Properties> readGitProperties() {
        try (InputStream in = Application.class.getClassLoader().getResourceAsStream("git.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return Optional.of(props);
            }
        } catch (IOException ignored) {
            // fall through
        }

        return readGitPropertiesFromJar();
    }

    private static Optional<Properties> readGitPropertiesFromJar() {
        try {
            CodeSource codeSource = Application.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return Optional.empty();
            }

            Path path = Path.of(codeSource.getLocation().toURI());
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
                return Optional.empty();
            }

            try (JarFile jarFile = new JarFile(path.toFile())) {
                JarEntry entry = jarFile.getJarEntry("git.properties");
                if (entry == null) {
                    return Optional.empty();
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(in);
                    return Optional.of(props);
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String readManifestAttribute(String name) {
        try {
            CodeSource codeSource = Application.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return readManifestAttributeFromClasspath(name);
            }

            Path path = Path.of(codeSource.getLocation().toURI());
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(path.toFile())) {
                    Manifest manifest = jarFile.getManifest();
                    if (manifest != null) {
                        String value = manifest.getMainAttributes().getValue(name);
                        if (!isBlank(value)) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }

        return readManifestAttributeFromClasspath(name);
    }

    private static String readManifestAttributeFromClasspath(String name) {
        try (InputStream in = Application.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in == null) {
                return null;
            }
            Attributes attributes = new Manifest(in).getMainAttributes();
            String value = attributes.getValue(name);
            return isBlank(value) ? null : value;
        } catch (IOException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
