package com.middleware.manager.web.api;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.web.api.dto.PageResponse;
import com.middleware.manager.web.api.dto.ReleaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/releases")
public class PublicReleaseApiController {
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;

    private final ReleaseService releaseService;

    public PublicReleaseApiController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    public PageResponse<ReleaseResponse> list(@RequestParam(defaultValue = "") String keyword,
                                              @RequestParam(defaultValue = "") String platform,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        Page<ReleaseAsset> releasesPage = releaseService.listPublishedReleases(keyword, platform, page, normalizeSize(size));
        List<ReleaseResponse> content = releasesPage.getContent().stream()
                .map(ReleaseResponse::from)
                .collect(Collectors.toList());
        return PageResponse.from(releasesPage, content);
    }

    @GetMapping("/{token}")
    public ReleaseResponse detail(@PathVariable String token) {
        try {
            return ReleaseResponse.from(releaseService.getPublishedRelease(token));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
