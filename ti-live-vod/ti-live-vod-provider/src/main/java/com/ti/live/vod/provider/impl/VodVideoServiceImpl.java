package com.ti.live.vod.provider.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ti.live.vod.dto.VodVideoDTO;
import com.ti.live.vod.entity.VodVideo;
import com.ti.live.vod.mapper.VodVideoMapper;
import com.ti.live.vod.service.IVodVideoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@DubboService
public class VodVideoServiceImpl implements IVodVideoService {

    private static final String VIDEO_CACHE_KEY_PREFIX = "vod:video:";
    private static final String PLAY_COUNT_KEY_PREFIX = "vod:playCount:";
    private static final long VIDEO_CACHE_TTL_MINUTES = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private VodVideoMapper vodVideoMapper;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Override
    public VodVideoDTO uploadVideo(Long userId, String title, String description, String videoUrl, String coverUrl, Integer duration) {
        VodVideo video = new VodVideo();
        video.setUserId(userId);
        video.setUserName("用户" + userId); // 实际应从用户服务获取
        video.setTitle(title);
        video.setDescription(description);
        video.setVideoUrl(videoUrl);
        video.setCoverUrl(coverUrl);
        video.setDuration(duration);
        video.setPlayCount(0);
        video.setStatus(0); // 待审核
        video.setCreateTime(LocalDateTime.now());
        video.setUpdateTime(LocalDateTime.now());

        vodVideoMapper.insert(video);
        log.info("上传视频成功: videoId={}, userId={}", video.getVideoId(), userId);

        return convertToDTO(video);
    }

    @Override
    public VodVideoDTO getVideoInfo(Long videoId) {
        // 先从缓存获取
        String cacheKey = VIDEO_CACHE_KEY_PREFIX + videoId;
        String cachedVideo = redisTemplate.opsForValue().get(cacheKey);
        if (cachedVideo != null) {
            try {
                return objectMapper.readValue(cachedVideo, VodVideoDTO.class);
            } catch (Exception e) {
                log.warn("解析视频缓存失败, videoId={}", videoId);
            }
        }

        VodVideo video = vodVideoMapper.selectById(videoId);
        if (video == null) {
            throw new RuntimeException("视频不存在");
        }

        // 写入缓存, 30分钟过期
        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(convertToDTO(video)),
                    VIDEO_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入视频缓存失败, videoId={}", videoId);
        }

        return convertToDTO(video);
    }

    @Override
    public VodVideoDTO updateVideo(Long videoId, String title, String description, String tags) {
        VodVideo video = vodVideoMapper.selectById(videoId);
        if (video == null) {
            throw new RuntimeException("视频不存在");
        }

        video.setTitle(title);
        video.setDescription(description);
        video.setTags(tags);
        video.setUpdateTime(LocalDateTime.now());
        vodVideoMapper.updateById(video);

        // 清除缓存
        redisTemplate.delete(VIDEO_CACHE_KEY_PREFIX + videoId);

        log.info("更新视频信息: videoId={}", videoId);
        return convertToDTO(video);
    }

    @Override
    public void deleteVideo(Long videoId) {
        VodVideo video = vodVideoMapper.selectById(videoId);
        if (video == null) {
            throw new RuntimeException("视频不存在");
        }

        vodVideoMapper.deleteById(videoId);

        // 清除缓存
        redisTemplate.delete(VIDEO_CACHE_KEY_PREFIX + videoId);

        log.info("删除视频: videoId={}", videoId);
    }

    @Override
    public VodVideoDTO auditVideo(Long videoId, Integer status) {
        VodVideo video = vodVideoMapper.selectById(videoId);
        if (video == null) {
            throw new RuntimeException("视频不存在");
        }

        video.setStatus(status);
        video.setUpdateTime(LocalDateTime.now());
        vodVideoMapper.updateById(video);

        // 清除缓存
        redisTemplate.delete(VIDEO_CACHE_KEY_PREFIX + videoId);

        log.info("审核视频: videoId={}, status={}", videoId, status);
        return convertToDTO(video);
    }

    @Override
    public void incrementPlayCount(Long videoId) {
        // 使用Redis原子操作增加播放次数, 并设置过期时间防止键无限膨胀
        String playCountKey = PLAY_COUNT_KEY_PREFIX + videoId;
        redisTemplate.opsForValue().increment(playCountKey);
        redisTemplate.expire(playCountKey, 1, TimeUnit.DAYS);

        // 定期同步到数据库（这里简化处理，直接更新）
        VodVideo video = vodVideoMapper.selectById(videoId);
        if (video != null) {
            video.setPlayCount(video.getPlayCount() + 1);
            vodVideoMapper.updateById(video);
        }
    }

    @Override
    public List<VodVideoDTO> getVideoList(int page, int size) {
        Page<VodVideo> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<VodVideo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VodVideo::getStatus, 1) // 已发布
                .orderByDesc(VodVideo::getCreateTime);

        Page<VodVideo> result = vodVideoMapper.selectPage(pageInfo, wrapper);
        return result.getRecords().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<VodVideoDTO> getUserVideos(Long userId, int page, int size) {
        Page<VodVideo> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<VodVideo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VodVideo::getUserId, userId)
                .orderByDesc(VodVideo::getCreateTime);

        Page<VodVideo> result = vodVideoMapper.selectPage(pageInfo, wrapper);
        return result.getRecords().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<VodVideoDTO> searchVideos(String keyword, int page, int size) {
        Page<VodVideo> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<VodVideo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VodVideo::getStatus, 1) // 已发布
                .and(query -> query
                        .like(VodVideo::getTitle, keyword)
                        .or().like(VodVideo::getDescription, keyword)
                        .or().like(VodVideo::getTags, keyword)
                )
                .orderByDesc(VodVideo::getPlayCount)
                .orderByDesc(VodVideo::getCreateTime);

        Page<VodVideo> result = vodVideoMapper.selectPage(pageInfo, wrapper);
        return result.getRecords().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private VodVideoDTO convertToDTO(VodVideo video) {
        VodVideoDTO dto = new VodVideoDTO();
        BeanUtils.copyProperties(video, dto);
        return dto;
    }
}
