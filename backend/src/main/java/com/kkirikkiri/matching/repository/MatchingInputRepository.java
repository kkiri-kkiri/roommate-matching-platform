package com.kkirikkiri.matching.repository;

import java.util.List;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@org.springframework.transaction.annotation.Transactional(readOnly = true,
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
@Repository
@RequiredArgsConstructor
public class MatchingInputRepository {
    private final EntityManager em;

    public List<Long> outgoingTargets(Long requesterId, Long afterId, int limit) {
        // Include old rows so unavailable targets can no longer retain recommendations.
        return em.createQuery("""
                select u.userId from User u where u.userId > :afterId and u.userId <> :id and
                ((u.status = com.kkirikkiri.user.entity.UserStatus.ACTIVE
                  and exists (select p.userProfileId from UserProfile p where p.user = u)
                  and exists (select s.profileSurveyId from ProfileSurvey s where s.user = u and s.completed = true))
                 or exists (select m.matchId from RoommateMatch m where m.requesterUser.userId = :id and m.targetUser = u))
                order by u.userId
                """, Long.class).setParameter("id", requesterId).setParameter("afterId", afterId)
                .setMaxResults(limit).getResultList();
    }

    public List<Long> incomingRequesters(Long targetId, Long afterId, int limit) {
        return em.createQuery("""
                select u.userId from User u where u.userId > :afterId and u.userId <> :id and
                ((u.status = com.kkirikkiri.user.entity.UserStatus.ACTIVE
                  and exists (select p.preferenceId from RoommatePreference p where p.user = u and p.completed = true))
                 or exists (select m.matchId from RoommateMatch m where m.targetUser.userId = :id and m.requesterUser = u))
                order by u.userId
                """, Long.class).setParameter("id", targetId).setParameter("afterId", afterId)
                .setMaxResults(limit).getResultList();
    }
}
