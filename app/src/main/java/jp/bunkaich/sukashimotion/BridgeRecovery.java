package jp.bunkaich.sukashimotion;

/** Connection generations and retry timing, independent of Android callbacks. */
final class BridgeRecovery {
    private static final long BIND_TIMEOUT_MS=10_000,MANAGER_WAKE_INTERVAL_MS=60_000;
    record Attempt(long id,boolean wakeManager) {}
    private long generation,bindingAt,nextAttempt,lastManagerWake=-MANAGER_WAKE_INTERVAL_MS;
    private int failures;
    private boolean binding,connected,timedOut;

    Attempt poll(long now,boolean permitted){
        if(connected)return null;
        if(binding){
            if(now-bindingAt<BIND_TIMEOUT_MS)return null;
            // Reject any callback already queued for the expired connection.
            ++generation;binding=false;timedOut=true;
        }
        if(now<nextAttempt)return null;
        if(!permitted){nextAttempt=now+1000;return null;}
        boolean wake=timedOut&&now-lastManagerWake>=MANAGER_WAKE_INTERVAL_MS;
        if(wake)lastManagerWake=now;
        timedOut=false;binding=true;bindingAt=now;
        nextAttempt=now+Math.min(15_000,1000L<<Math.min(failures++,4));
        return new Attempt(++generation,wake);
    }

    boolean current(long id){return id==generation&&(binding||connected);}
    boolean connected(long id){
        if(!current(id))return false;
        binding=false;connected=true;failures=0;nextAttempt=0;return true;
    }
    boolean failed(long id){
        if(!current(id))return false;
        ++generation;binding=connected=false;return true;
    }
    void lost(){++generation;binding=connected=false;nextAttempt=0;}
    void stop(){lost();failures=0;timedOut=false;}
}
