package com.kkirikkiri.matching.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.survey.entity.*;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

/** Each batch reads inputs after acquiring user locks in ascending PK order. */
@Service
@RequiredArgsConstructor
public class MatchingRefreshService {
    private static final int BATCH_SIZE = 100;
    private final PlatformTransactionManager transactionManager;
    private final MatchingInputRepository inputs;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ProfileSurveyRepository surveys;
    private final RoommatePreferenceRepository preferences;
    private final RoommatePreferenceOptionRepository options;
    private final PreferenceCriterionRepository criteria;
    private final RoommateMatchRepository matches;
    private final RoommateMatchCalculator calculator;

    public void refreshOutgoing(Long requesterId) {
        long afterId = 0;
        while (true) {
            List<Long> targets = inputs.outgoingTargets(requesterId, afterId, BATCH_SIZE);
            if (targets.isEmpty()) return;
            refresh(List.of(requesterId), targets);
            afterId = targets.getLast();
        }
    }

    public void refreshIncoming(Long targetId) {
        long afterId = 0;
        while (true) {
            List<Long> requesters = inputs.incomingRequesters(targetId, afterId, BATCH_SIZE);
            if (requesters.isEmpty()) return;
            refresh(requesters, List.of(targetId));
            afterId = requesters.getLast();
        }
    }

    public void refreshTargets(Long requesterId, List<Long> targetIds) {
        List<Long> sorted = targetIds.stream().distinct().sorted().toList();
        for (int offset = 0; offset < sorted.size(); offset += BATCH_SIZE) {
            refresh(List.of(requesterId), sorted.subList(offset, Math.min(offset + BATCH_SIZE, sorted.size())));
        }
    }

    public Optional<RoommateMatchCalculator.Result> refreshAnalysis(Long requesterId, Long targetId) {
        return Optional.ofNullable(refresh(List.of(requesterId), List.of(targetId)).get(new Pair(requesterId, targetId)));
    }

    private Map<Pair, RoommateMatchCalculator.Result> refresh(List<Long> requesterIds, List<Long> targetIds) {
        if (requesterIds.isEmpty() || targetIds.isEmpty()) return Map.of();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for (int attempt = 0; ; attempt++) {
            try {
                return tx.execute(status -> calculateBatch(requesterIds, targetIds));
            } catch (TransientDataAccessException failure) {
                if (attempt == 2) throw failure;
            }
        }
    }

    private Map<Pair, RoommateMatchCalculator.Result> calculateBatch(List<Long> requesterIds, List<Long> targetIds) {
        Map<Pair, RoommateMatchCalculator.Result> results = new HashMap<>();
        Set<Long> ids = new TreeSet<>(requesterIds);
        ids.addAll(targetIds);
        Map<Long, User> userMap = users.lockMatchingUsers(ids).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        var profileMap = profiles.findAllByUser_UserIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), Function.identity()));
        var surveyMap = surveys.findAllByUser_UserIdIn(targetIds).stream()
                .collect(Collectors.toMap(s -> s.getUser().getUserId(), Function.identity()));
        var preferenceMap = preferences.findAllByUser_UserIdIn(requesterIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), Function.identity()));
        List<Long> preferenceIds = preferenceMap.values().stream().map(RoommatePreference::getPreferenceId).toList();
        Map<Long, List<RoommatePreferenceOption>> optionMap = preferenceIds.isEmpty() ? Map.of()
                : options.findAllByRoommatePreference_PreferenceIdInOrderByPreferenceOptionIdAsc(preferenceIds).stream()
                .collect(Collectors.groupingBy(o -> o.getRoommatePreference().getPreferenceId()));
        Map<Long, List<PreferenceCriterion>> criterionMap = preferenceIds.isEmpty() ? Map.of()
                : criteria.findAllByRoommatePreference_PreferenceIdIn(preferenceIds).stream()
                .collect(Collectors.groupingBy(c -> c.getRoommatePreference().getPreferenceId()));
        var existing = matches.findAllByRequesterUser_UserIdInAndTargetUser_UserIdIn(requesterIds, targetIds).stream()
                .collect(Collectors.toMap(m -> new Pair(m.getRequesterUser().getUserId(), m.getTargetUser().getUserId()),
                        Function.identity()));
        List<RoommateMatch> saved = new ArrayList<>();
        List<RoommateMatch> removed = new ArrayList<>();
        for (Long requesterId : requesterIds) {
            RoommatePreference preference = preferenceMap.get(requesterId);
            for (Long targetId : targetIds) {
                if (requesterId.equals(targetId)) continue;
                RoommateMatch match = existing.get(new Pair(requesterId, targetId));
                Optional<RoommateMatchCalculator.Result> result = Optional.empty();
                if (active(userMap.get(requesterId)) && active(userMap.get(targetId))
                        && valid(profileMap.get(requesterId)) && valid(profileMap.get(targetId)) && preference != null) {
                    result = calculator.calculate(profileMap.get(requesterId), profileMap.get(targetId),
                            surveyMap.get(targetId), preference,
                            optionMap.getOrDefault(preference.getPreferenceId(), List.of()),
                            criterionMap.getOrDefault(preference.getPreferenceId(), List.of()));
                }
                if (result.isEmpty()) {
                    if (match != null) removed.add(match);
                    continue;
                }
                var value = result.get();
                results.put(new Pair(requesterId, targetId), value);
                if (match == null) {
                    saved.add(new RoommateMatch(userMap.get(requesterId), userMap.get(targetId),
                            value.score(), null));
                } else if (match.getMatchScore() != value.score()) {
                    match.updateResult(value.score(), null);
                }
            }
        }
        matches.deleteAll(removed);
        matches.saveAll(saved);
        matches.flush();
        return results;
    }

    private boolean active(User user) {
        return user != null && user.getStatus() == UserStatus.ACTIVE;
    }

    private boolean valid(UserProfile profile) {
        return profile != null && profile.getFaculty() != null && profile.getDormitory() != null
                && profile.getBirthYear() != null && profile.getGrade() != null
                && profile.getNickname() != null && profile.getAdmissionYear() != null && profile.getMbti() != null;
    }

    private record Pair(Long requesterId, Long targetId) {}
}
