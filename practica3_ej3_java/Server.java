/*
 * EchoServer.java
 * Just receives some data and sends back a "message" to a client
 *
 * Usage:
 * java Server port
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class Server
{
    public static void main(String[] args) throws IOException
    {
        /* Check the number of command line parameters */
        if ((args.length != 1) || (Integer.valueOf(args[0]) <= 0) )
        {
            System.out.println("1 arguments needed: port");
            System.exit(1);
        }

        /* The server socket */
        ServerSocket serverSocket = null;    
        try
        {
            serverSocket = new ServerSocket(Integer.valueOf(args[0]));
        } 
        catch (Exception e)
        {
            System.out.println("Error on server socket");
            System.exit(1);
        }
        /* Streams from/to client */
        DataInputStream fromclient;
        DataOutputStream toclient;

        /* Buffer to use with communications (and its length) */
        byte[] buffer, buff_size_barray;

        /* Fixed string to the client */
        String strresp = "I got your message";

        for (int i = 0; i < 6; i++) {
            /* The socket to be created on the connection with the client */
            Socket connected_socket = null;

            try /* To wait for a connection with a client */
            {
                connected_socket = serverSocket.accept();
            }
            catch (IOException e)
            {
                System.err.println("Error on Accept");
                System.exit(1);
            }

            /* Get the I/O streams from the connected socket */
            fromclient = new DataInputStream(connected_socket.getInputStream());
            toclient   = new DataOutputStream(connected_socket.getOutputStream());

            for (int j = 0; j < 100; j++) {
                /* Recv buff size from client */
                buff_size_barray = new byte[4];
                fromclient.read(buff_size_barray);

                /* Convert to int */
                int buff_size = ByteBuffer.wrap(buff_size_barray).getInt();
                System.out.println("Here is the buffer size: " +  buff_size);

                /* Send ack */
                buffer = strresp.getBytes();
                toclient.write(buffer, 0, buffer.length);

                /* Buffer */
                buffer = new byte[buff_size];

                int totalBytesRead = 0;
                while (totalBytesRead < buff_size) {
                    int bytesRead = fromclient.read(buffer, totalBytesRead, buff_size - totalBytesRead);
                    if (bytesRead == -1) {
                        break;
                    }
                    totalBytesRead += bytesRead;
                }

                System.out.println(buffer);

                /* Send ack */
                buffer = strresp.getBytes();
                toclient.write(buffer, 0, buffer.length);
            }
            
            /* Close everything related to the client connection */
            fromclient.close();
            toclient.close();
            connected_socket.close();
        }

        serverSocket.close();
    }
}
