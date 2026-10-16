package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.survey.repository.*;

class MatchingRefreshBatchTest {
    @Test void incomingRefreshLimitsEachLockSetAndAdvancesPastLastProcessedId() {
        var inputs=mock(MatchingInputRepository.class); var users=mock(UserRepository.class);
        var matches=mock(RoommateMatchRepository.class);
        List<Long> first=LongStream.rangeClosed(1,100).boxed().toList();
        when(inputs.incomingRequesters(999L,0L,100)).thenReturn(first);
        when(inputs.incomingRequesters(999L,100L,100)).thenReturn(List.of(101L));
        when(inputs.incomingRequesters(999L,101L,100)).thenReturn(List.of());
        var manager=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        };
        var refresh=new MatchingRefreshService(manager,inputs,users,mock(UserProfileRepository.class),
                mock(ProfileSurveyRepository.class),mock(RoommatePreferenceRepository.class),
                mock(RoommatePreferenceOptionRepository.class),mock(PreferenceCriterionRepository.class),
                matches,mock(RoommateMatchCalculator.class));
        refresh.refreshIncoming(999L);
        var ids=org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(users,times(2)).lockMatchingUsers(ids.capture());
        assertEquals(101,ids.getAllValues().get(0).size());
        assertEquals(new TreeSet<>(List.of(101L,999L)),ids.getAllValues().get(1));
        verify(matches,times(2)).flush();
    }
}
