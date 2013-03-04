package prash.tools;

import java.lang.reflect.*;
import java.io.*;
import java.net.*;

/**
 */

public class Proxy extends Thread {

  private static boolean xFlag = false;
  private static boolean fFlag = false;
  private static boolean dFlag = false;
  private static int delayMillis = 0;
  private static Class analyzerClass = null;
  private static Method reqMethod = null;
  private static Method resMethod = null;

  private final Socket fromSock;
  private final Socket toSock;
  private final boolean request;
  private Object analyzer = null;

  public Proxy(Socket front, Socket rear, boolean f, Object obj) {
    fromSock = front;
    toSock = rear;
    request = f;
    analyzer = obj;
  }

  @Override
  public void run() {
    // load the protocol handler if one exists, else load default handler
    if (analyzerClass != null) {
      try {
        if (request) {
          reqMethod.invoke(analyzer, new Object[] {fromSock, toSock});
        } else {
          resMethod.invoke(analyzer, new Object[] {fromSock, toSock});
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    } else if (xFlag) {
      hexAnalyze(fromSock, toSock, request);
    } else if (delayMillis != 0) {
      delayAnalyze(fromSock, toSock, request);
    } else {
      defaultAnalyze(fromSock, toSock, request);
    }
  }

  // log handling
  private static PrintWriter reqLogger;
  private static PrintWriter resLogger;

  private synchronized static void log(String s, boolean req) {
    if (!fFlag) {
      System.out.print(s);
      return;
    }
    try {
      if (reqLogger == null) {
        reqLogger = new PrintWriter(new FileOutputStream("request.log"));
        resLogger = new PrintWriter(new FileOutputStream("response.log"));
      }
      if (req) {
        reqLogger.print(s);
        reqLogger.flush();
      } else {
        resLogger.print(s);
        resLogger.flush();
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

  }

  private static void defaultAnalyze(Socket src, Socket dst, boolean req) {
    int n;
    if (dFlag) log("INI " + src + " > " + dst + "\n", req);
    byte[] data = new byte[10 * 1024];
    try {
      InputStream is = src.getInputStream();
      OutputStream os = dst.getOutputStream();
      for (;;) {
        n = is.read(data);
        if (n == -1) {
          // simulate a FIN
          if (dFlag) log("FIN " + src + " > " + dst + "\n", req);
          src.getInputStream().close();
          dst.getOutputStream().close();
          break;
        }
        if (dFlag) log("PSH " + src + " > " + dst + " " + n + " bytes"
          + "\n", req);
        os.write(data, 0, n);
      }
    } catch (IOException ioe) {
      if (dFlag) log("ERR " + src + " > " + dst + "\n", req);
      //if (dFlag) ioe.printStackTrace();
      try {
        src.close();
        dst.close();
      } catch (IOException ignore) { }
    }
  }

  private static void delayAnalyze(Socket src, Socket dst, boolean req) {
    int n;
    try {
      InputStream is = src.getInputStream();
      OutputStream os = dst.getOutputStream();
      if (req) Thread.currentThread().setName("Front");
      else Thread.currentThread().setName("Rear");
      for (;;) {
        n = is.read();
        if (n == -1) {
          // simulate a FIN
          src.getInputStream().close();
          dst.getOutputStream().close();
          break;
        }
        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException ignore) { }
        os.write(n);
        os.flush();
      }
    } catch (IOException ioe) {
      try {
        src.close();
        dst.close();
      } catch (IOException ignore) { }
    }
  }

  private static void hexAnalyze(Socket src, Socket dst, boolean req) {
    if (dFlag) log("INI " + src + " > " + dst + "\n", false);
    try {
      InputStream is = src.getInputStream();
      OutputStream os = dst.getOutputStream();
      for (;;) {
        byte[] data = new byte[16];
        int n = is.read(data);

        synchronized(Proxy.class) {
          if (n == -1) {
            // simulate a FIN
            if (dFlag) log("FIN " + src + " > " + dst + "\n", req);
            is.close();
            os.close();
            break;
          }

          byte[] buff = new byte[n];
          System.arraycopy(data, 0, buff, 0, n);

          // print the hex values
          for (int i = 0; i < n; i++) {
            String x = Integer.toHexString(buff[i] & 0xff);
            if (x.length() == 1) x = "0" + x;
            log(x + " ", req);
            if ((buff[i] < ' ') || (buff[i] > '~')) buff[i] = '.';
          }
          if (n != 16) {
            for (int i = n; i < 16; i++)
              log("   ", req);
          }
          log("   " + new String(buff, 0, n) + "\n", req);
          if (n != 16) log("\n\n", req);
        }

        os.write(data, 0, n);
      }
    } catch (IOException ioe) {
      if (dFlag) log("ERR " + src + " > " + dst + "\n", req);
      //if (dFlag) ioe.printStackTrace();
      try {
        src.close();
        dst.close();
      } catch (IOException ignore) { }
    }

  }

  public static void startProxy(String listenHost, int listenPort,
    String host, int port) {
    try {
      InetAddress server = InetAddress.getByName(host);
      InetAddress listener = InetAddress.getByName(listenHost);
      ServerSocket ssock = new ServerSocket(listenPort, 10, listener);
      for (;;) {
        Socket frontSock = ssock.accept();
        Socket rearSock = new Socket(server, port);

        Object obj = null;
        if (analyzerClass != null) obj = analyzerClass.newInstance();
        Proxy request = new Proxy(frontSock, rearSock, true, obj);
        Proxy response = new Proxy(rearSock, frontSock, false, obj);

        request.start();
        response.start();
      }
    } catch (Exception ioe) {
      ioe.printStackTrace();
    }
  }

  private static void printUsage() {
    System.err.println("Usage: java Proxy [-x|-f|-d|-y delay] [-p class] " +
      "listenHost:listenPort serverHost:serverPort");
    System.exit(1);
  }

  private static String[] parseHostPort(String s) {
    String[] ret = new String[2];
    int pos = s.indexOf(':');
    if (pos < 0) {
      ret[0] = s;
      ret[1] = "localhost";
    } else {
      ret[0] = s.substring(0, pos);
      ret[1] = s.substring(pos+1, s.length());
    }
    return ret;
  }

  public static void main(String[] args) throws Exception {
    int listenPort = 80;
    String listenHost = "localhost";
    int serverPort = 80;
    String serverHost = "www.gnu.org";

    for (int i = 0; i < args.length; i++) {
      if (args[i].toLowerCase().equals("-x")) {
        xFlag = true;
      } else if (args[i].toLowerCase().equals("-f")) {
        fFlag = true;
      } else if (args[i].toLowerCase().equals("-d")) {
        dFlag = true;
      } else if (args[i].toLowerCase().equals("-y")) {
        if (++i == args.length) printUsage();
        delayMillis = Integer.parseInt(args[i]);
      /*
      } else if (args[i].toLowerCase().equals("-p")) {
        if (++i == args.length) printUsage();
        analyzerClass = Class.forName(args[i]);
        reqMethod = analyzerClass.getMethod("requestAnalyze",
          new Class[] { Socket.class,
                        Socket.class });
        resMethod = analyzerClass.getMethod("responseAnalyze",
          new Class[] { Socket.class,
                        Socket.class });
      */
      } else if (args[i].toLowerCase().equals("-help")) {
        printUsage();
        System.exit(1);
      } else {
        if (args.length != i + 2) printUsage();

        String[] pair = parseHostPort(args[i]);
        listenHost = pair[0];
        listenPort = Integer.parseInt(pair[1]);

        pair = parseHostPort(args[++i]);
        serverHost = pair[0];
        serverPort = Integer.parseInt(pair[1]);

        break;
      }
    }

    if (dFlag)
      System.out.println("Client <=> " + listenHost + ":" +
          listenPort + " <=> " + serverHost + ":" + serverPort);
    startProxy(listenHost, listenPort, serverHost, serverPort);
    return;
  }

}

