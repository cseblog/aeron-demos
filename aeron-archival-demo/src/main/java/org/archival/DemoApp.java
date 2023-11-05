package org.archival;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;

import java.io.File;
import java.io.IOException;


public class DemoApp
{

    public static final String REPLICATION_CHANNEL = "aeron:udp?endpoint=localhost:0";
    public static final String CONTROL_REQUEST_CHANNEL = "aeron:udp?endpoint=localhost:8010";
    public static final String CONTROL_RESPONSE_CHANNEL = "aeron:udp?endpoint=localhost:0";
    //    private static final Logger LOGGER = LoggerFactory.getLogger(DemoApp.class);
    private final String channel = "aeron:ipc"; //connection ipc
    private final int streamCapture = 16; //kafka topic
    private final int streamReplay = 17; //kafka topic
    private final int sendCount = 10_000;

    private final IdleStrategy idleStrategy = new SleepingIdleStrategy();
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    private final File tempDir = Utils.createTempDir();
    boolean complete = false;
    private AeronArchive aeronArchive;
    private Aeron aeron;
    private ArchivingMediaDriver mediaDriver;

    public static void main(final String[] args)
    {
        final DemoApp demoApp = new DemoApp();
        demoApp.setup();
        System.out.println("Writing");

        demoApp.write();
        System.out.println("Readingssss");

        demoApp.read();


        demoApp.cleanUp();
    }

    private void cleanUp()
    {
        CloseHelper.quietClose(aeronArchive);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

    private void read()
    {
        try (AeronArchive reader = AeronArchive.connect(new AeronArchive.Context()
            .controlRequestChannel(CONTROL_REQUEST_CHANNEL)
            .controlResponseChannel(CONTROL_RESPONSE_CHANNEL)
            .aeron(aeron)))
        {
            final long recordingId = findLatestRecording(reader, channel, streamCapture);
            final long position = 0L;
            final long length = Long.MAX_VALUE;

            final long sessionId = reader.startReplay(recordingId, position, length, channel, streamReplay);
            final String channelRead = ChannelUri.addSessionId(channel, (int)sessionId);

            final Subscription subscription = reader.context().aeron().addSubscription(channelRead, streamReplay);

            while (!subscription.isConnected())
            {
                idleStrategy.idle();
            }

            while (!complete)
            {
                final int fragments = subscription.poll(this::archiveReader, 1);
                idleStrategy.idle(fragments);
            }
        }
    }

    private void write()
    {
        aeronArchive.startRecording(channel, streamCapture, SourceLocation.LOCAL);

        try (ExclusivePublication publication = aeron.addExclusivePublication(channel, streamCapture))
        {
            while (!publication.isConnected())
            {
                idleStrategy.idle();
            }

            for (int i = 0; i <= sendCount; i++)
            {
                buffer.putInt(0, i);
                while (publication.offer(buffer, 0, Integer.BYTES) < 0)
                {
                    idleStrategy.idle();
                }
            }

            final long stopPosition = publication.position();
            final CountersReader countersReader = aeron.countersReader();
            final int counterId = RecordingPos.findCounterIdBySession(countersReader, publication.sessionId());

            while (countersReader.getCounterValue(counterId) < stopPosition)
            {
                long counter = countersReader.getCounterValue(counterId);
                System.out.println("wait " + counter);
                idleStrategy.idle();
            }
        }
    }

    private long findLatestRecording(final AeronArchive archive, final String channel, final int stream)
    {
        final MutableLong lastRecordingId = new MutableLong();

        final RecordingDescriptorConsumer consumer =
            (controlSessionId, correlationId, recordingId,
                startTimestamp, stopTimestamp, startPosition,
                stopPosition, initialTermId, segmentFileLength,
                termBufferLength, mtuLength, sessionId,
                streamId, strippedChannel, originalChannel,
                sourceIdentity) -> lastRecordingId.set(recordingId);

        final long fromRecordingId = 0L;
        final int recordCount = 100;

        final int foundCount = archive.listRecordingsForUri(fromRecordingId, recordCount, channel, stream, consumer);

        if (foundCount == 0)
        {
            throw new IllegalStateException("no recordings found");
        }

        return lastRecordingId.get();
    }

    public void archiveReader(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final int valueRead = buffer.getInt(offset);
        System.out.println("Received " + valueRead);
        if (valueRead == sendCount)
        {
            complete = true;
        }
    }

    public void setup()
    {
        mediaDriver = ArchivingMediaDriver.launch(
            new MediaDriver.Context()
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true),
            new Archive.Context()
                .deleteArchiveOnStart(true)
                .controlChannel(CONTROL_REQUEST_CHANNEL)
                .replicationChannel(REPLICATION_CHANNEL)
                .archiveDir(tempDir)
        );

        aeron = Aeron.connect(); //start aeron connect MD

        aeronArchive = AeronArchive.connect(
            new AeronArchive.Context()
                .aeron(aeron)
                .controlRequestChannel(CONTROL_REQUEST_CHANNEL)
                .controlResponseChannel(CONTROL_RESPONSE_CHANNEL)
        );
    }

    public class Utils
    {
        public static File createTempDir()
        {
            final File tempDir;
            try
            {
                tempDir = File.createTempFile("archive", "tmp");
            }
            catch (final IOException ex)
            {
                throw new RuntimeException(ex);
            }

            if (!tempDir.delete())
            {
                throw new IllegalStateException("Cannot delete tmp file!");
            }

            if (!tempDir.mkdir())
            {
                throw new IllegalStateException("Cannot create folder!");
            }

            return tempDir;
        }
    }
}
