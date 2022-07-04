package com.shun.gateway.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Created by chenwenshun on 2022/6/30
 */
@Component
@Scope("prototype")
public class CircleBloomFilter {


    private CopyOnWriteArrayList<BloomFilter<CharSequence>> filters;

    private static final long expectedInsertions = 10000 * 100L;
    private static final double fpp = 0.000001;


    public CircleBloomFilter() {
        this.filters = new CopyOnWriteArrayList<>();
        filters.add(BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp));
        doCircle();
    }

    private void doCircle() {
        new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "CircleBloomFilter");
            thread.setDaemon(true);
            return thread;
        }).scheduleAtFixedRate(() -> {
            this.filters.add(0, BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp));
            if(filters.size() > 5){
                this.filters.remove(filters.size() -1);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void put(String key){
        this.filters.get(0).put(key);
    }

    public Boolean exists(String key){
        Iterator<BloomFilter<CharSequence>> iterator = this.filters.iterator();

        while ( iterator.hasNext() ){
            if (iterator.next().mightContain(key)){
                return true;
            }
        }
        return false;
    }


}
