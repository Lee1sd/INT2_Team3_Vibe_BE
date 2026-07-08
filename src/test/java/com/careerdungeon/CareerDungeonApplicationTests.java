package com.careerdungeon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Career Dungeon의 Spring 애플리케이션 컨텍스트가 정상적으로 시작되는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CareerDungeonApplicationTests {

    @Test
    void contextLoads() {
        // 컨텍스트 생성 중 예외가 없으면 기본 구성이 유효하다.
    }
}
