package prash.tools;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;

/* This utility class behaves as a browser pinging
 * a resource on a server. A status other than 200
 * is considered to be an invalid status
 *
 * 1xx - Informational  (100 - 101)
 * 2xx - Successful     (200 - 206)
 * 3xx - Redirection    (300 - 305)
 * 4xx - Client Error   (400 - 415)
 * 5xx - Server Error   (500 - 505)
 *
 */

public class Banger {
    ////////////////////////////////////////////////////
    // Defaults for arguments
    ////////////////////////////////////////////////////
    protected static String scheme = "http";
    protected static String host   = "localhost";
    protected static int port      = 7001;
    protected static String uri    = "/";
    protected static String method = "GET";
    protected static int tcount    = 1;
    protected static int iter      = 1;
    protected static boolean dFlag = false;
    protected static boolean fFlag = false;      // read request from file
    protected static boolean vFlag = false;
    protected static boolean gFlag = false;
    protected static boolean cFlag = false;      // cookie flag
    protected static boolean kFlag = false;      // keep-alive flag
    protected static long delay     = 0;          // delay between requests

    protected static long totalTime = 0;
    protected static long totalFail = 0;
    protected static long totalSockets = 0;
    protected static long totalBytesRecv = 0;

    protected static StringBuffer postContent;   // contents of POST operation
    protected static StringBuffer requestContent; // request from a file
    protected Client[] myClient = null;
    protected static String VALID_STATUS = "200";
    public static char CR = '\r';
    public static char LF = '\n';
    public static String CRLF = "\r\n";
    public static String VERSION_STRING = "Bulldog 2.0 alpha";
    public static int processedRequests = 0;
    public static int twoPercent = 0;
    public static long beginTime = -1;

    public void init() {
        myClient = new Client[tcount];
        for (int i=0; i<tcount; i++) myClient[i] = new Client(i);
    }

    public void starter() {
        for (int i=0; i<tcount; i++) myClient[i].start();
    }

    public void waiter() throws InterruptedException {
        for (int i=0; i<tcount; i++) myClient[i].join();
    }

    /* Main driver for the program
     */
    public static void main(String[] args) {
        for(int i=0; i< args.length; i++) {
            if (args[i].equalsIgnoreCase("-s")) {
                scheme = args[++i];
            } else if (args[i]. equalsIgnoreCase("-h")) {
                host = args[++i];
            } else if (args[i]. equalsIgnoreCase("-p")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i]. equalsIgnoreCase("-u")) {
                uri = args[++i];
                if (!uri.startsWith("/")) uri = "/" + uri;
            } else if (args[i]. equalsIgnoreCase("-m")) {
                method = args[++i].toUpperCase();
                if (method.equalsIgnoreCase("GET") ||
                    method.equalsIgnoreCase("HEAD")) {
                    // define methods to go through
                } else if (method.equalsIgnoreCase("POST")) {
                    try {
                        FileReader postFile = new FileReader("post.txt");
                        if (postFile != null) {
                            BufferedReader br = new BufferedReader(postFile);
                            postContent = new StringBuffer();
                            int c;
                            while((c = br.read()) != -1) {
                                postContent.append((char)c);
                            }
                        }
                    } catch (Exception fe) {
                        System.err.println("Error: post.txt not found in current directory");
                        fe.printStackTrace();
                    }
                } else {
                    System.err.println("Invalid method: " + method);
                    System.exit(1);
                }
            } else if (args[i]. equalsIgnoreCase("-t")) {
                tcount = Integer.parseInt(args[++i]);
            } else if (args[i]. equalsIgnoreCase("-i")) {
                iter = Integer.parseInt(args[++i]);
            } else if (args[i]. equalsIgnoreCase("-d")) {
                dFlag = true;
            } else if (args[i]. equalsIgnoreCase("-v")) {
                vFlag = true;
            } else if (args[i]. equalsIgnoreCase("-k")) {
                kFlag = true;
            } else if (args[i]. equalsIgnoreCase("-c")) {
                cFlag = true;
            } else if (args[i]. equalsIgnoreCase("-g")) {
                gFlag = true;
                vFlag = false;
                dFlag = false;
            } else if (args[i]. equalsIgnoreCase("-y")) {
                delay = Long.parseLong(args[++i]);
            } else if (args[i]. equalsIgnoreCase("-f")) {
                fFlag = true;
                try {
                    // open the file to write
                    FileReader reqReader = new FileReader(args[++i]);
                    if (reqReader != null) {
                        BufferedReader br = new BufferedReader(reqReader);
                        requestContent = new StringBuffer();
                        int c;
                        while((c = br.read()) != -1) {
                            requestContent.append((char)c);
                        }
                    }

                } catch (FileNotFoundException ioe) {
                    System.err.println("[Banger] request file not found.");
                    System.exit(1);
               } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Error: unknown argument - " + args[i]);
                usage();
                System.exit(1);
            }
        }
        debug("[scheme=" + scheme + "] [host=" + host + "] [port=" + port +
              "] [uri=" + uri + "] [method=" + method + "] [threads=" + tcount +
              "] [iterations=" + iter + "] [debug=" + dFlag + "] [file=" + fFlag +
              "] [vFlag=" + vFlag + "] [kFlag=" + kFlag + "] [delay=" + delay +
              "] [cFlag=" + cFlag + "]");

