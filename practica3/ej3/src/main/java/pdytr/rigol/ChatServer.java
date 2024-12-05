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

    public ChatServer(int port, String filePath, String logPath) {
        this.server = ServerBuilder.forPort(port)
                .addService(new ChatServerImpl(filePath, logPath))
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
        ChatServer server = new ChatServer(8080, "history","logs");
        server.start();
        server.stop();
    }

    private class ChatServerImpl extends ChatServiceGrpc.ChatServiceImplBase {
        private final ChatHistory chatHistory;
        private final ServerLogging serverLogger;

        public ChatServerImpl(String filePath, String logPath) {
            this.chatHistory = new ChatHistory(filePath);
            this.serverLogger = new ServerLogging(logPath);
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

        private class ServerLogging {
            private final String filePath;

            public ServerLogging(String filePath) {
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

        @Override
        public void join(JoinRequest request, StreamObserver<ChatEvent> responseObserver) {
            String username = request.getUsername();
            //Debug
            String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[38;5;204m" + "JOIN" + "\u001B[33m" + " => " + "\u001B[95m" + username + "\u001B[0m";
            serverLogger.log(log);

            ServerCallStreamObserver<ChatEvent> serverObserver = (ServerCallStreamObserver<ChatEvent>) responseObserver;
            serverObserver.setOnCancelHandler(() -> {
                if (clientList.containsKey(username)) {
                    //Debug
                    String log2 = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[38;5;201m" + "OBSERVER" + "\u001B[33m" + " :: " + "\u001B[31m" + "SuddenLeave" + "\u001B[33m" + " => " + "\u001B[95m" + username + "\u001B[0m";
                    serverLogger.log(log2);

                    clientLocks.remove(clientList.remove(username));

                    broadcastEvent(
                            chatEventBuilder(MessageRequest.newBuilder()
                                    .setUsername("Server")
                                    .setMessage(username + " disconnected")
                                    .build()),
                            "Server"
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
            String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[38;5;208m" + "PROBE" + "\u001B[33m" + " => " + "\u001B[95m" + username + "\u001B[0m";
            serverLogger.log(log);

            if ((clientList.containsKey(username)) && (!clientList.get(username).isCancelled())) {
                responseObserver.onNext(ServerEvent.newBuilder().setStatus(true).build());
                event(
                        clientList.get(username),
                        serverEventBuilder("Welcome!"),
                        request.getUsername()
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
                String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[94m" + "LEAVE" + "\u001B[33m" + " => " + "\u001B[95m" + username + "\u001B[0m";
                serverLogger.log(log);

                clientLocks.get(clientList.get(username)).lock();
                try {
                    ServerCallStreamObserver<ChatEvent> clientObserver = clientList.remove(username);
                    clientLocks.get(clientObserver).unlock();

                    event(
                            clientObserver,
                            serverEventBuilder("Goodbye!"),
                            request.getUsername()
                    );

                    clientLocks.remove(clientObserver);

                    responseObserver.onNext(ServerEvent.newBuilder().setStatus(true).build());
                    broadcastEvent(
                            chatEventBuilder(MessageRequest.newBuilder()
                                    .setUsername("Server")
                                    .setMessage(username + " disconnected")
                                    .build()),
                            "Server"
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
            String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[96m" + "MESSAGE" + "\u001B[33m" + " => " + "\u001B[95m" + request.getUsername() + "\u001B[0m";
            serverLogger.log(log);

            broadcastEvent(
                    chatEventBuilder(request),
                    request.getUsername()
            );

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
            return ChatEvent.newBuilder().setMessage("\u001B[1m" + "\u001B[33m" + "Server: "  + "\u001B[31m" + message + "\u001B[0m").build();
        }

        private ChatEvent chatEventBuilder(MessageRequest request) {
            String message = "\u001B[1m" + "\u001B[33m" + "["  + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "\u001B[95m" + request.getUsername() + "\u001B[33m" + ": " + "\u001B[34m" + request.getMessage() + "\u001B[0m";
            chatHistory.appendMessage(message);

            return ChatEvent.newBuilder().setMessage(message).build();
        }

        private ChatEvent chatEventBuilder(MessageRequest request, Instant timestamp) {
            String message = "\u001B[1m" + "\u001B[33m" + "["  + "\u001B[36m" + timestamp.toString() + "\u001B[33m" + "] " + "\u001B[95m" + request.getUsername() + "\u001B[33m" + ": " + "\u001B[34m" + request.getMessage() + "\u001B[0m";
            chatHistory.appendMessage(message);

            return ChatEvent.newBuilder().setMessage(message).build();
        }

        private void event(ServerCallStreamObserver<ChatEvent> clientObserver, ChatEvent event, String receiver) {
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

            // Debug
            String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[38;5;126m" + "SERVER_MSG" + "\u001B[33m" + " => " + "\u001B[95m" + receiver + "\u001B[0m";
            serverLogger.log(log);
        }

        private void broadcastEvent(ChatEvent event, String sender) {
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

            // Debug
            String log = "\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + Instant.now().toString() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + "BROADCAST" + "\u001B[33m" + " => " + "\u001B[95m" + sender + "\u001B[0m";
            serverLogger.log(log);
        }
    }

}
