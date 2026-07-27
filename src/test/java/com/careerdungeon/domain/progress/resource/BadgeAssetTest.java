package com.careerdungeon.domain.progress.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** S3 업로드 원본으로 보관하는 Level1~5 PNG 자산을 검증한다. */
class BadgeAssetTest {

    /** 기존 Level1~4 업로드 원본이 손상되지 않은 1254px 정사각 PNG인지 검증한다. */
    @ParameterizedTest
    @ValueSource(strings = {"Level1.png", "Level2.png", "Level3.png", "Level4.png"})
    @DisplayName("Level1~4 뱃지 이미지는 1254x1254 PNG로 배포된다")
    void badgeAssetIsReadableSquarePng(String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/badges/" + fileName);

        assertThat(resource.exists()).isTrue();
        try (var inputStream = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isEqualTo(1254);
            assertThat(image.getHeight()).isEqualTo(1254);
        }
    }

    /** Stage5 최종 뱃지 원본이 클래스패스에 존재하고 디코딩 가능한지 검증한다. */
    @Test
    @DisplayName("Level5 뱃지 이미지는 배포 경로에 존재한다")
    void level5BadgeAssetExists() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/badges/Level5.png");

        assertThat(resource.exists()).isTrue();
        try (var inputStream = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isPositive();
            assertThat(image.getHeight()).isPositive();
        }
    }
}
