package pdytr.rigol;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import pdytr.rigol.Experiment.*;
import pdytr.rigol.ExperimentServiceGrpc.*;

import java.io.IOException;

public class ExperimentServer {
    private final Server server;

    public ExperimentServer(int port) {
        this.server = ServerBuilder.forPort(port)
                .addService(new ExperimentServerImpl())
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
        ExperimentServer server = new ExperimentServer(8080);
        server.start();
        server.stop();
    }

    private static class ExperimentServerImpl extends ExperimentServiceGrpc.ExperimentServiceImplBase {
        @Override
        public void unaryExperiment(TimeStamp request, StreamObserver<TimeStamp> responseObserver) {
            responseObserver.onNext(request);
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<TimeStamp> asyncExperiment(final StreamObserver<TimeStamp> responseObserver) {
            return new StreamObserver<TimeStamp>() {
                @Override
                public void onNext(TimeStamp timeStamp) {
                    responseObserver.onNext(timeStamp);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }

}
