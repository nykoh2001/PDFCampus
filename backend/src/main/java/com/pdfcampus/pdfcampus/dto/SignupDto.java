package com.pdfcampus.pdfcampus.dto;

import com.pdfcampus.pdfcampus.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.time.LocalDate;

@AllArgsConstructor
@ToString
@Setter
@Getter
public class SignupDto {
    private int uid;
    private String userId;
    private String password;
    private String username;
    private boolean isSubscribed;
    private String productName;
    private LocalDate subscribeDate;
    private LocalDate joinedDate;
    private String refreshToken;
    private String accessToken;

    public User toEntity() {
        return new User(uid, userId, password, username, isSubscribed, productName, subscribeDate, joinedDate, refreshToken);
    }
}