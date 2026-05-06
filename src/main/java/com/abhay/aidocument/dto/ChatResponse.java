package com.abhay.aidocument.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatResponse {

    private String answer;
    private Double timestamp;
}