package org.node;

import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ClusteredMediaDriver;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MinMulticastFlowControlSupplier;
import io.aeron.driver.ThreadingMode;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.NoOpLock;
import org.agrona.concurrent.ShutdownSignalBarrier;


import java.io.File;
import java.util.Arrays;
import java.util.List;

import static java.lang.Integer.parseInt;
import static org.messaging.ClusterNodeConfig.*;

public class Node {

    private static ErrorHandler errorHandler(final String context) {
        return
                (Throwable throwable) -> {
                    System.err.println(context);
                    throwable.printStackTrace(System.err);
                };
    }

    private static String udpChannel(final int nodeId, final String hostname, final int portOffset) {
        final int port = calculatePort(nodeId, portOffset);
        return new ChannelUriStringBuilder()
                .media("udp")
                .termLength(TERM_LENGTH)
                .endpoint(hostname + ":" + port)
                .build();
    }

    private static String logControlChannel(final int nodeId, final String hostname, final int portOffset) {
        final int port = calculatePort(nodeId, portOffset);
        return new ChannelUriStringBuilder()
                .media("udp")
                .termLength(TERM_LENGTH)
                .controlMode(CommonContext.MDC_CONTROL_MODE_MANUAL)
                .controlEndpoint(hostname + ":" + port)
                .build();
    }

    private static String logReplicationChannel(final String hostname) {
        return new ChannelUriStringBuilder()
                .media("udp")
                .endpoint(hostname + ":0")
                .build();
    }

    private static String clusterMembers(final List<String> hostnames) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++) {
            sb.append(i);
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, CLIENT_FACING_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, MEMBER_FACING_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, LOG_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':').append(calculatePort(i, TRANSFER_PORT_OFFSET));
            sb.append(',').append(hostnames.get(i)).append(':')
                    .append(calculatePort(i, ARCHIVE_CONTROL_PORT_OFFSET));
            sb.append('|');
        }

        return sb.toString();
    }

    @SuppressWarnings("try")
    public static void main(final String[] args) {
        final int nodeId = parseInt(System.getProperty("aeron.cluster.tutorial.nodeId"));
        final String[] hostnames = System.getProperty(
                "aeron.cluster.tutorial.hostnames", "localhost,localhost,localhost").split(",");            // <2>
        final String hostname = hostnames[nodeId];
        final File baseDir = new File(System.getProperty("user.dir"), "node" + nodeId);
        final String aeronDirName = CommonContext.getAeronDirectoryName() + "-" + nodeId + "-driver";
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .aeronDirectoryName(aeronDirName)
                .threadingMode(ThreadingMode.SHARED)
                .termBufferSparseFile(true)
                .multicastFlowControlSupplier(new MinMulticastFlowControlSupplier())
                .terminationHook(barrier::signal)
                .errorHandler(errorHandler("Media Driver"));

        final AeronArchive.Context replicationArchiveContext = new AeronArchive.Context()
                .controlResponseChannel("aeron:udp?endpoint=" + hostname + ":0");

        final Archive.Context archiveContext = new Archive.Context()
                .aeronDirectoryName(aeronDirName)
                .archiveDir(new File(baseDir, "archive"))
                .controlChannel(udpChannel(nodeId, hostname, ARCHIVE_CONTROL_PORT_OFFSET))
                .archiveClientContext(replicationArchiveContext)
                .localControlChannel("aeron:ipc?term-length=64k")
                .recordingEventsEnabled(false)
                .threadingMode(ArchiveThreadingMode.SHARED)
                .replicationChannel("aeron:udp?endpoint=" + hostname + ":0");

        final AeronArchive.Context aeronArchiveContext = new AeronArchive.Context()
                .lock(NoOpLock.INSTANCE)
                .controlRequestChannel(archiveContext.localControlChannel())
                .controlResponseChannel(archiveContext.localControlChannel())
                .aeronDirectoryName(aeronDirName);

        final ConsensusModule.Context consensusModuleContext = new ConsensusModule.Context()
                .errorHandler(errorHandler("Consensus Module"))
                .clusterMemberId(nodeId)
                .clusterMembers(clusterMembers(Arrays.asList(hostnames)))
                .clusterDir(new File(baseDir, "cluster"))
//                .egressChannel("aeron:udp?endpoint=localhost:0")
                .ingressChannel("aeron:udp?term-length=64k")
                .logChannel(logControlChannel(nodeId, hostname, LOG_CONTROL_PORT_OFFSET))
                .replicationChannel(logReplicationChannel(hostname))
                .archiveContext(aeronArchiveContext.clone());

        final ClusteredServiceContainer.Context clusteredServiceContext =
                new ClusteredServiceContainer.Context()
                        .aeronDirectoryName(aeronDirName)
                        .archiveContext(aeronArchiveContext.clone())
                        .clusterDir(new File(baseDir, "cluster"))
                        .clusteredService(new NodeService())
                        .errorHandler(errorHandler("Clustered Service"));

        try (
                ClusteredMediaDriver clusteredMediaDriver = ClusteredMediaDriver.launch(
                        mediaDriverContext, archiveContext, consensusModuleContext);
                ClusteredServiceContainer container = ClusteredServiceContainer.launch(
                        clusteredServiceContext)) {
            System.out.println("[" + nodeId + "] Started Cluster Node on " + hostname + "...");
            barrier.await();
            System.out.println("[" + nodeId + "] Exiting");
        }
    }


}