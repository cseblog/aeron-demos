package org.messaging;

import org.agrona.BitUtil;

public class ClusterNodeConfig {
    public static final int CORRELATION_ID_OFFSET = 0;
    public static final int CUSTOMER_ID_OFFSET = CORRELATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int PRICE_OFFSET = CUSTOMER_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int BID_MESSAGE_LENGTH = PRICE_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int BID_SUCCEEDED_OFFSET = BID_MESSAGE_LENGTH;
    public static final int EGRESS_MESSAGE_LENGTH = BID_SUCCEEDED_OFFSET + BitUtil.SIZE_OF_BYTE;

    public static final int SNAPSHOT_CUSTOMER_ID_OFFSET = 0;
    public static final int SNAPSHOT_PRICE_OFFSET = SNAPSHOT_CUSTOMER_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int SNAPSHOT_MESSAGE_LENGTH = SNAPSHOT_PRICE_OFFSET + BitUtil.SIZE_OF_LONG;


    public static final int ARCHIVE_CONTROL_PORT_OFFSET = 1;
    public static final int CLIENT_FACING_PORT_OFFSET = 2;
    public static final int MEMBER_FACING_PORT_OFFSET = 3;
    public static final int LOG_PORT_OFFSET = 4;
    public static final int TRANSFER_PORT_OFFSET = 5;
    public static final int LOG_CONTROL_PORT_OFFSET = 6;
    public static final int REPLICATION_PORT_OFFSET = 7;
    public static final int TERM_LENGTH = 64 * 1024;


    private static final int PORT_BASE = 9000;
    private static final int PORTS_PER_NODE = 100;

    public static int calculatePort(final int nodeId, final int offset) {
        return PORT_BASE + (nodeId * PORTS_PER_NODE) + offset;
    }
}
