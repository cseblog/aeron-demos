package org.artio.demo.server;

import io.aeron.shadow.org.HdrHistogram.Histogram;

public class Demo
{
    private static final Histogram HISTOGRAM = new Histogram(3600000000000L, 3);
    static long count = 0;
    public static void main(String[] args)
    {
        while(true) {
            long st = System.currentTimeMillis();
            System.out.println(count);
            count++;
            long et = System.currentTimeMillis();


            HISTOGRAM.recordValue(et-st);
            if(count >= 1000_000){
                break;
            }
        }

        HISTOGRAM.outputPercentileDistribution(System.out, 100.0);


    }
}
