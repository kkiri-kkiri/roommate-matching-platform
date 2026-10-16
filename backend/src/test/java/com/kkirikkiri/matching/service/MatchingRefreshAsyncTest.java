package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class MatchingRefreshAsyncTest {
    @Test void blockedCalculationDoesNotBlockCallerAndPerformsBothDirections() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        var refresh=mock(MatchingRefreshService.class);
        doAnswer(call->{entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return null;}).when(refresh).refreshIncoming(1L);
        doAnswer(call->{finished.countDown(); return null;}).when(refresh).refreshOutgoing(1L);
        try {
            var listener=new MatchingRefreshListener(refresh,executor);
            listener.refresh(new MatchingInputsChanged(1L,true,true));
            assertTrue(entered.await(5,TimeUnit.SECONDS)); assertEquals(1,finished.getCount());
            release.countDown(); assertTrue(finished.await(5,TimeUnit.SECONDS));
            verify(refresh).refreshIncoming(1L); verify(refresh).refreshOutgoing(1L);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void queueSaturationDoesNotRunWorkOnCallerOrFailCommittedInput() {
        var refresh=mock(MatchingRefreshService.class);
        var listener=new MatchingRefreshListener(refresh,task->{throw new RejectedExecutionException();});
        assertDoesNotThrow(()->listener.refresh(new MatchingInputsChanged(1L,true,true)));
        verifyNoInteractions(refresh);
    }
}
