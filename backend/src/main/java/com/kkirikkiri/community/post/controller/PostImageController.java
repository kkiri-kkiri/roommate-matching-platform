package com.kkirikkiri.community.post.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.kkirikkiri.community.post.service.PostImageAccessService;

@RestController
@RequestMapping("/api/post-images")
@RequiredArgsConstructor
public class PostImageController {
    private final PostImageAccessService access;

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        var bytes = access.read(filename);
        var response = ResponseEntity.status(bytes.isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0");
        if (bytes.isEmpty()) return response.build();
        String extension = filename.substring(filename.lastIndexOf('.') + 1);
        String type = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> throw new IllegalStateException("Unexpected image type");
        };
        return response.contentType(MediaType.parseMediaType(type)).body(bytes.get());
    }
}
