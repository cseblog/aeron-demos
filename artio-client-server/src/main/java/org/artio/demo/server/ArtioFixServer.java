package org.artio.demo.server;


import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.driver.MediaDriver.Context;
import org.agrona.IoUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.artio.demo.DemoUtil;
import uk.co.real_logic.artio.CommonConfiguration;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.library.AcquiringSessionExistsHandler;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.validation.AuthenticationStrategy;
import uk.co.real_logic.artio.validation.MessageValidationStrategy;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.driver.ThreadingMode.SHARED;
import static java.util.Collections.singletonList;
import static uk.co.real_logic.artio.messages.SessionState.DISCONNECTED;

public final class ArtioFixServer
{
    public static final String ACCEPTOR_COMP_ID = "acceptor";
    public static final String INITIATOR_COMP_ID = "initiator";
    public static final String CONTROL_CHANNEL = "aeron:udp?endpoint=localhost:7050";
    public static final String REPLICATION_CHANNEL = "aeron:udp?endpoint=localhost:7040";
//    public static final String CONTROL_RESPONSE_CHANNEL = "aeron:udp?endpoint=localhost:7060";
    public static final String CONTROL_RESPONSE_CHANNEL = "aeron:ipc";
    public static final String CONTROL_REQUEST_CHANNEL = "aeron:ipc";

//    public static final String CONTROL_REQUEST_CHANNEL = "aeron:udp?endpoint=localhost:7070";

    private static Session session;

    public static void main(final String[] args)
    {
        final MessageValidationStrategy validationStrategy = MessageValidationStrategy.targetCompId(ACCEPTOR_COMP_ID)
            .and(MessageValidationStrategy.senderCompId(Collections.singletonList(INITIATOR_COMP_ID)));

        final AuthenticationStrategy authenticationStrategy = AuthenticationStrategy.of(validationStrategy);

        // Static configuration lasts the duration of a FIX-Gateway instance
        final String aeronChannel = "aeron:udp?endpoint=localhost:10000";
        final EngineConfiguration configuration = new EngineConfiguration()
            .bindTo("localhost", 9999)
            .libraryAeronChannel(aeronChannel);
        configuration.authenticationStrategy(authenticationStrategy);
        configuration.aeronArchiveContext()
            .controlResponseChannel(CONTROL_RESPONSE_CHANNEL)
            .controlRequestChannel(CONTROL_REQUEST_CHANNEL);


        cleanupOldLogFileDir(configuration);

        final Context context = new Context()
            .threadingMode(SHARED)
            .dirDeleteOnStart(true);

        final Archive.Context archiveContext = new Archive.Context()
            .threadingMode(ArchiveThreadingMode.SHARED)
            .controlChannel(CONTROL_CHANNEL)
            .replicationChannel(REPLICATION_CHANNEL)
            .deleteArchiveOnStart(true);

        try (ArchivingMediaDriver driver = ArchivingMediaDriver.launch(context, archiveContext);
            FixEngine gateway = FixEngine.launch(configuration))
        {
            final LibraryConfiguration libraryConfiguration = new LibraryConfiguration();

            // You register the new session handler - which is your application hook
            // that receives messages for new sessions
            libraryConfiguration
                .sessionAcquireHandler((session, acquiredInfo) -> onConnect(session))
                .sessionExistsHandler(new AcquiringSessionExistsHandler())
                .libraryAeronChannels(singletonList(aeronChannel));

            final IdleStrategy idleStrategy = CommonConfiguration.backoffIdleStrategy();

            try (FixLibrary library = DemoUtil.blockingConnect(libraryConfiguration))
            {
                final AtomicBoolean running = new AtomicBoolean(true);
                SigInt.register(() -> running.set(false));

                while (running.get())
                {
                    idleStrategy.idle(library.poll(1));

                    if (session != null && session.state() == DISCONNECTED)
                    {
                        break;
                    }
                }
            }
        }

        System.exit(0);
    }

    public static void cleanupOldLogFileDir(final EngineConfiguration configuration)
    {
        IoUtil.delete(new File(configuration.logFileDir()), true);
    }

    private static SessionHandler onConnect(final Session session)
    {
        ArtioFixServer.session = session;

        // Simple server just handles a single connection on a single thread
        // You choose how to manage threads for your application.

        return new DemoSessionHandler(session);
    }
}