package com.ti.id.provider.impl;

import com.ti.id.dto.BatchIdGenerateRequest;
import com.ti.id.dto.BatchIdGenerateResponse;
import com.ti.id.dto.IdGenerateRequest;
import com.ti.id.dto.IdGenerateResponse;
import com.ti.id.provider.util.SnowflakeIdGenerator;
import com.ti.id.service.IIdGenerateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@DubboService
public class IdGenerateProviderService implements IIdGenerateService {

    /** 雪花算法 ID 生成器（单例，全局共享） */
    private final SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(0, 0);

    private final Random random = new Random();

    @Override
    public IdGenerateResponse generateId(IdGenerateRequest request) {
        log.info("开始生成ID, businessName={}, stringFormat={}", request.getBusinessName(), request.isStringFormat());

        IdGenerateResponse response = new IdGenerateResponse();
        response.setBusinessName(request.getBusinessName());

        if (request.isStringFormat()) {
            String idStr = snowflakeIdGenerator.nextIdStr();
            response.setIdStr(idStr);
            response.setStringFormat(true);
            log.info("生成字符串ID成功: {}", idStr);
        } else {
            long id = snowflakeIdGenerator.nextId();
            response.setId(id);
            response.setStringFormat(false);
            log.info("生成数字ID成功: {}", id);
        }

        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    @Override
    public BatchIdGenerateResponse batchGenerateId(BatchIdGenerateRequest request) {
        log.info("开始批量生成ID, businessName={}, count={}", request.getBusinessName(), request.getCount());

        int count = Math.min(request.getCount(), 1000);
        List<IdGenerateResponse> results = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            IdGenerateRequest req = new IdGenerateRequest();
            req.setBusinessName(request.getBusinessName());
            req.setStringFormat(request.isStringFormat());
            results.add(generateId(req));
        }

        BatchIdGenerateResponse response = new BatchIdGenerateResponse();
        response.setIds(results);
        response.setCount(results.size());
        response.setBusinessName(request.getBusinessName());

        log.info("批量生成ID完成, 数量={}", results.size());
        return response;
    }

    @Override
    public String generateIdAsString(String businessName) {
        String id = snowflakeIdGenerator.nextIdStr();
        log.info("生成字符串ID成功: {}, businessName={}", id, businessName);
        return id;
    }

    @Override
    public long generateIdAsLong(String businessName) {
        long id = snowflakeIdGenerator.nextId();
        log.info("生成数字ID成功: {}, businessName={}", id, businessName);
        return id;
    }

    @Override
    public String generateSequentialOrderId(String prefix) {
        // 生成序列化订单ID: 前缀 + 年月日时分秒 + 6位随机数
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomStr = String.format("%06d", random.nextInt(1000000));
        String orderId = prefix + timestamp + randomStr;
        log.info("生成序列化订单ID成功: {}", orderId);
        return orderId;
    }

    @Override
    public String generateRandomOrderId(String prefix) {
        // 生成无规律订单ID: 前缀 + 32位随机字符串
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 32; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        String orderId = sb.toString();
        log.info("生成无规律订单ID成功: {}", orderId);
        return orderId;
    }

    @Override
    public long generateSnowflakeId(String businessName) {
        long id = snowflakeIdGenerator.nextId();
        log.info("生成雪花算法ID成功: {}, businessName={}", id, businessName);
        return id;
    }

    @Override
    public long generateSegmentId(String businessName) {
        // 号段算法由雪花算法统一替代, 保证 ID 全局唯一
        long id = snowflakeIdGenerator.nextId();
        log.info("生成号段算法ID成功: {}, businessName={}", id, businessName);
        return id;
    }

    @Override
    public String generateDistributedStringId(String businessName) {
        String id = snowflakeIdGenerator.nextIdStr();
        log.info("生成分布式字符串ID成功: {}, businessName={}", id, businessName);
        return id;
    }
}
