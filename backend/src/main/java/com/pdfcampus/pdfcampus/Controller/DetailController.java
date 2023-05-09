package com.pdfcampus.pdfcampus.Controller;

import com.pdfcampus.pdfcampus.dto.DetailBookDto;
import com.pdfcampus.pdfcampus.dto.MypageDto;
import com.pdfcampus.pdfcampus.service.DetailService;
import com.pdfcampus.pdfcampus.service.MypageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DetailController {
    private final DetailService detailService;

    public DetailController(DetailService detailService) {
        this.detailService = detailService;
    }

    @GetMapping("/book/detail/{bookId}")
    public ResponseEntity<Map<String, Object>> getDetailBookData(@PathVariable String bookId) {
        try {
            DetailBookDto DetailBookData = DetailService.getBookData(bookId);
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> apiStatus = new HashMap<>();
            Map<String, Object> responseData = new LinkedHashMap<>();
            Map<String, Object> subscribeInfo = new LinkedHashMap<>();

            if(mypageData.isSubscribed()){ //구독한 사용자에 경우 subscribeInfo 구성
                subscribeInfo.put("productName", mypageData.getProductName());
                subscribeInfo.put("subscribeDate", mypageData.getSubscribeDate());
            }

            responseData.put("username", mypageData.getUsername());
            responseData.put("isSubscribed", mypageData.isSubscribed());
            responseData.put("joinedDate", mypageData.getJoinedDate());
            responseData.put("subscribedInfo", subscribeInfo);

            response.put("data", responseData);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 기타 예외가 발생한 경우
            Map<String, Object> responseBody = new LinkedHashMap<>();

            // 500
            Map<String, String> apiStatus = new HashMap<>();
            apiStatus.put("errorMessage", "서버 오류가 발생했습니다.");
            apiStatus.put("errorCode", "N500");
            responseBody.put("apiStatus", apiStatus);

            return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
