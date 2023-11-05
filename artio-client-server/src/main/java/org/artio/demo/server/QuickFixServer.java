package org.artio.demo.server;

import io.aeron.shadow.org.HdrHistogram.Histogram;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import static quickfix.field.TransactTime.FIELD;

public class QuickFixServer
{
    public static void main(String[] args) throws ConfigError
    {
        Application fixApplication = new FixAcceptorApp();
        Connector connector = createConnector(fixApplication, QuickFixServer.class.getClassLoader()
            .getResourceAsStream("quickfix/acceptor.conf"));
        connector.start();
    }

    private static Connector createConnector(Application fixApp, InputStream acceptorConfig) throws ConfigError
    {
        SessionSettings settings = new SessionSettings(acceptorConfig);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);

        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        return new SocketAcceptor(fixApp, storeFactory, settings, logFactory, messageFactory);
    }


    public static class FixAcceptorApp extends MessageCracker implements Application
    {
        private static final Logger LOGGER = Logger.getLogger(String.valueOf(FixAcceptorApp.class));
        private Session defaultSession;
        private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        private static final Histogram HISTOGRAM = new Histogram(3_600_000_000_000L, 3);
        long count = 0;

        public FixAcceptorApp()
        {
            HISTOGRAM.reset();
        }

        @Override
        public void onCreate(SessionID sessionID)
        {
        }

        @Override
        public void onLogon(SessionID sessionID)
        {
            LOGGER.info("Session is logged on");
            defaultSession = Session.lookupSession(sessionID);
        }

        @Override
        public void onLogout(SessionID sessionID)
        {
            LOGGER.info("onLogout");
        }

        @Override
        public void toAdmin(Message message, SessionID sessionID)
        {
        }

        @Override
        public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon
        {
        }

        @Override
        public void toApp(Message message, SessionID sessionID) throws DoNotSend
        {
            LOGGER.info("Message is being sent:" + message + "sessionID" + sessionID.toString());
        }

        protected void onMessage(final Message message, final SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue
        {
            super.onMessage(message, sessionID);
        }

        public void onMessage(NewOrderSingle message, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue
        {
            count++;
            String senderCompId = message.getHeader().getString(SenderCompID.FIELD);
            String targetCompId = message.getHeader().getString(TargetCompID.FIELD);
            String clOrdId = message.getString(ClOrdID.FIELD);
            String symbol = message.getString(Symbol.FIELD);
            TransactTime transactTime = message.getTransactTime();
            char side = message.getChar(Side.FIELD);
            char ordType = message.getChar(OrdType.FIELD);
            double price = 0;
            price = message.getDouble(Price.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);

            LocalDateTime localDateTime = transactTime.getValue();
            LocalDateTime now = LocalDateTime.now();

            Duration duration = Duration.between(localDateTime, now);
            long t = duration.toNanos();
            HISTOGRAM.recordValue(t);
            if(count >= 100_000) {
                HISTOGRAM.outputPercentileDistribution(System.out, 100.0);
                System.exit(0);
            }
        }


        @Override
        public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType
        {
            crack(message, sessionID);
        }
    }
}
