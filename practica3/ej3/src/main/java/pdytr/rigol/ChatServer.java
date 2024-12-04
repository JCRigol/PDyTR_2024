package pdytr.rigol;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import pdytr.rigol.Chat.*;
import pdytr.rigol.ChatServiceGrpc.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class ChatServer {
    private final ConcurrentMap<String, ServerCallStreamObserver<ChatEvent>> clientList = new ConcurrentHashMap<>();
    private final ConcurrentMap<ServerCallStreamObserver<ChatEvent>, ReentrantLock> clientLocks = new ConcurrentHashMap<>();

    private final Server server;

    public ChatServer(int port, String filePath) {
        this.server = ServerBuilder.forPort(port)
            .addService(new ChatServerImpl(filePath))
            .build();
    }

    public void start() throws IOException {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
    }

    public void stop() throws InterruptedException {
        server.awaitTermination();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ChatServer server = new ChatServer(8080, "logs");
        server.start();
        server.stop();
    }

    private class ChatServerImpl extends ChatServiceGrpc.ChatServiceImplBase {
        private final ChatHistory chatHistory;
        private final CountDownLatch concurrencyTestingLatch = new CountDownLatch(4);

        public ChatServerImpl(String filePath) {
            this.chatHistory = new ChatHistory(filePath);
        }

        private class ChatHistory {
            private final String filePath;

            public ChatHistory(String filePath) {
                this.filePath = filePath;
            }

            public synchronized void appendMessage(String message) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                    writer.write(message);
                    writer.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void join(JoinRequest request, StreamObserver<ChatEvent> responseObserver) {
            String username = request.getUsername();
            //Debug
            System.out.println("Joined: " + username + " " + Instant.now().toString());

            ServerCallStreamObserver<ChatEvent> serverObserver = (ServerCallStreamObserver<ChatEvent>) responseObserver;
            serverObserver.setOnCancelHandler(() -> {
                if (clientList.containsKey(username)) {
                    //Debug
                    System.out.println("Leave(sudden): " + username + " " + Instant.now().toString());
                    clientLocks.remove(clientList.remove(username));

                    broadcastEvent(
                            chatEventBuilder(MessageRequest.newBuilder()
                                    .setUsername("Server")
                                    .setMessage(username + " disconnected")
                                    .build())
                    );
                }
            });

            clientLocks.computeIfAbsent(serverObserver, k -> new ReentrantLock());
            clientList.put(username, serverObserver);
        }

        @Override
        public void probe(ProbeRequest request, StreamObserver<ServerEvent> responseObserver) {
            String username = request.getUsername();
            //Debug
            System.out.println("Probe: " + username + " " + Instant.now().toString());

            if ((clientList.containsKey(username)) && (!clientList.get(username).isCancelled())) {
                responseObserver.onNext(ServerEvent.newBuilder().setStatus(true).build());
                event(
                        clientList.get(username),
                        serverEventBuilder("Welcome!")
                );
            }
            else
                responseObserver.onNext(ServerEvent.newBuilder().setStatus(false).build());
            responseObserver.onCompleted();
        }

        @Override
        public void leave(LeaveRequest request, StreamObserver<ServerEvent> responseObserver) {
            String username = request.getUsername();

            if ((clientList.containsKey(username)) && (!clientList.get(username).isCancelled())) {
                //Debug
                System.out.println("Leave: " + username + " " + Instant.now().toString());

                clientLocks.get(clientList.get(username)).lock();
                try {
                    ServerCallStreamObserver<ChatEvent> clientObserver = clientList.remove(username);
                    clientLocks.get(clientObserver).unlock();

                    event(
                            clientObserver,
                            serverEventBuilder("Goodbye!")
                    );

                    clientLocks.remove(clientObserver);

                    responseObserver.onNext(ServerEvent.newBuilder().setStatus(true).build());
                    broadcastEvent(
                            chatEventBuilder(MessageRequest.newBuilder()
                                    .setUsername("Server")
                                    .setMessage(username + " disconnected")
                                    .build())
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else
                responseObserver.onNext(ServerEvent.newBuilder().setStatus(false).build());
            responseObserver.onCompleted();
        }

        @Override
        public void sendMessage(MessageRequest request, StreamObserver<ServerEvent> responseObserver) {
            //Debug
            System.out.println("Message: " + request.getUsername() + " " + Instant.now().toString());

            //First 4 messages from clients should be concurrent, since threads release from latch at around the same time
            //And go into race conditions
            if (concurrencyTestingLatch.getCount() > 0) {
                try {
                    concurrencyTestingLatch.countDown();
                    concurrencyTestingLatch.await();

                    Instant timestamp = Instant.now();

                    broadcastEvent(
                            chatEventBuilder(request, timestamp)
                    );
                } catch (InterruptedException e) {
                    responseObserver.onNext(ServerEvent.newBuilder().setStatus(false).build());
                }
            } else {
                broadcastEvent(
                        chatEventBuilder(request)
                );
            }

            responseObserver.onNext(ServerEvent.newBuilder().setStatus(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void getHistory(HistoryRequest request, StreamObserver<FileChunk> responseObserver) {
            File original = new File(chatHistory.filePath);
            File copy = new File(chatHistory.filePath + "_temp");

            try {
                Files.copy(original.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Error on file read")
                        .asRuntimeException());
                return;
            }

            try (InputStream inputStream = Files.newInputStream(copy.toPath())) {
                byte[] buffer = new byte[1024*1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    FileChunk chunk = FileChunk.newBuilder()
                            .setContent(ByteString.copyFrom(buffer, 0, bytesRead))
                            .build();
                    responseObserver.onNext(chunk);
                }

                responseObserver.onCompleted();
            } catch (IOException e) {
                responseObserver.onError(Status.INTERNAL
                                .withDescription("Error on file read")
                                .asRuntimeException());
            } finally {
                copy.delete();
            }
        }

        private ChatEvent serverEventBuilder(String message) {
            return ChatEvent.newBuilder().setMessage("Server: " + message).build();
        }

        private ChatEvent chatEventBuilder(MessageRequest request) {
            String message = "[" + Instant.now().toString() + "] " + request.getUsername() + ": " + request.getMessage();
            chatHistory.appendMessage(message);

            return ChatEvent.newBuilder().setMessage(message).build();
        }

        private ChatEvent chatEventBuilder(MessageRequest request, Instant timestamp) {
            String message = "[" + timestamp.toString() + "] " + request.getUsername() + ": " + request.getMessage();
            chatHistory.appendMessage(message);

            return ChatEvent.newBuilder().setMessage(message).build();
        }

        private void event(ServerCallStreamObserver<ChatEvent> clientObserver, ChatEvent event) {
            if (!clientObserver.isCancelled()) {
                clientLocks.get(clientObserver).lock();

                try {
                    clientObserver.onNext(event);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                } finally {
                    clientLocks.get(clientObserver).unlock();
                }
            }
        }

        private void broadcastEvent(ChatEvent event) {
            clientList.forEach((username, clientObserver) -> {
                if (!clientObserver.isCancelled()) {

                    clientLocks.get(clientObserver).lock();
                    try {
                        clientObserver.onNext(event);
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    } finally {
                        clientLocks.get(clientObserver).unlock();
                    }
                }
            });
        }
    }

}
