package org.multicast;

import io.aeron.Publication;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class SendAgent implements Agent
{
    private final Publication publication;
    private final int sendCount;
    private final UnsafeBuffer unsafeBuffer;
    private int currentCountItem = 1;
    private ShutdownSignalBarrier barrier;

    public SendAgent(final Publication publication, int sendCount, ShutdownSignalBarrier barrier)
    {
        this.publication = publication;
        this.sendCount = sendCount;
        this.unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(64));
        this.barrier = barrier;
        unsafeBuffer.putInt(0, currentCountItem);
    }

    @Override
    public int doWork() throws InterruptedException {
        if (currentCountItem > sendCount)
        {
            barrier.signal();
            return 0;
        }

        if (publication.isConnected())
        {
            if (publication.offer(unsafeBuffer) > 0)
            {
                currentCountItem += 1;
                unsafeBuffer.putInt(0, currentCountItem);
//                System.out.println("Sending..." + currentCountItem);
//                Thread.sleep(1);
            }
        }
        return 0;
    }

    @Override
    public String roleName()
    {
        return "sender";
    }
}

