package org.example;

import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class MediaDriver {

    //Start Media Driver sepperately
    public static void main(String[] args) {
        final io.aeron.driver.MediaDriver.Context ctx = new io.aeron.driver.MediaDriver.Context()
                .termBufferSparseFile(false)
                .useWindowsHighResTimer(true)
                .threadingMode(ThreadingMode.DEDICATED)
                .conductorIdleStrategy(BusySpinIdleStrategy.INSTANCE)
                .receiverIdleStrategy(NoOpIdleStrategy.INSTANCE)
                .senderIdleStrategy(NoOpIdleStrategy.INSTANCE);

        try (io.aeron.driver.MediaDriver ignored = io.aeron.driver.MediaDriver.launch(ctx)) {
            new ShutdownSignalBarrier().await();
            System.out.println("Shutdown Driver...");
        }
    }
}


