package org.multicast;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static void main(String[] args) {
        final String ip = System.getProperty("aeron.endpoint.ip", "localhost");
        final String port = System.getProperty("aeron.endpoint.port", "4300");

        final String channel = String.format("aeron:udp?endpoint=239.255.255.1:%s|interface=%s|ttl=16", port, ip);
        final int stream = 10;
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //construct the subs and pubs
        final Subscription subscription = aeron.addSubscription(channel, stream);

        //construct the agents
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount);

        //construct agent runners
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive, Throwable::printStackTrace, null, receiveAgent);

        System.out.println("Starting Receive... channel: " + channel);

        //start the runners
        long st = System.currentTimeMillis();
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();
        long processTime = System.currentTimeMillis() - st;
        System.out.println("Process time: " + processTime);
        //close the resources
        receiveAgentRunner.close();
        aeron.close();
    }
}