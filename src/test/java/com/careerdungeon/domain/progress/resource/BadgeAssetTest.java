package com.careerdungeon.domain.progress.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** seed의 이미지 URL과 함께 배포되는 Level1~4 PNG 자산을 검증한다. */
class BadgeAssetTest {

    /** 각 정적 자산이 손상되지 않은 1254px 정사각 PNG인지 검증한다. */
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
}
