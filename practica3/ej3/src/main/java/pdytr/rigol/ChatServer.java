package pdytr.rigol;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

/**
 * Created by rayt on 5/16/16.
 */
public class ChatServer {
    public static void main(String[] args) throws InterruptedException, IOException {
        Server server = ServerBuilder.forPort(9090).addService(new ChatServiceImpl()).build();

        server.start();
        server.awaitTermination();
    }
}