package com.middleware.manager.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FileDownloadControllerTest {

    @Mock
    private ReleaseService releaseService;

    @Mock
    private StorageService storageService;

    private FileDownloadController controller;
    private ReleaseAsset release;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new FileDownloadController(releaseService, storageService);
        release = new ReleaseAsset();
        release.setId(1L);
        release.setStoredFileName("stored.bin");
        release.setOriginalFileName("download.bin");
        release.setContentType("application/octet-stream");
        release.setFileSize(10L);
        when(releaseService.getPublishedRelease("token")).thenReturn(release);
        when(storageService.loadAsResource("stored.bin"))
                .thenReturn(new ByteArrayResource(new byte[10]));
    }

    @Test
    @DisplayName("TC-CATALOG-001 Range 非法时不增加下载计数")
    void invalidRangeDoesNotIncrementDownloadCount() {
        ResponseEntity<?> response = controller.download("token", "bytes=20-30");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        verify(releaseService, never()).incrementDownloadCount(release);
    }

    @Test
    @DisplayName("TC-CATALOG-002 文件校验通过后才增加下载计数")
    void validDownloadIncrementsCountAfterResourceValidation() {
        ResponseEntity<?> response = controller.download("token", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InOrder inOrder = inOrder(storageService, releaseService);
        inOrder.verify(storageService).loadAsResource("stored.bin");
        inOrder.verify(releaseService).incrementDownloadCount(release);
    }
}
