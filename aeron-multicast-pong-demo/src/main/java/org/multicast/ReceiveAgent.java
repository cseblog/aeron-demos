package org.multicast;


import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class ReceiveAgent implements Agent {
    private final Subscription subscription;
    private final ShutdownSignalBarrier barrier;
    private final int sendCount;
    private int counter;

    public ReceiveAgent(final Subscription subscription, ShutdownSignalBarrier barrier, int sendCount) {
        this.subscription = subscription;
        this.barrier = barrier;
        this.sendCount = sendCount;
        this.counter = 0;
    }

    @Override
    public int doWork() throws Exception {
        subscription.poll(this::handler, 1000);
        return 0;
    }

    private void handler(DirectBuffer buffer, int offset, int length, Header header) {
        final int lastValue = buffer.getInt(offset);
//        System.out.println("Receive: counter: " + counter + " last value: " + lastValue);
        counter++;
        if (counter >= sendCount) {
            barrier.signal();
        }
    }
    @Override
    public String roleName() {
        return "receiver";
    }
}
