package com.kkirikkiri.community.post.service;

import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.repository.PostImageRepository;

@Service
@RequiredArgsConstructor
public class PostImageAccessService {
    private final PostImageRepository images;
    private final PostImageStorage storage;

    @Transactional(readOnly = true)
    public Optional<byte[]> read(String filename) {
        if (!PostImageStorage.isSafeFilename(filename)
                || !images.existsByImageUrlAndPost_Status(PostImageStorage.URL_PREFIX + filename,
                        CommunityContentStatus.ACTIVE)) return Optional.empty();
        try {
            return Optional.of(storage.read(filename));
        } catch (IOException | SecurityException unavailable) {
            return Optional.empty();
        }
    }
}
