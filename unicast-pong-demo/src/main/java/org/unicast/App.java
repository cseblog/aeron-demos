package org.unicast;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static final String n1 = "192.168.64.6";
    public static final String n2 = "192.168.64.5";
    public static final String n3 = "192.168.64.4";
    public static final int port = 3999;
    public static void main(String[] args) {

        final String channel = String.format("aeron:udp?endpoint=%s:%s",n2, port, n2);
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
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive,
                Throwable::printStackTrace, null, receiveAgent);

        System.out.println("Starting Receive...");

        //start the runners
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        //close the resources
        receiveAgentRunner.close();
        aeron.close();
    }
}