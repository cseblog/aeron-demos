package org.artio.demo.server;


import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import io.aeron.shadow.org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import quickfix.field.*;
import uk.co.real_logic.artio.builder.Printer;
import uk.co.real_logic.artio.decoder.NewOrderCrossDecoder;
import uk.co.real_logic.artio.decoder.NewOrderSingleDecoder;
import uk.co.real_logic.artio.decoder.PrinterImpl;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.util.AsciiBuffer;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static io.aeron.logbuffer.ControlledFragmentHandler.Action.ABORT;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.CONTINUE;

public class DemoSessionHandler implements SessionHandler
{

    private final AsciiBuffer asciiBuffer = new MutableAsciiBuffer();
    private final NewOrderSingleDecoder newOrderSingle = new NewOrderSingleDecoder();
    private static final Histogram HISTOGRAM = new Histogram(3_600_000_000_000L, 3);
    long count = 0;

    public DemoSessionHandler(final Session session)
    {
    }

    public Action onMessage(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int libraryId,
        final Session session,
        final int sequenceIndex,
        final long messageType,
        final long timestampInNs,
        final long position,
        final OnMessageInfo messageInfo)
    {
        asciiBuffer.wrap(buffer, offset, length);

        if (messageType == NewOrderSingleDecoder.MESSAGE_TYPE)
        {
            newOrderSingle.decode(asciiBuffer, 0, length);
            String senderCompId = newOrderSingle.header().senderCompIDAsString();
            String targetCompId = newOrderSingle.header().targetCompIDAsString();
            String sendingTime = newOrderSingle.header().sendingTimeAsString();
            String clOrdId = newOrderSingle.clOrdIDAsString();
            String symbol = newOrderSingle.symbolAsString();
            char side = newOrderSingle.side();
            char ordType = newOrderSingle.ordType();
            double price = newOrderSingle.price().toDouble();
            double qty = newOrderSingle.orderQty().toDouble();
            String transactionTime = newOrderSingle.transactTimeAsString();

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");

            try
            {
                Date sending = dateFormat.parse(sendingTime);
                Instant now = Instant.now();
                Duration duration = Duration.between(sending.toInstant(), now);
                long t = duration.toNanos();
                HISTOGRAM.recordValue(t);
                if(count >= 100_000) {
                    HISTOGRAM.outputPercentileDistribution(System.out, 100.0);
                    System.exit(0);
                }
                count++;
            }
            catch (ParseException e)
            {
                throw new RuntimeException(e);
            }

        }


        return CONTINUE;
    }

    public void onTimeout(final int libraryId, final Session session)
    {
    }

    public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow)
    {
    }

    public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason)
    {
        System.out.printf("%d Disconnected: %s%n", session.id(), reason);
        return CONTINUE;
    }

    public void onSessionStart(final Session session)
    {
    }
}

