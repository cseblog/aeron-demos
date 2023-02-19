package org.example;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static void main(String[] args) {
        final String channel = "aeron:ipc";
        final int streamId = 10;
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //Step 1: Construct the subs and pubs
        final Subscription subscription = aeron.addSubscription(channel, streamId);

        //Step 2: Construct the agents
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount);

        //Step3: Construct agent runners
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive,
                Throwable::printStackTrace, null, receiveAgent);

        System.out.println("Starting Receive...");
        long startTime = System.currentTimeMillis();
        //Step 4: Start the runners
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();
        System.out.println(String.format("Process time: %s", System.currentTimeMillis() - startTime));
        //close the resources
        receiveAgentRunner.close();
        aeron.close();
    }
}