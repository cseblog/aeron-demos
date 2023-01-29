package org.consumer;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.Arrays;
import java.util.List;

import static org.messaging.ClusterNodeConfig.*;

public class Consumer implements EgressListener {
    private final MutableDirectBuffer actionBidBuffer = new ExpandableArrayBuffer();
    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    private final long customerId;

    public Consumer(final long customerId) {
        this.customerId = customerId;
    }

    public void onMessage(
            final long clusterSessionId,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header) {
        final long correlationId = buffer.getLong(offset + CORRELATION_ID_OFFSET);
        final long customerId = buffer.getLong(offset + CUSTOMER_ID_OFFSET);
        final long currentPrice = buffer.getLong(offset + PRICE_OFFSET);
        final boolean bidSucceed = 0 != buffer.getByte(offset + BID_SUCCEEDED_OFFSET);

        printOutput(
                "SessionMessage(" + clusterSessionId + ", " + correlationId + "," +
                        customerId + ", " + currentPrice + ", " + bidSucceed + ")");
    }

    public void onSessionEvent(
            final long correlationId,
            final long clusterSessionId,
            final long leadershipTermId,
            final int leaderMemberId,
            final EventCode code,
            final String detail) {
        printOutput(
                "SessionEvent(" + correlationId + ", " + leadershipTermId + ", " +
                        leaderMemberId + ", " + code + ", " + detail + ")");
    }

    public void onNewLeader(
            final long clusterSessionId,
            final long leadershipTermId,
            final int leaderMemberId,
            final String ingressEndpoints) {
        printOutput("NewLeader(" + clusterSessionId + ", " + leadershipTermId + ", " + leaderMemberId + ")");
    }
    // end::response[]

    private void bidInAuction(final AeronCluster aeronCluster) {
        while (!Thread.currentThread().isInterrupted()) {
            aeronCluster.sendKeepAlive();
            idleStrategy.idle(aeronCluster.pollEgress());
        }
    }

    public static String ingressEndpoints(final List<String> hostnames) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++) {
            sb.append(i).append('=');
            sb.append(hostnames.get(i))
                    .append(':')
                    .append(calculatePort(i, CLIENT_FACING_PORT_OFFSET));
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void printOutput(final String message) {
        System.out.println(message);
    }

    public static void main(final String[] args) {
        final int customerId = Integer.parseInt(System.getProperty("aeron.cluster.tutorial.customerId"));       // <1>
        final String[] hostnames = System
                .getProperty("aeron.cluster.tutorial.hostnames", "localhost,localhost,localhost")
                .split(",");
        final String ingressEndpoints = ingressEndpoints(Arrays.asList(hostnames));
        System.out.println("ingressEndpoints..." + ingressEndpoints);

        final Consumer client = new Consumer(customerId);
        try (
                MediaDriver mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()                      // <1>
                        .threadingMode(ThreadingMode.SHARED)
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true));
                AeronCluster aeronCluster = AeronCluster.connect(
                        new AeronCluster.Context()
                                .egressListener(client)
                .egressChannel("aeron:udp?endpoint=localhost:0")
//                                .egressChannel("aeron:udp?endpoint=239.255.255.1:4300|interface=192.168.10.137|ttl=16")// <3>
                                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                                .ingressChannel("aeron:udp")                                                                    // <4>
                                .ingressEndpoints(ingressEndpoints)))                                                           // <5>
        {
            client.bidInAuction(aeronCluster);
        }
    }

}