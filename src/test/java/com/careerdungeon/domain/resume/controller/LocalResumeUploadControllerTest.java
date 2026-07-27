package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.resume.service.LocalResumeFileStorage;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalResumeUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "resume.storage.mode=local")
class LocalResumeUploadControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LocalResumeFileStorage localResumeFileStorage;

    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    UserRepository userRepository;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadsRawBytesWithAuthenticatedUserAndIssuedToken() throws Exception {
        byte[] bytes = "%PDF-local-demo".getBytes();

        mockMvc.perform(put("/api/resumes/local-upload/token-1")
                        .contentType("application/pdf")
                        .content(bytes))
                .andExpect(status().isNoContent());

        verify(localResumeFileStorage).upload(1L, "token-1", "application/pdf", bytes);
    }
}
