package org.node;

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.IdleStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.messaging.ClusterNodeConfig.*;

public class NodeService implements ClusteredService {

    private final MutableDirectBuffer egressMessageBuffer = new ExpandableArrayBuffer();
    private final MutableDirectBuffer snapshotBuffer = new ExpandableArrayBuffer();
    List<ClientSession> sessionList = new ArrayList<>();
    private final Auction auction = new Auction();
    private Cluster cluster;
    private IdleStrategy idleStrategy;

    public void onStart(final Cluster cluster, final Image snapshotImage) {
        this.cluster = cluster;
        this.idleStrategy = cluster.idleStrategy();

        if (null != snapshotImage) {
            loadSnapshot(cluster, snapshotImage);
        }
    }

    public void onSessionMessage(
            final ClientSession session,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header) {

        final long correlationId = buffer.getLong(offset + CORRELATION_ID_OFFSET);
        final long customerId = buffer.getLong(offset + CUSTOMER_ID_OFFSET);
        final long price = buffer.getLong(offset + PRICE_OFFSET);
        final boolean bidSucceeded = auction.attemptBid(price, customerId);

        sessionList.stream().forEach(s -> {
            egressMessageBuffer.putLong(CORRELATION_ID_OFFSET, correlationId);
            egressMessageBuffer.putLong(CUSTOMER_ID_OFFSET, customerId);
            egressMessageBuffer.putLong(PRICE_OFFSET, price);
            egressMessageBuffer.putByte(BID_SUCCEEDED_OFFSET, (byte) 1);

            idleStrategy.reset();
            while (s.offer(egressMessageBuffer, 0, EGRESS_MESSAGE_LENGTH) < 0) {
                idleStrategy.idle();
            }
        });
    }

    public void onTakeSnapshot(final ExclusivePublication snapshotPublication) {
        snapshotBuffer.putLong(SNAPSHOT_CUSTOMER_ID_OFFSET, auction.getCurrentWinningCustomerId());
        snapshotBuffer.putLong(SNAPSHOT_PRICE_OFFSET, auction.getBestPrice());

        idleStrategy.reset();
        while (snapshotPublication.offer(snapshotBuffer, 0, SNAPSHOT_MESSAGE_LENGTH) < 0) {
            idleStrategy.idle();
        }
    }

    private void loadSnapshot(final Cluster cluster, final Image snapshotImage) {
        final MutableBoolean allDataLoaded = new MutableBoolean(false);

        while (!snapshotImage.isEndOfStream()) {
            final int fragmentsPolled = snapshotImage.poll(
                    (buffer, offset, length, header) -> // <2>
                    {
                        assert length >= SNAPSHOT_MESSAGE_LENGTH;
                        final long customerId = buffer.getLong(offset + SNAPSHOT_CUSTOMER_ID_OFFSET);
                        final long price = buffer.getLong(offset + SNAPSHOT_PRICE_OFFSET);

                        auction.loadInitialState(price, customerId);

                        allDataLoaded.set(true);
                    },
                    1);

            if (allDataLoaded.value) {
                break;
            }

            idleStrategy.idle(fragmentsPolled);
        }

        assert snapshotImage.isEndOfStream();
        assert allDataLoaded.value;
    }

    public void onRoleChange(final Cluster.Role newRole) {
    }

    public void onTerminate(final Cluster cluster) {
    }

    public void onSessionOpen(final ClientSession session, final long timestamp) {
        System.out.println("onSessionOpen(" + session + ")");
        sessionList.add(session);
    }

    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason) {
        System.out.println("onSessionClose(" + session + ")");
        sessionList.remove(session);
    }

    public void onTimerEvent(final long correlationId, final long timestamp) {
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final NodeService that = (NodeService) o;

        return auction.equals(that.auction);
    }

    public int hashCode() {
        return Objects.hash(auction);
    }

    public String toString() {
        return "NodeService{" + "auction=" + auction + '}';
    }

    static class Auction {
        private long bestPrice = 0;
        private long currentWinningCustomerId = -1;

        void loadInitialState(final long price, final long customerId) {
            bestPrice = price;
            currentWinningCustomerId = customerId;
        }

        boolean attemptBid(final long price, final long customerId) {
            System.out.println("attemptBid(this=" + this + ", price=" + price + ",customerId=" + customerId + ")");

            if (price <= bestPrice) {
                return false;
            }

            bestPrice = price;
            currentWinningCustomerId = customerId;

            return true;
        }

        long getBestPrice() {
            return bestPrice;
        }

        long getCurrentWinningCustomerId() {
            return currentWinningCustomerId;
        }

        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Auction auction = (Auction) o;

            return bestPrice == auction.bestPrice && currentWinningCustomerId == auction.currentWinningCustomerId;
        }

        public int hashCode() {
            return Objects.hash(bestPrice, currentWinningCustomerId);
        }

        public String toString() {
            return "Auction{" +
                    "bestPrice=" + bestPrice +
                    ", currentWinningCustomerId=" + currentWinningCustomerId +
                    '}';
        }
    }


}
