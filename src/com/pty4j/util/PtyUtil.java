package com.pty4j.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.pty4j.windows.WinPty;
import com.sun.jna.Platform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
/**
 * @author traff
 */
public class PtyUtil {
  public static final String OS_VERSION = System.getProperty("os.version").toLowerCase();

  private final static String PTY_LIB_FOLDER = System.getenv("PTY_LIB_FOLDER");

  public static String[] toStringArray(Map<String, String> environment) {
    List<String> list = Lists.transform(Lists.newArrayList(environment.entrySet()), new Function<Map.Entry<String, String>, String>() {
      public String apply(Map.Entry<String, String> entry) {
        return entry.getKey() + "=" + entry.getValue();
      }
    });
    return list.toArray(new String[list.size()]);
  }

  /**
   * Returns the folder that contains a jar that contains the class
   *
   * @param aclass a class to find a jar
   * @return
   */
  private static String getJarContainingFolderPath(Class aclass) throws Exception {
    CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();

    File jarFile;

    if (codeSource.getLocation() != null) {
      URI location = codeSource.getLocation().toURI();
      if (location.getScheme().equals("jar")) {
        location = new URI(location.getSchemeSpecificPart());
        if (location.getScheme().equals("file")) {
          String part = location.getSchemeSpecificPart();
          int index = part.lastIndexOf('!');
          location = new URI(part.substring(0, index));
        }
      }
      jarFile = new File(location.getPath());
    } else {
      String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();

      int startIndex = path.indexOf(":") + 1;
      int endIndex = path.indexOf("!");
      if (startIndex == -1 || endIndex == -1) {
        throw new IllegalStateException("Class " + aclass.getSimpleName() + " is located not within a jar: " + path);
      }
      String jarFilePath = path.substring(startIndex, endIndex);
      jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
      jarFile = new File(jarFilePath);
    }
    return jarFile.getParentFile().getAbsolutePath();
  }

  private static String getPtyLibFolderPath() throws Exception {
    if (PTY_LIB_FOLDER != null) {
      return PTY_LIB_FOLDER;
    }
    //Class aclass = WinPty.class.getClassLoader().loadClass("com.jediterm.pty.PtyMain");
    Class aclass = WinPty.class;

    return getJarContainingFolderPath(aclass);
  }

  public static File resolveNativeLibrary() throws Exception {
    String libFolderPath = getPtyLibFolderPath();

    if (libFolderPath != null) {

      File libFolder = new File(libFolderPath);
      File lib = resolveNativeLibrary(libFolder);

      lib = lib.exists() ? lib : resolveNativeLibrary(new File(libFolder, "libpty"));

      if (!lib.exists()) {
        return unpackNativeLibrary();
      }

      return lib;
    } else {
      throw new IllegalStateException("Couldn't detect lib folder");
    }
  }

  private static File unpackNativeLibrary() throws IOException {
    String platformFolder = getPlatformFolder();
    String archFolder = getArchFolder();
    String libName = getNativeLibraryName();
    String resourceName = Joiner.on("/").join("", platformFolder, archFolder,
        libName);
    InputStream is = PtyUtil.class.getResourceAsStream(resourceName);
    if (is == null) {
      throw new IOException("Failed to load resource " + resourceName);
    }

    File location = new File(System.getProperty("java.io.tmpdir"), libName);
    FileOutputStream fos = new FileOutputStream(location);
    try {
      ByteStreams.copy(is, fos);
    } finally {
      fos.close();
    }
    return location;
  }

  private static File resolveNativeLibrary(File parent) {
    File path = new File(parent, getPlatformFolder());
    path = new File(path,getArchFolder());
    return new File(path, getNativeLibraryName());
  }

  private static String getArchFolder() {
	  if (isWinXp()) return "xp";
	  else if (Platform.is64Bit()) return "x86_64";
	  else return "x86";
  }
  
  private static String getPlatformFolder() {
    String result;

    if (Platform.isMac()) {
      result = "macosx";
    } else if (Platform.isWindows()) {
      result = "win";
    } else if (Platform.isLinux()) {
      result = "linux";
    } else {
      throw new IllegalStateException("Platform " + Platform.getOSType() + " is not supported");
    }

    return result;
  }

  private static String getNativeLibraryName() {
    String result;

    if (Platform.isMac()) {
      result = "libpty.dylib";
    } else if (Platform.isWindows()) {
      result = "libwinpty.dll";
    } else if (Platform.isLinux()) {
      result = "libpty.so";
    } else {
      throw new IllegalStateException("Platform " + Platform.getOSType() + " is not supported");
    }

    return result;
  }

  public static boolean isWinXp() {
    return Platform.isWindows() && (OS_VERSION.equals("5.1") || OS_VERSION.equals("5.2"));
  }
}
