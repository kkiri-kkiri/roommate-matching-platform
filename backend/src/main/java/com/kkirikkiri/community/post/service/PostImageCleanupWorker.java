package com.kkirikkiri.community.post.service;

import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.community.post.repository.PostImageCleanupRepository;
import com.kkirikkiri.community.post.repository.PostImageRepository;

@Service
public class PostImageCleanupWorker {
    private final PostImageCleanupRepository jobs;
    private final PostImageRepository images;
    private final PostImageStorage storage;
    private final TransactionTemplate transaction;

    public PostImageCleanupWorker(PostImageCleanupRepository jobs, PostImageRepository images,
            PostImageStorage storage, PlatformTransactionManager manager) {
        this.jobs = jobs;
        this.images = images;
        this.storage = storage;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void process(Long id) {
        try {
            transaction.executeWithoutResult(status -> jobs.findForUpdate(id).ifPresent(job -> {
                // A batch may have selected this ID before another worker postponed it.
                // Compare the latest locked value in the same application time domain as enqueue/retryPending.
                if (!job.isDue(LocalDateTime.now())) return;
                // Includes references retained by DELETED posts. Never lock account/post rows here.
                // Public write paths cannot attach an existing URL; new uploads use CREATE_NEW UUIDs.
                if (images.existsByImageUrl(job.getImageUrl())) {
                    jobs.delete(job);
                    return;
                }
                try {
                    String filename = PostImageStorage.filenameFromUrl(job.getImageUrl())
                            .orElseThrow(() -> new IOException("Invalid image identifier"));
                    storage.delete(filename);
                    jobs.delete(job);
                } catch (IOException | SecurityException failure) {
                    job.retryLater();
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Image cleanup deferred: jobId={}", id);
                }
            }));
        } catch (RuntimeException failure) {
            // The durable row remains after DB failure; never turn a committed edit into HTTP failure.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Image cleanup transaction deferred: jobId={}", id);
        }
    }
}
