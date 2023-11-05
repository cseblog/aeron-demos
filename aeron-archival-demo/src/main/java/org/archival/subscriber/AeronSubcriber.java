package org.archival.subscriber;


import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public class AeronSubcriber
{
    public static void main(String[] args)
    {
        // Define the Aeron context
        Aeron.Context ctx = new Aeron.Context();

        // Create an Aeron instance
        try (Aeron aeron = Aeron.connect(ctx))
        {
            // Define the subscription channel and stream ID
            String channel = "aeron:ipc";
            int streamId = 16;

            // Create a subscription for the given channel and stream ID
            Subscription subscription = aeron.addSubscription(channel, streamId);

            // Create a FragmentHandler to process received messages
            FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
                // Process the message here
                String message = new String(buffer.byteArray(), offset, length);
                System.out.println("Received message: " + message);
            };

            // Create an IdleStrategy for the subscriber to use when no messages are available
            IdleStrategy idleStrategy = new BackoffIdleStrategy(10, 1000, 5000, 10000);

            // Main message processing loop
            while (true)
            {
                // Poll the subscription for new messages
                int fragmentsRead = subscription.poll(fragmentHandler, 10);

                // Use the idle strategy when no messages are available
                if (fragmentsRead == 0)
                {
                    idleStrategy.idle();
                }
            }
        }
    }
}