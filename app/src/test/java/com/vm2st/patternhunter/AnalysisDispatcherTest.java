package com.vm2st.patternhunter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Field;

public class AnalysisDispatcherTest {
    private AnalysisSummary summary(String value){return new AnalysisSummary(value.length(),1,0,value,value,Collections.emptyList());}
    private static class Listener implements AnalysisDispatcher.Listener {
        final List<AnalysisSummary> results=new ArrayList<>();final List<RuntimeException> errors=new ArrayList<>();
        @Override public void onResult(AnalysisSummary result){results.add(result);}
        @Override public void onError(RuntimeException error){errors.add(error);}
    }
    private void pump(BlockingQueue<Runnable> queue) throws Exception { Runnable callback=queue.poll(3,TimeUnit.SECONDS);assertNotNull("No UI delivery",callback);callback.run(); }
    @Test public void latestRequestWinsAndQueueNeverGrowsUnbounded() throws Exception {
        BlockingQueue<Runnable> main=new LinkedBlockingQueue<>();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        AnalysisDispatcher d=new AnalysisDispatcher(main::add,text->{
            if(text.equals("old")){started.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}}
            return summary(text);
        });Listener listener=new Listener();
        try {
            d.submit("old",listener);assertTrue(started.await(2,TimeUnit.SECONDS));
            for(int i=0;i<1000;i++)d.submit("new"+i,listener);
            Field field=AnalysisDispatcher.class.getDeclaredField("worker");field.setAccessible(true);
            assertTrue(((ThreadPoolExecutor)field.get(d)).getQueue().size()<=1);
            release.countDown();pump(main);
            assertEquals(1,listener.results.size());assertEquals("new999",listener.results.get(0).getCharacterClass(false));assertTrue(listener.errors.isEmpty());
        } finally {release.countDown();d.close();}
    }
    @Test public void cancellingInvalidatesAnAlreadyPostedResult() throws Exception {
        BlockingQueue<Runnable> main=new LinkedBlockingQueue<>();AnalysisDispatcher d=new AnalysisDispatcher(main::add,this::summary);Listener listener=new Listener();
        try {d.submit("old",listener);Runnable old=main.poll(2,TimeUnit.SECONDS);assertNotNull(old);d.cancel();old.run();assertTrue(listener.results.isEmpty());}
        finally{d.close();}
    }
    @Test public void closePreventsQueuedDeliveryAndFurtherSubmissions() throws Exception {
        BlockingQueue<Runnable> main=new LinkedBlockingQueue<>();AnalysisDispatcher d=new AnalysisDispatcher(main::add,this::summary);Listener listener=new Listener();
        d.submit("old",listener);Runnable result=main.poll(2,TimeUnit.SECONDS);assertNotNull(result);d.close();d.close();result.run();d.submit("new",listener);
        assertTrue(listener.results.isEmpty());assertTrue(listener.errors.isEmpty());assertNull(main.poll(100,TimeUnit.MILLISECONDS));
    }
    @Test public void workerExceptionsBecomeAnErrorAndNextRequestStillWorks() throws Exception {
        BlockingQueue<Runnable> main=new LinkedBlockingQueue<>();AnalysisDispatcher d=new AnalysisDispatcher(main::add,text->{if(text.equals("bad"))throw new IllegalArgumentException("Invalid");return summary(text);});Listener listener=new Listener();
        try{d.submit("bad",listener);pump(main);assertEquals(1,listener.errors.size());d.submit("ok",listener);pump(main);assertEquals(1,listener.results.size());}
        finally{d.close();}
    }
    @Test public void cancellationInterruptsTheRunningTaskWithoutShowingAnError() throws Exception {
        BlockingQueue<Runnable> main=new LinkedBlockingQueue<>();CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1);
        AnalysisDispatcher d=new AnalysisDispatcher(main::add,text->{started.countDown();try{Thread.sleep(10000);}catch(InterruptedException cancelled){interrupted.countDown();throw new CancellationException();}return summary(text);});Listener listener=new Listener();
        try{d.submit("slow",listener);assertTrue(started.await(2,TimeUnit.SECONDS));d.cancel();assertTrue(interrupted.await(2,TimeUnit.SECONDS));assertNull(main.poll(100,TimeUnit.MILLISECONDS));assertTrue(listener.errors.isEmpty());}
        finally{d.close();}
    }
}
