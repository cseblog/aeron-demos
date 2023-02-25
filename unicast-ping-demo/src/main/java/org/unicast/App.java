package org.unicast;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static final String n1 = "192.168.64.6";
    public static final String n2 = "192.168.64.5";
    public static final String n3 = "192.168.64.4";
    public static int port = 3999;
    public static void main(String[] args) {
        final String channel = String.format("aeron:udp?endpoint=%s:%s", n1, port);
        final int stream = 10;
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //construct the subs and pubs
        final Publication publication = aeron.addPublication(channel, stream);

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount);

        //construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend,
                Throwable::printStackTrace, null, sendAgent);

        System.out.println("Starting SendingAgent...");
        //start the runners
        AgentRunner.startOnThread(sendAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        //close the resources
        sendAgentRunner.close();
        aeron.close();
    }
}