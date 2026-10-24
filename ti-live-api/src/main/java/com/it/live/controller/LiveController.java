package com.it.live.controller;

import com.it.live.entity.WebResDTO;
import com.ti.live.live.service.ILiveStreamService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直播流 HTTP 接口 (通过网关 /api/live/** 访问)
 */
@RestController
@RequestMapping("/api/live")
public class LiveController {

    @DubboReference
    private ILiveStreamService liveStreamService;

    @GetMapping("/list")
    public WebResDTO list() {
        return WebResDTO.success(liveStreamService.getLiveStreams());
    }

    @GetMapping("/info")
    public WebResDTO info(@RequestParam Long streamId) {
        return WebResDTO.success(liveStreamService.getLiveStreamInfo(streamId));
    }

    @PostMapping("/create")
    public WebResDTO create(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                            @RequestParam String title,
                            @RequestParam String roomName) {
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            return new WebResDTO(WebResDTO.FAIL, "X-User-Id header is missing");
        }
        return WebResDTO.success(liveStreamService.createLiveStream(Long.parseLong(userIdHeader), title, roomName));
    }

    @PostMapping("/start")
    public WebResDTO start(@RequestParam Long streamId) {
        return WebResDTO.success(liveStreamService.startLiveStream(streamId));
    }

    @PostMapping("/end")
    public WebResDTO end(@RequestParam Long streamId) {
        return WebResDTO.success(liveStreamService.endLiveStream(streamId));
    }
}
