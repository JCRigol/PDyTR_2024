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

public class Client
{
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
    long start_time, end_time, total_time;
    
    /* Get some input from user
    Console console  = System.console();
    String inputline = console.readLine("Please enter the message: "); */
    for (int i = 0; i < buff_size; i++) {
      buffer_send[i] = 'A';
    }

    for (int i = 0; i < 100; i++) {
      /* time0 goes here */
      start_time = System.nanoTime();

      /* Send buff size to server, receive ack */
      byte[] buff_size_barray = ByteBuffer.allocate(4).putInt(buff_size).array();
      toserver.write(buff_size_barray, 0, buff_size_barray.length);
      
      buffer_recv = new byte[256];
      fromserver.read(buffer_recv);

      System.out.println("Received ack");

      /* Send read data to server */
      toserver.write(buffer_send, 0, buffer_send.length);
      
      /* Recv data back from server (get space) */
      buffer_recv = new byte[256];
      fromserver.read(buffer_recv);

      /* time1 goes here */
      end_time = System.nanoTime();

      /* Show data received from server */
      String resp = new String(buffer_recv);
      System.out.println("Iteration " + (i + 1) + resp);

      total_time = end_time - start_time;
      System.out.println(total_time);
    }

    fromserver.close();
    toserver.close();
    socketwithserver.close();
  }
}
