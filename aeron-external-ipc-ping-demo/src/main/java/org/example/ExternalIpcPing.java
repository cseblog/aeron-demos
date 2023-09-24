package org.example;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class ExternalIpcPing {

    //Start Media Driver seperately
    public static void main(String[] args) {
        final String channel = "aeron:ipc";
        final int streamId = 10;
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final Aeron aeron = Aeron.connect();

        //Step 1: Construct the subs and pubs
        final Publication publication = aeron.addPublication(channel, streamId);

        //Step 2: Construct the agents
        final SendAgent sendAgent = new SendAgent(publication, barrier, sendCount);

        //Step 3: Construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend,
                Throwable::printStackTrace, null, sendAgent);


        System.out.println("Starting SendingAgent....");
        //Step 4: Start the runners
        AgentRunner.startOnThread(sendAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        //close the resources
        sendAgentRunner.close();
        aeron.close();
    }
}