        //System.out.println("------------------------------------------------------------");
        // Initialize the data structure
        //progressBar();
        Banger myBanger = new Banger();
        // Initialize the threads
        myBanger.init();

        beginTime = System.currentTimeMillis();
        // Start the threads
        myBanger.starter();
        // Wait for threads to die
        try {
            myBanger.waiter();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        totalTime = System.currentTimeMillis() - beginTime;

        // All threads dead.
        if (gFlag) System.out.println("");
        System.out.println("------------------------------------------------------------");
        System.out.println(iter*tcount + " requests took avg time: " +
            (float)totalTime/(float)(iter*tcount) + " ms Failed: " + totalFail +
            " total time: " + totalTime + " ms");
        System.out.println("Avg : " + ((float)totalTime)/((float)(iter)));
        System.out.println("Fail: " + totalFail);
        System.out.println("TPS : " + (float)(iter*tcount*1000)/(float)totalTime);
        System.out.println("BRPS : " + (float)totalBytesRecv/(float)totalTime);
        System.out.println("SOCK : " + totalSockets);
    }

    public static void debug(String s) {
        if (dFlag) System.err.println("[D] " + s);
    }

    public static void say(String s) {
        if (!gFlag) System.out.println(s);
    }

    public static void progressBar() {
        if (gFlag) {
            System.out.println("0    10   20   30   40   50   60   70   80   90  100");
            System.out.println("|    |    |    |    |    |    |    |    |    |    |");
            System.out.print("x");
            twoPercent = (int)(0.02 * iter * tcount);
        }
    }

    public static void progress() {
        if (gFlag) {
            processedRequests++;
            if (tcount*iter < 100) {
                if (processedRequests == 2) {
                    System.out.print("x");
                    processedRequests = 0;
                }
                return;
            } else if (processedRequests == twoPercent) {
                System.out.print("x");
                processedRequests = 0;
            }
        }
    }

    public static void progressX() {
      if (gFlag) {
        processedRequests++;
        if (processedRequests % 10 == 0) {
          System.out.print("\rPro:" + processedRequests + " TPS:" +
              (float)(processedRequests*1000)/(float)(System.currentTimeMillis() - beginTime));
        }
      }
    }

    private static void streamWriter(OutputStream os, String s) throws IOException {
        if (vFlag) System.out.print(s);
        os.write(s.getBytes());
    }

    private static int streamReader(InputStream is) throws IOException {
        int c = is.read();
        if (vFlag && c != -1) System.out.print((char)c);
        return c;
    }

    private static int blockRead(InputStream is, int cl) throws IOException {
      byte[] b = new byte[4 * 1024];
      int totalRead = 0;
      int c;
      while ((c = is.read(b)) > 0) {
        totalRead += c;
        if (cl > 0 && totalRead >= cl) break;
      }
      return totalRead;
    }

    public static void sendRequest(OutputStream os, boolean ka, String cookie) throws IOException {
        if (fFlag){
            streamWriter(os, requestContent.toString());
            os.flush();
            return;
        }
        streamWriter(os, method.toUpperCase() + " " + uri + " " + "HTTP/1.1" + CRLF);
        streamWriter(os, "User-Agent: " + VERSION_STRING + CRLF);
        streamWriter(os, "Host: " + host + ":" + port + CRLF);

        if (method.toUpperCase().equals("POST"))
            streamWriter(os, "Content-Length:" + postContent.length() + CRLF);
        if (!ka)
            streamWriter(os, "Connection: Close" + CRLF);
        else
            streamWriter(os, "Connection: Keep-Alive" + CRLF);

        if (cFlag && cookie != null && cookie.length() != 0)
            streamWriter(os, "Cookie: " + cookie + CRLF);
        streamWriter(os, CRLF);
        if (method.toUpperCase().equals("POST"))
            streamWriter(os, postContent.toString());
        os.flush();
    }

