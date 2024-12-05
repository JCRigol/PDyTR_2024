package pdytr.rigol;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pdytr.rigol.Chat.*;
import pdytr.rigol.ChatServiceGrpc.*;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class ChatClient {
    // Seems like each client having a separate channel creates issues
    private static final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8080")
            .usePlaintext(true)
            .build();

    private final ChatServiceBlockingStub blockingStub;
    private final ChatServiceStub asyncStub;

    private CountDownLatch latch;

    private final ClientLogging logger;

    /*private static class ClientDisconnectedException extends RuntimeException {
        public ClientDisconnectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }*/

    public ChatClient(String logPath) {
        blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        asyncStub = ChatServiceGrpc.newStub(channel);
        logger = new ClientLogging(logPath);
    }

    private class ClientLogging {
        private final String filePath;

        public ClientLogging(String filePath) {
            this.filePath = filePath;
        }

        public synchronized void log(String message) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                writer.write(message);
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Random random = new Random();

        String username = args[0];
        ChatClient client = new ChatClient(username + "_logs");

        try {
            client.joinChat(username);

            Thread.sleep(random.nextInt(2000));
            client.sendMessage(username, "Hello from " + username);

            Thread.sleep(random.nextInt(500)); // Otro mensaje
            for (int i = 1; i <= 10; i++) {
                client.sendMessage(username, "Another message(" + i + ") from " + username);
                Thread.sleep(random.nextInt(500));
                if (username.equals("CGPT")) {
                    client.shutdown();
                } else if (username.equals("Barbara") && i == 4) {
                    client.getChatHistory();
                }
            }

            Thread.sleep(random.nextInt(2000));
            client.leaveChat(username);
        } catch (StatusRuntimeException e) {
            client.logger.log("\u001B[1m" + "\u001B[31m" + "DESCONEXIÓN ESPONTÁNEA" + "\u001B[0m");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void joinChat(String username) throws InterruptedException {
        latch = new CountDownLatch(1);
        subscribeToChat(username);

        ProbeRequest request = ProbeRequest.newBuilder().setUsername(username).build();
        ServerEvent response = blockingStub.probe(request);

        while (!response.getStatus()) {
            Thread.sleep(50);
            response = blockingStub.probe(request);
        }

        latch.countDown();
    }

    private void subscribeToChat(String username) {
        JoinRequest request = JoinRequest.newBuilder().setUsername(username).build();

        asyncStub.join(request, new StreamObserver<ChatEvent>() {
            @Override
            public void onNext(ChatEvent event) {
                logger.log(event.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                //Supposedly I should handle the client disconnection here. For simulation purposes, obviously
                //Handle it by.... shutting down the client instance??? Can Java even do that?
                logger.log("ERROR: " + t.getMessage());
                latch = new CountDownLatch(1);
            }

            @Override
            public void onCompleted() {
                logger.log("Done");
            }
        });
    }

    public void leaveChat(String username) throws InterruptedException {
        LeaveRequest request = LeaveRequest.newBuilder().setUsername(username).build();
        ServerEvent response = blockingStub.leave(request);

        while (!response.getStatus()) {
            Thread.sleep(50);
            response = blockingStub.leave(request);
        }

        shutdown();
    }

    public void sendMessage(String username, String message) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        MessageRequest request = MessageRequest.newBuilder()
                .setUsername(username)
                .setMessage(message)
                .build();

        boolean success = blockingStub.sendMessage(request).getStatus();
        while (!success) {
            success = blockingStub.sendMessage(request).getStatus();
        }
    }

    public void getChatHistory() {
        HistoryRequest request = HistoryRequest.newBuilder().setCommand("/historial").build();

        asyncStub.getHistory(request, new StreamObserver<FileChunk>() {
            private FileOutputStream fileOutputStream;

            @Override
            public void onNext(FileChunk fileChunk) {
                try {
                    if (fileOutputStream == null)
                        fileOutputStream = new FileOutputStream("chat_history.txt");
                    fileOutputStream.write(fileChunk.getContent().toByteArray());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log("ERROR: " + t.getMessage());
                closeFile();
            }

            @Override
            public void onCompleted() {
                logger.log("\u001B[1m" + "\u001B[31m" + "HISTORY DOWNLOAD DONE" + "\u001B[0m");
                closeFile();
            }

            private void closeFile() {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }

    public void shutdown() {
        channel.shutdown();
    }

}
