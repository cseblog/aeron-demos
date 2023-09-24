package org.artio.demo;

import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.status.AtomicCounter;
import uk.co.real_logic.artio.CommonConfiguration;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DemoUtil
{
    public static FixLibrary blockingConnect(final LibraryConfiguration configuration)
    {
        final FixLibrary library = FixLibrary.connect(configuration);
        while (!library.isConnected())
        {
            library.poll(1);
            Thread.yield();
        }
        return library;
    }

    public static void runAgentUntilSignal(final Agent agent, final MediaDriver mediaDriver) throws InterruptedException
    {
        final AtomicCounter errorCounter =
            mediaDriver.context()
                .countersManager()
                .newCounter("exchange_agent_errors");

        final AgentRunner runner = new AgentRunner(
            CommonConfiguration.backoffIdleStrategy(),
            Throwable::printStackTrace,
            errorCounter,
            agent);

        final Thread thread = AgentRunner.startOnThread(runner);
        final AtomicBoolean running = new AtomicBoolean(true);
        SigInt.register(() -> running.set(false));

        while (running.get())
        {
            Thread.sleep(100);
        }

        thread.join();
    }
}