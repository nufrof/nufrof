package com.nufrof.classpath;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GetCp {
    private static Set<String> alreadyScanned = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("AS = Already Scanned.");
        System.out.println("NCP = Either no MANIFEST.MF or no Class-Path attribute in the MANIFEST.MF.");
        System.out.println("DNE = The jar doesn't exist.");
        traverse(args[0], null);
    }

    public static void traverse(String jarPath, Integer iteration) throws Exception {
        if (iteration == null) {
            iteration = 0;
        }
        String spacer = "";
        for (int i = 0; i < iteration; i++) {
            spacer += "  ";
        }
        spacer += "- ";
        File jarFile = new File(jarPath);
        if (alreadyScanned.contains(jarFile.getAbsolutePath())) {
            System.out.println(spacer + jarPath + " [AS]");
            return;
        }
        alreadyScanned.add(jarFile.getAbsolutePath());
        if (!doesJarExist(jarPath)) {
            System.out.println(spacer + jarPath + " [DNE]");
            return;
        }
        if (jarFile.isDirectory()) {
            for (File file : jarFile.listFiles()) {
                if (file.getName().endsWith(".jar")) {
                    traverse(file.getAbsolutePath(), iteration);
                }
            }
            return;
        }
        List<String> classPathList = getJarsManifestClasspath(jarPath);
        if (classPathList == null || classPathList.isEmpty()) {
            System.out.println(spacer + jarPath + " [NCP]");
            return;
        } else {
            System.out.println(spacer + jarPath);
        }
        for (String s : classPathList) {
            String depPath = getAbsolutePathOfDependency(jarPath, s);
            traverse(depPath, iteration + 1);
        }
    }

    private static String getAbsolutePathOfDependency(String jarFilePath, String dependencyPathFromManifest) {
        Path jarPath = Paths.get(new File(jarFilePath).getParent());
        Path dependencyPath = Paths.get(dependencyPathFromManifest);
        if (dependencyPath.isAbsolute()) {
            return dependencyPathFromManifest;
        }
        return Paths.get(jarPath.toString(), dependencyPath.toString()).toAbsolutePath().normalize().toString();
    }

    private static boolean doesJarExist(String pathToJar) {
        File file = new File(pathToJar);
        return file.exists();
    }

    private static List<String> getJarsManifestClasspath(String pathToJar) throws Exception {
        ZipFile zipFile = new ZipFile(pathToJar);
        ZipEntry manifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
        if (manifestEntry == null) return null;
        InputStream inputStream = zipFile.getInputStream(manifestEntry);
        Manifest manifest = new Manifest(inputStream);
        String classPath = manifest.getMainAttributes().getValue("Class-Path");
        if (classPath == null) return null;
        List<String> classPathList = Arrays.asList(classPath.split(" "));
        return classPathList;
    }
}
