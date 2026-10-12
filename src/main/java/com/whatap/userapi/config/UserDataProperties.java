package com.whatap.userapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("user-api.data")
public record UserDataProperties(@NotBlank String filePath) {
}
