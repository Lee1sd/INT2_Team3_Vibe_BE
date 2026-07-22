package com.careerdungeon.domain.resume.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ResumeFileStorage {

    public void delete(String s3Key) throws IOException {
        Files.deleteIfExists(Path.of(s3Key));
    }
}
