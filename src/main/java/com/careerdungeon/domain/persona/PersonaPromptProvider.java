package com.careerdungeon.domain.persona;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 페르소나 톤에 따라 System Prompt 템플릿을 로드하고 이름 등 동적 값을 주입한다.
 *
 * <p>신규 페르소나 톤을 추가할 때는 {@link PersonaTone}에 상수를 추가하고
 * {@code src/main/resources/prompts/persona/<tone>.txt} 템플릿 파일만 새로 두면 된다 —
 * 이 클래스에 톤별 분기 코드를 추가할 필요가 없다(NFR-07 스타일 엔진 추상화).
 *
 * <p>실 LLM 호출부(이슈 #8, {@code LlmClient} 구현체)에서 System Prompt를 조립할 때
 * 이 클래스를 사용할 예정이다. Mock 모드({@link com.careerdungeon.global.llm.mock.MockLlmClient})는
 * 고정 응답만 반환하므로 이 클래스를 아직 참조하지 않는다.
 */
@Component
public class PersonaPromptProvider {

    private static final String TEMPLATE_PATH_FORMAT = "prompts/persona/%s.txt";
    private static final Pattern USER_NAME_TOKEN = Pattern.compile("\\{\\{userName\\}\\}");

    private final Map<PersonaTone, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 주어진 페르소나 톤의 System Prompt를 로드하고 사용자 이름을 주입한다(FR-12).
     */
    public String systemPrompt(PersonaTone tone, String userName) {
        Objects.requireNonNull(tone, "tone must not be null");
        Objects.requireNonNull(userName, "userName must not be null");
        String template = templateCache.computeIfAbsent(tone, this::loadTemplate);
        return USER_NAME_TOKEN.matcher(template).replaceAll(Matcher.quoteReplacement(userName));
    }

    private String loadTemplate(PersonaTone tone) {
        String path = TEMPLATE_PATH_FORMAT.formatted(tone.apiValue());
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "페르소나 System Prompt 템플릿을 찾을 수 없습니다: " + path, e);
        }
    }
}
