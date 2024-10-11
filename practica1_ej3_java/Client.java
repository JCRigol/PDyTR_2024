/*
 * Client.java
 * Just sends stdin read data to and receives back some data from the server
 *
 * usage:
 * java Client serverhostname port
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.zip.CRC32;
import java.util.Locale;

public class Client
{
  private static final String CSV_FILE = "times.csv";

  public static void main(String[] args) throws IOException
  {
    /* Check the number of command line parameters */
    if ((args.length != 3) || (Integer.valueOf(args[1]) <= 0) || (Integer.valueOf(args[2]) <= 0) )
    {
      System.out.println("3 arguments needed: serverhostname port buffsize");
      System.exit(1);
    }

    /* The socket to connect to the echo server */
    Socket socketwithserver = null;

    try /* Connection with the server */
    { 
      socketwithserver = new Socket(args[0], Integer.valueOf(args[1]));
    }
    catch (Exception e)
    {
      System.out.println("ERROR connecting");
      System.exit(1);
    } 

    /* Streams from/to server */
    DataInputStream  fromserver;
    DataOutputStream toserver;

    /* Streams for I/O through the connected socket */
    fromserver = new DataInputStream(socketwithserver.getInputStream());
    toserver   = new DataOutputStream(socketwithserver.getOutputStream());

    /* Buffer to use with communications (and its length) */
    int buff_size = Integer.valueOf(args[2]);
    byte[] buffer_send = new byte[buff_size];
    byte[] buffer_recv;

    /* Time utils */
    Instant start_time, end_time;
    double total_time;

    /* Checksum utils
    CRC32 crc = new CRC32();
    long checksum;
    boolean done; */
    
    /* Get some input from user
    Console console  = System.console();
    String inputline = console.readLine("Please enter the message: "); */
    for (int i = 0; i < buff_size; i++) {
      buffer_send[i] = 'A';
    }

   // crc.update(buffer_send, 0, buff_size);
    //checksum = crc.getValue();
    System.out.println("Buffsize " + (buff_size) + ":\n");

    for (int i = 0; i < 100; i++) {
      //done = false;

     // while (!done) {
        /* time0 goes here */
        start_time = Instant.now();

        /* Send buff size to server, receive ack */
        byte[] buff_size_barray = ByteBuffer.allocate(4).putInt(buff_size).array();
        toserver.write(buff_size_barray, 0, buff_size_barray.length);
        
        buffer_recv = new byte[256];
        fromserver.read(buffer_recv);

        /* Send read data to server */
        toserver.write(buffer_send, 0, buffer_send.length);
        
        /* Recv data back from server (get space) */
        buffer_recv = new byte[256];
        fromserver.read(buffer_recv);

        /* Handle integrity verification
        byte[] checksum_barray = ByteBuffer.allocate(8).putLong(checksum).array();
        toserver.write(checksum_barray, 0, checksum_barray.length);

        done = fromserver.readBoolean(); */

        /* time1 goes here */
        end_time = Instant.now();
        long total_time_nanos = Duration.between(start_time, end_time).toNanos();
        total_time = (total_time_nanos / 1000000000.0) / 4;

        System.out.println("Iteration " + (i + 1) + ":");
        System.out.printf("Total time: %.9f seconds (%.0f nanoseconds)\n", total_time, (double) total_time_nanos);
        logToCSV(i, buff_size, total_time);
      //}
    }

    fromserver.close();
    toserver.close();
    socketwithserver.close();
  }

  private static void logToCSV(int iteration, int buff_size, double time) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
        // Write the data to the CSV
        writer.printf(Locale.US, "%d,%d,%.9f%n", buff_size, iteration + 1, time);
    } catch (IOException e) {
        System.err.println("Error writing to CSV: " + e.getMessage());
    }
  }
}
