package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/parse")
public class ParseController {

    @Autowired
    private ParseService parseService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> parseDocument(@RequestParam("file") MultipartFile file,
                                                @RequestParam("file_md5") String fileMd5,
                                                @RequestAttribute(value = "userId", required = false) String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PARSE_DOCUMENT");
        try {
            LogUtils.logBusiness("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "开始解析文档: fileMd5=%s, fileName=%s, fileSize=%d", 
                    fileMd5, file.getOriginalFilename(), file.getSize());
            
            parseService.parseAndSave(fileMd5, file.getInputStream());
            
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "SUCCESS");
            LogUtils.logUserOperation(userId != null ? userId : "system", "PARSE_DOCUMENT", 
                    fileMd5, "SUCCESS");
            monitor.end("文档解析成功");
            
            Map<String, Object> data = new HashMap<>();
            data.put("message", "文档解析成功");
            data.put("fileMd5", fileMd5);
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 200);
            resp.put("message", "ok");
            resp.put("data", data);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            LogUtils.logBusinessError("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "文档解析失败: fileMd5=%s, fileName=%s", e, fileMd5, file.getOriginalFilename());
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "FAILED");
            monitor.end("文档解析失败: " + e.getMessage());

            Map<String, Object> errData = new HashMap<>();
            errData.put("message", "文档解析失败：" + e.getMessage());
            errData.put("fileMd5", fileMd5);
            Map<String, Object> errResp = new HashMap<>();
            errResp.put("code", 400);
            errResp.put("message", "文档解析失败");
            errResp.put("data", errData);
            return ResponseEntity.badRequest().body(errResp);
        }
    }
}