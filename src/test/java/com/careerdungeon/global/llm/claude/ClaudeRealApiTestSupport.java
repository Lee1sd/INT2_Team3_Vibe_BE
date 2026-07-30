package com.careerdungeon.global.llm.claude;

/**
 * Claude 실 API 수동 테스트가 배포 서버와 같은 환경변수 우선순위를 공유하도록 돕는다.
 */
final class ClaudeRealApiTestSupport {

    private ClaudeRealApiTestSupport() {
    }

    /** 후보 환경변수 중 처음 설정된 값을 반환하며 비밀값 자체는 출력하지 않는다. */
    static String firstConfiguredEnvironment(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException(String.join(" 또는 ", names) + " 환경변수가 필요합니다.");
    }

    /** 지정한 환경변수가 비어 있으면 프로젝트의 확정 기본값을 반환한다. */
    static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