    /**
     * Utility method to extract Headers. Reads such that the
     * next byte read after this method will be the first byte
     * of the body. Read till you get an empty line.
     *
     * @return status
     * @throws IOException RuntimeException
     */
     private static int readHeaders(InputStream is, List hdrs, List vals)
            throws IOException, RuntimeException {
        int r;
        int status = -1;
        StringBuffer line = new StringBuffer("");
        // parse the first line .. read till \r\n
        while((r = streamReader(is)) != LF)  {
            if (r == -1) throw new EOFException("Stream closed by peer");
            if (r != CR) line.append((char)r);
        }

        try {
            status = Integer.parseInt(line.toString().substring(9,12));
        } catch (Exception e) {
            throw new RuntimeException("Exception parsing firstline");
        }
        // parse the headers
        for(;;) {
            // read one header
            StringBuffer hdrLine = new StringBuffer("");
            while((r = streamReader(is)) != LF) {
                if (r == -1) throw new EOFException("Stream closed by peer");
                if (r != CR) hdrLine.append((char)r);
            }
            // if you get an empty line then getout.
            if (hdrLine.length() == 0)
                break;
            else
                parseHeader(hdrLine.toString(), hdrs, vals);
        }
        return status;
    }

    private static String getHeaderValue(String name, List hdrs, List vals) {
        for(int i=0; i<hdrs.size(); i++) {
            if (name.equalsIgnoreCase((String)hdrs.get(i)))
                return (String)vals.get(i);
        }
        return null;
    }

    /**
     * Parses each header line.
     */
    private static void parseHeader(String hdr, List headers, List values) {
        if (hdr.trim().length() == 0) return;
        int colpos = hdr.indexOf(':');
        if (colpos != -1 || colpos == hdr.length()-1) {
            headers.add(hdr.substring(0, colpos).trim());
            values.add(hdr.substring(colpos+1).trim());
        } else {
            // if there is no : in the header, then set value to ""
            headers.add(hdr);
            values.add("");
        }
    }

    /**
     * Reads the body.
     * If ContentLength is present, reads till its satisfied and keep the socket open
     * If no ContentLength, read using ChunkedEncoding and keep the socket open
     * If neither is present, it HANGS
     */
    private static int readBody(InputStream is, int cl, boolean ka, boolean ce)
                throws IOException, RuntimeException {
        if (ce) {
            return readChunkedBody(is);
        }
        // read till content-length is satisfied
        // if no content-length, then check keep-alive
        if (!vFlag) {
          return blockRead(is, cl);
        }

        int n = 0;
        int r;
        if (cl >= 0) {
            for(r=0; r<cl; r++) {
                streamReader(is);
            }
            n = cl;
        } else if (!ka) {
            while((r = streamReader(is)) != -1) {
              n++;
            }
        } else {
            while((r = streamReader(is)) != -1) {
              n++;
            }
        }
        return n;
    }

    /**
     * Reads in chunked body. Only used if Transfer-Encoding: Chunked
     */
    private static int readChunkedBody(InputStream is) throws IOException {
        int n = 4;
        int chunksize = readChunkSize(is);
        while(chunksize != 0) {
            n += (chunksize + 2);
            for(int i=0; i<chunksize; i++) {
                streamReader(is);
            }
            // chunk data ends with CRLF remove them
            if ((char)is.read() != LF) {
                streamReader(is);
            }
            chunksize = readChunkSize(is);
        }
        // ok got a 0 chunk.. read till we get a CRLF
        for(;;) {
            n += 2;
            if((char)streamReader(is) == CR &&
                    (char)streamReader(is) == LF)
               break;
        }
        return n;
    }

    /**
     * Reads a chunk size and returns it. Follows RFC2068 for
     * reading chunked data.
     *
     * @return          The size of the chunk
     */
    private static int readChunkSize(InputStream is) throws IOException, RuntimeException {
        int chunksize = 0;
        StringBuffer sb = new StringBuffer();
        char r;
        while ((r = (char)streamReader(is)) != LF) {
            boolean s = false;
            if (r == ';') s = true;
            if (r != CR && !s) sb.append(r);
        }
        try {
            chunksize = Integer.parseInt(sb.toString().trim(), 16);
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Problem parsing chunk length");
        }
        return chunksize;
    }

    /* help and usage
     */
    private static void usage() {
        System.err.println("Usage: java Banger options \n" +
                           "\t -s scheme.               Default http\n" +
                           "\t -h host.                 Default localhost\n" +
                           "\t -p port.                 Default 7001\n" +
                           "\t -u URI.                  Default / \n" +
                           "\t -m method.               Default GET; for POST, " +
                                                        "post.txt must exist\n" +
                           "\t -t thread count.         Default 1 \n" +
                           "\t -i iteration count.      Default 1 \n" +
                           "\t -d debug info to stdout. Default OFF \n" +
                           "\t -v verbose req/res.      Default OFF \n" +
                           "\t -k use Keep-Alive        Default OFF \n" +
                           "\t -c send Cookies          Default OFF \n" +
                           "\t -y delay in millis.      Default 0 \n" +
                           "\t -g show progress bar.    Default OFF \n" +
                           "\t -f read req from file    \n");
    }

