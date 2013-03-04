package prash.tools;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.net.*;

public class JarHow {

  private static Map<File, String> classpath = new LinkedHashMap<File,String>();
  private static Map<String, File> resourceMap = new HashMap<String, File>();
  private static boolean debug; // -d
  private static boolean list; // -l
  private static boolean dumpCP; // -c

  public static void processClassPath(String cp, String delmimter,
                                      File parentDir, File referer) throws IOException
  {
    if (debug) System.out.println("Processing classpath:" + cp);
    StringTokenizer tokenizer = new StringTokenizer(cp, delmimter);
    while (tokenizer.hasMoreTokens()) {
      String element = tokenizer.nextToken();
      File f = (parentDir == null ? new File(element) : new File(parentDir, element));
      if (f.exists()) {
        try {
          f = f.getCanonicalFile();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (classpath.containsKey(f)) {
        if (debug) System.out.println("Found duplicate reference to file: "  +
            f + " from: " + referer);
        String val = classpath.get(f);
        val = (val == null) ? "" : val + ", ";
        classpath.put(f, val + (referer == null ? "@" : referer.getPath()) );
        continue;
      }
      classpath.put(f, (parentDir == null)? null : referer.getPath());
      if (!f.exists()) {
        if (debug) System.out.println("File does not exist - " + f);
        continue;
      }
      if (f.isDirectory()) continue;
      // follow manifests
      JarFile jar = new JarFile(f);
      Manifest manifest = jar.getManifest();
      if (manifest != null) {
        String mcp = manifest.getMainAttributes().getValue(
            Attributes.Name.CLASS_PATH);
        if (mcp != null && mcp.length() > 0) {
          if (debug) System.out.println("Following manifest of file: " + f);
          processClassPath(mcp, " ", f.getParentFile(), f);
        }
      }
    }
  }

  public static Enumeration<URL> findClass(String className) throws IOException {
    if (className.startsWith("%")) {
      // this is a resource
      className = className.substring(1);
    } else {
      //if (!className.startsWith("/")) className = "/" + className;
      className = className.replace('.', '/') + ".class";
    }
    System.out.println("Querying... " + className);
    return JarHow.class.getClassLoader().getResources(className);
    //return JarHow.class.getResource(className);
  }

  public static void printUsage(String error) {
    System.out.println("ERROR: " + error);
    printUsage();
  }

  public static void printUsage() {
    StringBuilder sb = new StringBuilder();
    sb.append("\njava prash.tools.JHow [options] class1 class2 ...");
    sb.append("\n  -c dump the effective classpath");
    sb.append("\n  -d output debug information");
    sb.append("\n  -l list all resources");
    sb.append("\n");
    sb.append("\nPrepend resources with '%'  ");
    System.out.println(sb.toString());
    System.exit(1);
  }

  public static void main(String args[]) throws Exception {
    long t0 = System.currentTimeMillis();
    if (args.length == 0) printUsage();
    for (String arg : args) {
      if (arg.startsWith("-")) {
        if (arg.equals("-d")) debug = true;
        else if (arg.equals("-l")) list = true;
        else if (arg.equals("-c")) dumpCP = true;
        else printUsage("Unknown option " + arg);
      } else {
        Enumeration<URL> urls = findClass(arg);
        //URL urls = findClass(arg);
        if (urls == null || !urls.hasMoreElements()) {
          System.out.println("Not found");
          System.exit(0);
        }

        while(urls.hasMoreElements()) {
          URL u = urls.nextElement();
          System.out.println(u);
        }
      }
    }

    if (!dumpCP && !list) return;

    System.out.println("Processing classpath...");
    processClassPath(System.getProperty("java.class.path"), File.pathSeparator,
        null, null);


    if (dumpCP) {
      System.out.println("Dumping classpath...");
      for (Map.Entry<File,String> e : classpath.entrySet()) {
        if (e.getValue() != null) {
          System.out.println(e.getKey() + " <- " + e.getValue());
        } else {
          System.out.println(e.getKey());
        }
      }
    }
    int resourceCount = 0;
    if (list) {
      File tempFile = File.createTempFile("JarHow-", ".txt");
      System.out.println("Creating resource listing at " + tempFile.getPath() );
      PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(tempFile)));
      for (File f : classpath.keySet()) {
        resourceCount += dumpAllResources(f, pw);
      }
      pw.flush();
      pw.close();
    }
    System.out.println("-----------------------------------");
    System.out.println("Elapsed time: " + (System.currentTimeMillis() - t0));
    System.out.println("Classpath entries: " + classpath.size());
    System.out.println("Resources scanned: " + resourceCount);
  }

  private static int dumpAllResources(File f, PrintWriter writer) throws IOException {
    int n = 0;
    if (!f.exists()) {
      if (debug) System.out.println("Not found: " + f);
      return n;
    }
    if (f.isDirectory()) {
      n += dumpFiles(f, writer);
    } else if (f.getName().endsWith(".jar") ) {
      n += dumpJarFiles(f, writer);
    } else {
      n++;
      writer.println(f);
    }
    return n;
  }

  private static int dumpFiles(File parent, PrintWriter writer) {
    int n = 0;
    for (File child : parent.listFiles()) {
      if (child.isDirectory()) n += dumpFiles(child, writer);
      else {
        n++;
        writer.println(child + "\t\t\t" + parent);
      }
    }
    return n;
  }

  private static int dumpJarFiles(File jar, PrintWriter writer) throws IOException {
    int n = 0;
    ZipFile zip = new ZipFile(jar);
    Enumeration e = zip.entries();
    while (e.hasMoreElements()) {
      n++;
      writer.println(e.nextElement() +"\t\t\t" + jar);
    }
    return n;
  }
}
