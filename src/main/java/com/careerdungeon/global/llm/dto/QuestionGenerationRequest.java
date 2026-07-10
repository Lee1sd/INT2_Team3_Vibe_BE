package com.careerdungeon.global.llm.dto;

/**
 * @param resumeText  마스킹 처리된 이력서 추출 텍스트 (Resume.extractedText)
 * @param keyword     사용자가 선택한 키워드 (FR-02, 6종 중 하나)
 * @param personaTone 면접관 톤 (PersonaConfig.tone — 예: "LENIENT", "STRICT")
 * @param userName    사용자 이름 — 질문/피드백 개인화에 사용 (FR-12)
 */
public record QuestionGenerationRequest(
        String resumeText,
        String keyword,
        String personaTone,
        String userName
) {}
