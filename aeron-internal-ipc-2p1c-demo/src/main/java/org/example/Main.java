package org.example;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.util.UUID;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        final String channel = "aeron:ipc";
        final IdleStrategy idle = new SleepingIdleStrategy();
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));

        try (
                MediaDriver driver = MediaDriver.launch();
             Aeron aeron = Aeron.connect();
             Subscription sub = aeron.addSubscription(channel, 10);
             Publication pub1 = aeron.addPublication(channel, 10);
             Publication pub2 = aeron.addPublication(channel, 10)) {
            while (!pub1.isConnected() || !pub2.isConnected()) {
                idle.idle();
            }

            //Thread 1 send data
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        String msg = "T1: " + UUID.randomUUID().toString();
                        unsafeBuffer.putStringAscii(0, msg);
                        System.out.println("T1 sending: " + msg);

                        while (pub1.offer(unsafeBuffer) < 0) {
                            idle.idle();
                        }

                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            Thread t2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        String msg = "T2: " + UUID.randomUUID();
                        unsafeBuffer.putStringAscii(0, msg);
                        System.out.println("T2 sending: " + msg);

                        while (pub2.offer(unsafeBuffer) < 0) {
                            idle.idle();
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });


            //Thread 2 receive data
            Thread t3 = new Thread(new Runnable() {
                @Override
                public void run() {
                    FragmentHandler handler = (buffer, offset, length, header) -> System.out.println("received:" + buffer.getStringAscii(offset));
                    while (true) {
                        idle.idle(sub.poll(handler, 1));
                    }
                }
            });

            t1.start();
            t2.start();
            t3.start();

            //Waiting
            t1.join();
            t2.join();
            t3.join();
        }
    }
}