package pdytr.rigol;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import pdytr.rigol.Experiment.*;
import pdytr.rigol.ExperimentServiceGrpc.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class ExperimentClient {
    public static void main(String[] args) throws InterruptedException {
        System.out.println(args[0] + args[1]);

        final int threadCount = Integer.parseInt(args[0]);
        final int repCount = Integer.parseInt(args[1]);

        final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8080").usePlaintext(true).build();
        final ExperimentServiceBlockingStub blockingStub = ExperimentServiceGrpc.newBlockingStub(channel);
        final ExperimentServiceStub asyncStub = ExperimentServiceGrpc.newStub(channel);

        final List<Long> responseTimes = new ArrayList<>();

        long startMeasure, elapsedTime;
        CountDownLatch latch;


        // Unary A Threaded Experiment
        latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(new ExperimentTask(blockingStub, responseTimes, latch, 1, repCount)).start();
        }

        latch.await();

        // Storage
        saveMeasurement(threadCount, repCount, responseTimes, "unaryTypeA_MT.csv");


        // Unary A Single Thread Experiment
        TimeStamp request = TimeStamp.getDefaultInstance();
        for (int i = 0; i < threadCount*repCount; i++) {
            startMeasure = System.nanoTime();
            blockingStub.unaryExperiment(request);
            elapsedTime = System.nanoTime() - startMeasure;
            responseTimes.add(elapsedTime);

            System.out.println("Unary (1_ST): " + elapsedTime);
        }

        // Storage
        saveMeasurement(threadCount, repCount, responseTimes, "unaryTypeA_ST.csv");


        // Unary B Threaded Experiment
        latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(new ExperimentTask(blockingStub, responseTimes, latch, 2, repCount)).start();
        }

        latch.await();

        // Storage
        saveMeasurement(threadCount, repCount, responseTimes, "unaryTypeB_MT.csv");


        // Unary B Single Thread Experiment
        for (int i = 0; i < threadCount*repCount; i++) {
            TimeStamp response = blockingStub.unaryExperiment(
                    TimeStamp.newBuilder().setStartTime(System.nanoTime()).build()
            );
            elapsedTime = System.nanoTime() - response.getStartTime();
            responseTimes.add(elapsedTime);

            System.out.println("Unary (2_ST): " + elapsedTime);
        }

        // Storage
        saveMeasurement(threadCount, repCount, responseTimes, "unaryTypeB_ST.csv");


        // Async Single Thread experiment (Threaded shouldn't be necessary)
        latch = new CountDownLatch(threadCount*repCount);
        CountDownLatch finalLatch = latch;
        StreamObserver<TimeStamp> requestObserver = asyncStub.asyncExperiment(new StreamObserver<TimeStamp>() {
            @Override
            public void onNext(TimeStamp timeStamp) {
                long totalTime = System.nanoTime() - timeStamp.getStartTime();

                synchronized (responseTimes) {
                    responseTimes.add(totalTime);
                }

                System.out.println("Async: " + totalTime);
                finalLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("Error: " + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("Done");
            }
        });

        try {
            for (int i = 0; i < threadCount*repCount; i++) {
                requestObserver.onNext(
                        TimeStamp.newBuilder().setStartTime(System.nanoTime()).build()
                );
            }
        } catch (RuntimeException e) {
            requestObserver.onError(e);
            e.printStackTrace();
        }

        latch.await();
        requestObserver.onCompleted();

        // Storage
        saveMeasurement(threadCount, repCount, responseTimes,"async.csv");

        // End experiment
        channel.shutdown();
    }

    private static void saveMeasurement(int threadCount, int repCount, List<Long> responseTimes, String fName) {
        File storage = new File(fName);

        try (FileOutputStream oFile = new FileOutputStream(storage, true)) {
            for (Long m : responseTimes) {
                String row = threadCount + "," + repCount + "," + m + "\n";
                oFile.write(row.getBytes());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        responseTimes.clear();
    }

    private static class ExperimentTask implements Runnable {
        private final ExperimentServiceBlockingStub blockingStub;
        private final List<Long> responseTimes;
        private final CountDownLatch latch;
        private final int type, repetitions;

        public ExperimentTask(ExperimentServiceBlockingStub blockingStub, List<Long> responseTimes, CountDownLatch latch, int type, int repetitions) {
            this.blockingStub = blockingStub;
            this.responseTimes = responseTimes;
            this.latch = latch;
            this.type = type;
            this.repetitions = repetitions;
        }

        @Override
        public void run() {
            switch (type) {
                case 1:
                    unaryTypeA();
                    break;
                case 2:
                    unaryTypeB();
                    break;
                default:
                    System.out.println("Error");
            }

        }

        private void unaryTypeA() {
            for (int i = 1; i <= repetitions; i++) {
                TimeStamp request = TimeStamp.getDefaultInstance();

                long startMeasure = System.nanoTime();
                Objects.requireNonNull(blockingStub).unaryExperiment(request);
                long elapsedTime = System.nanoTime() - startMeasure;

                synchronized (responseTimes) {
                    responseTimes.add(elapsedTime);
                }

                System.out.println("Unary (1_MT): " + elapsedTime);
            }

            latch.countDown();
        }

        private void unaryTypeB() {
            for (int i = 1; i <= repetitions; i++) {
                TimeStamp response = Objects.requireNonNull(blockingStub).unaryExperiment(
                        TimeStamp.newBuilder().setStartTime(System.nanoTime()).build()
                );
                long elapsedTime = System.nanoTime() - response.getStartTime();

                synchronized (responseTimes) {
                    responseTimes.add(elapsedTime);
                }

                System.out.println("Unary (2_MT): " + elapsedTime);
            }
            latch.countDown();
        }
    }

}
