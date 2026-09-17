package jp.bunkaich.sukashimotion;

import org.junit.Test;
import static org.junit.Assert.*;

public class BridgeRecoveryTest {
    @Test public void stalledHelperRequestsManagerRecoveryAndRejectsItsLateBinder(){
        BridgeRecovery recovery=new BridgeRecovery();
        BridgeRecovery.Attempt stalled=recovery.poll(0,true);
        assertFalse(stalled.wakeManager());
        assertNull(recovery.poll(9999,true));
        BridgeRecovery.Attempt retry=recovery.poll(10_000,true);
        assertTrue(retry.wakeManager());
        assertFalse(recovery.connected(stalled.id()));
        assertTrue(recovery.connected(retry.id()));
        assertNull(recovery.poll(90_000,true));
    }

    @Test public void repeatedStartupFailuresDoNotContinuouslyWakeTheManager(){
        BridgeRecovery recovery=new BridgeRecovery();
        recovery.poll(0,true);
        assertTrue(recovery.poll(10_000,true).wakeManager());
        assertFalse(recovery.poll(20_000,true).wakeManager());
        assertFalse(recovery.poll(30_000,true).wakeManager());
        assertFalse(recovery.poll(40_000,true).wakeManager());
        // The retry delay is capped at 15 seconds, even after many failures.
        assertNull(recovery.poll(50_000,true));
        assertFalse(recovery.poll(55_000,true).wakeManager());
        assertNull(recovery.poll(65_000,true));
        assertTrue(recovery.poll(70_000,true).wakeManager());
    }

    @Test public void stopImmediatelyRejectsQueuedConnectionAndDisconnectCallbacks(){
        BridgeRecovery recovery=new BridgeRecovery();
        BridgeRecovery.Attempt first=recovery.poll(0,true);
        recovery.stop();
        assertFalse(recovery.current(first.id()));
        assertFalse(recovery.connected(first.id()));
        BridgeRecovery.Attempt replacement=recovery.poll(1,true);
        assertTrue(recovery.connected(replacement.id()));
        assertFalse(recovery.current(first.id()));
        assertFalse(recovery.failed(first.id()));
        assertNull(recovery.poll(60_000,true));
    }

    @Test public void lostHealthyConnectionCanReconnectWithoutWaitingForOldBackoff(){
        BridgeRecovery recovery=new BridgeRecovery();
        BridgeRecovery.Attempt first=recovery.poll(0,true);
        assertTrue(recovery.connected(first.id()));
        recovery.lost();
        BridgeRecovery.Attempt second=recovery.poll(1,true);
        assertNotNull(second);assertFalse(second.wakeManager());
        assertFalse(recovery.connected(first.id()));
        assertTrue(recovery.connected(second.id()));
    }

    @Test public void noPermissionNeverCreatesOrWarmsAHelper(){
        BridgeRecovery recovery=new BridgeRecovery();
        for(long now:new long[]{0,1,1000,60_000})assertNull(recovery.poll(now,false));
        BridgeRecovery.Attempt allowed=recovery.poll(61_000,true);
        assertNotNull(allowed);assertFalse(allowed.wakeManager());
    }

    @Test public void thrownBindDoesNotAcceptLateCallbackAndRetainsRetryDelay(){
        BridgeRecovery recovery=new BridgeRecovery();
        BridgeRecovery.Attempt first=recovery.poll(0,true);
        assertTrue(recovery.failed(first.id()));
        assertFalse(recovery.connected(first.id()));
        assertNull(recovery.poll(999,true));
        BridgeRecovery.Attempt retry=recovery.poll(1000,true);
        assertNotNull(retry);assertFalse(retry.wakeManager());
        assertTrue(recovery.connected(retry.id()));
    }

    @Test public void stopAndRestartDoNotBypassManagerWakeRateLimit(){
        BridgeRecovery recovery=new BridgeRecovery();
        recovery.poll(0,true);
        assertTrue(recovery.poll(10_000,true).wakeManager());
        recovery.stop();
        recovery.poll(10_001,true);
        assertFalse(recovery.poll(20_001,true).wakeManager());
    }
}
