package com.copilot.auth.dto;

import com.copilot.auth.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @ValidPassword String password
) {
}
