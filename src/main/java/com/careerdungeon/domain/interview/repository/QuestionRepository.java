package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.interview.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
}