    protected class Client extends Thread {

        private int id = 0;
        private long clientTotalTime = 0;
        private int failures = 0;
        private boolean keepAlive = false;
        private String cookie = "";
        private Socket sock = null;
        private boolean connected = false;
        private long bytesRecv = 0L;
        private int sockCount = 0;

        // constructer
        public Client(int id) {
            this.id = id;
            if (kFlag) keepAlive = true;
        }

        @Override
        public void run() {
            // run the client the iterator times
            int status = 0;
            long responseTime = -1;
            for (int cnt=0; cnt<iter; cnt++) {
                responseTime = System.currentTimeMillis();
                try {
                    resetSocket(false);
                    try {
                      status = talk(cnt);
                    } catch(EOFException eof) {
                      // retry on new socket
                      say("Client[" + id + "] server didn't read request. Retrying again...");
                      resetSocket(true);
                      status = talk(cnt);
                    }
                    if (dFlag & status > 399) {
                        say("Client[" + id + "] Iteration[" + iter +
                            "] Failed - status:" + status);
                    }
                } catch (IOException ioe) {
                    if (dFlag) ioe.printStackTrace();
                    this.failures++;
                    say("Client[" + id + "] Iteration[" + iter + "] Failed - " + ioe.getMessage());
                    try {
                        resetSocket(false);
                    } catch (IOException ig) {
                        if (dFlag) ig.printStackTrace();
                    }
                }
                responseTime = System.currentTimeMillis() - responseTime;
                clientTotalTime += responseTime;
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    if (dFlag) ie.printStackTrace();
                }
                progressX();
            }
            say("Client[" + id + "] -> [" + iter + "] Total[" + clientTotalTime +
                "] Fail[" + failures + "] Avg[" + (float)clientTotalTime/(float)iter +
                "] TPS[" + (float)(iter*1000)/(float)clientTotalTime + "]");
            totalFail += this.failures;
            totalBytesRecv += bytesRecv;
            totalSockets += sockCount;
        }

        private void resetSocket(boolean force) throws IOException {
            if (!force && kFlag && this.keepAlive && connected) return;
            if (this.connected) {
                this.sock.close();
                this.connected = false;
            }
            sockCount++;
            this.sock = new Socket(InetAddress.getByName(host), port);
            if (kFlag && this.keepAlive) {
                this.keepAlive = true;
                this.sock.setKeepAlive(true);
            }
            this.connected = true;
            debug("Client[" + id + "] Socket recycle localPort[" + sock.getLocalPort() +
                        "] remotePort[" + sock.getPort() + "]");
        }

        public int talk(int iteration) throws IOException {
            // assume the socket is connected
            // get streams
            InputStream sockRead = this.sock.getInputStream();
            OutputStream sockWrite = this.sock.getOutputStream();
            ArrayList hdrs = new ArrayList();
            ArrayList vals = new ArrayList();
            boolean chk = false;
            int cl = -1;

            if (vFlag)
                System.out.println(".......................... request ........................");
            // send a request
            sendRequest(sockWrite, this.keepAlive, this.cookie);
            if (vFlag)
                System.out.println(".......................... response ........................");
            // read headers
            int status = readHeaders(sockRead, hdrs, vals);

            if ("Close".equalsIgnoreCase(getHeaderValue("Connection", hdrs, vals)))
                this.keepAlive = false;
            else
                this.keepAlive = true;

            String ckie = getHeaderValue("Set-Cookie", hdrs, vals);
            if (ckie != null)
              this.cookie = ckie;

            int idx = this.cookie.indexOf(";");
            if (idx != -1) {
              this.cookie = this.cookie.substring(0, idx);
            }

            if("Chunked".equalsIgnoreCase(getHeaderValue("Transfer-Encoding", hdrs, vals)))
                chk = true;

            try {
                cl = Integer.parseInt(getHeaderValue("Content-Length", hdrs, vals));
            } catch (NumberFormatException nfe) {
                cl = -1;
            }

            // read the body
            bytesRecv += readBody(sockRead, cl, this.keepAlive, chk);
            //sockRead.close();
            //sockWrite.close();

            if (vFlag)
                System.out.println("............................................................");
            debug("Client[" + id + "][" + iteration + "] Status [" + status +
                        "] LocalPort [" + this.sock.getLocalPort() + "] KeepAlive [" + keepAlive +
                        "]");
            return status;
        }

    }
}

