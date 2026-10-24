package com.it.live.controller;

import com.it.live.entity.WebResDTO;
import com.ti.live.vod.dto.VodVideoDTO;
import com.ti.live.vod.service.IVodVideoService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点播视频 HTTP 接口 (通过网关 /api/vod/** 访问)
 */
@RestController
@RequestMapping("/api/vod")
public class VodController {

    @DubboReference
    private IVodVideoService vodVideoService;

    @GetMapping("/list")
    public WebResDTO list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return WebResDTO.success(vodVideoService.getVideoList(page, size));
    }

    @GetMapping("/search")
    public WebResDTO search(@RequestParam String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return WebResDTO.success(vodVideoService.searchVideos(keyword, page, size));
    }

    @GetMapping("/info")
    public WebResDTO info(@RequestParam Long videoId) {
        return WebResDTO.success(vodVideoService.getVideoInfo(videoId));
    }

    @PostMapping("/upload")
    public WebResDTO upload(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                            @RequestParam String title,
                            @RequestParam(required = false) String description,
                            @RequestParam String videoUrl,
                            @RequestParam(required = false) String coverUrl,
                            @RequestParam(required = false) Integer duration) {
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            return new WebResDTO(WebResDTO.FAIL, "X-User-Id header is missing");
        }
        Long userId = Long.parseLong(userIdHeader);
        VodVideoDTO dto = vodVideoService.uploadVideo(userId, title, description, videoUrl, coverUrl, duration);
        return WebResDTO.success(dto);
    }
}
