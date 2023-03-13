package org.multicast;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static void main(String[] args) {
        final String ip = System.getProperty("aeron.endpoint.ip", "localhost");
        final String port = System.getProperty("aeron.endpoint.port", "4300");

        final String channel = String.format("aeron:udp?endpoint=239.255.255.1:%s|interface=%s|ttl=16", port, ip);
        System.out.println("Broadcast to " + channel);
        final int stream = 10;
        final int sendCount = 2_000_000;

        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //construct the subs and pubs
        final Publication publication = aeron.addPublication(channel, stream);

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount, barrier);

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