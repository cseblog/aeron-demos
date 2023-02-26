package org.unicast;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class App {
    public static void main(String[] args) {

        final String ip = System.getProperty("aeron.endpoint.ip", "localhost");
        final String port = System.getProperty("aeron.endpoint.port", "9898");

        final String channel = String.format("aeron:udp?endpoint=%s:%s", ip, port);
        final int streamId = 10;
        System.out.println("Aeron on chanel: " + channel + " streamId: " + streamId);
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //construct the subs and pubs
        final Subscription subscription = aeron.addSubscription(channel, streamId);

        //construct the agents
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount);

        //construct agent runners
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive,
                Throwable::printStackTrace, null, receiveAgent);

        System.out.println("Starting Receive...");
        long startTime = System.currentTimeMillis();
        //start the runners
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();
        long p = System.currentTimeMillis() - startTime;
        System.out.println("Process time: " + p);

        //close the resources
        receiveAgentRunner.close();
        aeron.close();
    }
}