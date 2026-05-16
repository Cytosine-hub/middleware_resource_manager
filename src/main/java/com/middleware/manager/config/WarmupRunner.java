package com.middleware.manager.config;

import com.middleware.manager.repository.*;
import com.middleware.manager.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class WarmupRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(WarmupRunner.class);

    private final ReleaseAssetRepository releaseRepository;
    private final SoftwareTypeRepository typeRepository;
    private final StandardDocumentRepository docRepository;
    private final StandardParameterRepository paramRepository;
    private final ForumPostRepository forumRepository;
    private final ForumTagRepository tagRepository;
    private final ReleaseService releaseService;
    private final StandardDocumentService docService;
    private final ForumService forumService;

    public WarmupRunner(ReleaseAssetRepository releaseRepository, SoftwareTypeRepository typeRepository,
                        StandardDocumentRepository docRepository, StandardParameterRepository paramRepository,
                        ForumPostRepository forumRepository, ForumTagRepository tagRepository,
                        ReleaseService releaseService, StandardDocumentService docService,
                        ForumService forumService) {
        this.releaseRepository = releaseRepository;
        this.typeRepository = typeRepository;
        this.docRepository = docRepository;
        this.paramRepository = paramRepository;
        this.forumRepository = forumRepository;
        this.tagRepository = tagRepository;
        this.releaseService = releaseService;
        this.docService = docService;
        this.forumService = forumService;
    }

    @Override
    public void run(ApplicationArguments args) {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                LOG.info("Warming up...");

                typeRepository.findDistinctCategories();
                typeRepository.findByActiveTrueOrderByCategoryAscNameAsc();
                releaseService.listPublishedReleases("", "", 0, 1);
                releaseService.listAdminReleases("", "", null, null, 0, 1);

                docService.listPublishedStandards();
                java.util.List<com.middleware.manager.domain.StandardDocument> allDocs = docService.list("", "", "", null);
                if (!allDocs.isEmpty()) {
                    com.middleware.manager.domain.StandardDocument doc = allDocs.get(0);
                    docService.render(doc);
                    docService.listPublishedRelatedDocuments(doc.getId());
                    paramRepository.findByActiveTrueOrderByCategoryAscCodeAsc();
                }

                forumService.listPosts("", "", 0, 1);
                forumService.getAllTags();
                if (forumService.listPosts("", "", 0, 1).getTotalElements() > 0) {
                    java.util.List<com.middleware.manager.domain.ForumPost> posts = forumService.listPosts("", "", 0, 1).getContent();
                    if (!posts.isEmpty()) {
                        forumService.getComments(posts.get(0).getId());
                    }
                }

                LOG.info("Warmup complete");
            } catch (Exception e) {
                LOG.warn("Warmup error (non-fatal): {}", e.getMessage());
            }
        }, "warmup-thread").start();
    }
}
