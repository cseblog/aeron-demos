package org.example;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class Main {
    public static void main(String[] args) {
//        final String channel = "aeron:ipc";
//        final String channel = "aeron:udp?endpoint=localhost:20123";
        //                .egressChannel("aeron:udp?endpoint=localhost:0")
        final String channel = "aeron:udp?endpoint=239.255.255.1:4300|interface=192.168.10.137|ttl=16";
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