package org.artio.demo.client;

import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.TestRequest;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class QuickFixClient
{
    private static long count = 10_000_000;

    public static void main(String[] args) throws ConfigError
    {
        Application fixApplication = new FixClientApp();
        Connector connector = createConnector(fixApplication, QuickFixClient.class.getClassLoader().getResourceAsStream(
            "quickfix/initiator.conf"));
        connector.start();
    }

    private static Connector createConnector(Application fixApp, InputStream acceptorConfig) throws ConfigError
    {

        SessionSettings settings = new SessionSettings(acceptorConfig);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);

        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        return new SocketInitiator(fixApp, storeFactory, settings, logFactory, messageFactory);
    }

    public static class FixClientApp implements Application
    {
        private static final Logger LOGGER = Logger.getLogger(String.valueOf(FixClientApp.class));
        private Session defaultSession;
        private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        public FixClientApp()
        {
            scheduledExecutorService.scheduleAtFixedRate(() -> send(), 1, 1, TimeUnit.MICROSECONDS);
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
//            LOGGER.info("Message is being sent:" + message);
        }

        @Override
        public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType
        {
            LOGGER.info("Message received:" + message);
        }

        private void send()
        {
            if (count <= 0)
            {
                System.exit(0);
            }
            if (isLoggedOn() && count > 0)
            {
                quickfix.fix44.NewOrderSingle newOrderSingle = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(UUID.randomUUID().toString()), new Side(Side.BUY),
                    new TransactTime(LocalDateTime.now()), new OrdType(OrdType.LIMIT));
                newOrderSingle.set(new OrderQty(1000));
                newOrderSingle.set(new Symbol("USD/SGD"));
                newOrderSingle.set(new HandlInst('1'));
                newOrderSingle.set(new Price(1.3444454));
                defaultSession.send(newOrderSingle);
                count--;
            }
            else
            {
                LOGGER.info("session is not logged in, message will not send");
            }
        }

        private boolean isLoggedOn()
        {
            return defaultSession != null && defaultSession.isLoggedOn();
        }
    }
}
