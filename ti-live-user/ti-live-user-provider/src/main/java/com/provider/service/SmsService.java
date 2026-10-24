package com.provider.service;

import com.provider.entity.SmsDO;
import com.provider.mapper.SmsMapper;
import com.ti.user.dto.MsgCheckDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsService {

    @Resource
    private SmsMapper smsMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final String SMS_CODE_KEY_PREFIX = "sms:login:code:";
    private static final String SMS_COOLDOWN_KEY_PREFIX = "sms:login:cooldown:";
    private static final int CODE_EXPIRE_SECONDS = 300;
    private static final int COOLDOWN_SECONDS = 60;

    public boolean sendLoginCode(String mobile) {
        if (mobile == null || mobile.isEmpty()) {
            return false;
        }

        // 频控: 60秒内不允许重复发送
        String cooldownKey = SMS_COOLDOWN_KEY_PREFIX + mobile;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.warn("验证码发送过于频繁, mobile={}", mobile);
            return false;
        }

        int code = 100000 + new Random().nextInt(900000);

        SmsDO smsDO = new SmsDO();
        smsDO.setPhone(mobile);
        smsDO.setCode(code);
        smsDO.setSceneType(1);
        smsDO.setStatus(0);
        smsDO.setExpireTime(LocalDateTime.now().plusMinutes(5));

        int rows = smsMapper.insert(smsDO);
        if (rows > 0) {
            String redisKey = SMS_CODE_KEY_PREFIX + mobile;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(code), CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        }

        return rows > 0;
    }

    public MsgCheckDTO checkLoginCode(String mobile, int code) {
        String redisKey = SMS_CODE_KEY_PREFIX + mobile;
        Object cachedCode = redisTemplate.opsForValue().get(redisKey);

        if (cachedCode == null) {
            return new MsgCheckDTO(false, "验证码已过期");
        }

        if (!String.valueOf(code).equals(cachedCode.toString())) {
            return new MsgCheckDTO(false, "验证码错误");
        }

        // 校验通过后立即删除, 防止验证码重放
        redisTemplate.delete(redisKey);
        return new MsgCheckDTO(true, "验证通过");
    }
}
