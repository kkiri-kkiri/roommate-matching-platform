package com.kkirikkiri.community.post.service;

import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import lombok.RequiredArgsConstructor;
import com.kkirikkiri.community.post.entity.PostImageCleanup;
import com.kkirikkiri.community.post.repository.PostImageCleanupRepository;

@Service
@RequiredArgsConstructor
public class PostImageCleanupService {
    private final PostImageCleanupRepository jobs;
    private final PostImageCleanupWorker worker;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String url) {
        // External/invalid historical metadata is never a filesystem deletion instruction.
        if (PostImageStorage.filenameFromUrl(url).isEmpty()) return;
        Long id = jobs.save(new PostImageCleanup(url)).getCleanupId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { worker.process(id); }
        });
    }

    public void retryPending() {
        // Bounded batch; separate worker transactions release each job lock independently.
        jobs.findDueIds(LocalDateTime.now(), PageRequest.of(0, 100)).forEach(worker::process);
    }
}
