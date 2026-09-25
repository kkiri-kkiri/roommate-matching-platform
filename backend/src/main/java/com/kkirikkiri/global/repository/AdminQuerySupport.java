package com.kkirikkiri.global.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** 관리자 조회 공통 실행부. JPQL 조각은 코드 상수만 사용하고 입력값은 모두 바인딩한다. */
public abstract class AdminQuerySupport {
	protected final EntityManager entityManager;

	protected AdminQuerySupport(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	protected static String keyword(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	protected static Map<String, Object> parameters(Object... pairs) {
		Map<String, Object> result = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			result.put((String) pairs[i], pairs[i + 1]);
		}
		return result;
	}

	protected <T> TypedQuery<T> query(String jpql, Class<T> type, Map<String, Object> parameters) {
		TypedQuery<T> query = entityManager.createQuery(jpql, type);
		parameters.forEach(query::setParameter);
		return query;
	}

	/** 정렬은 각 조회의 명시적 ORDER BY를 사용한다. Pageable은 offset/size에만 적용한다. */
	protected <T> Page<T> page(String content, String count, Class<T> type,
			Map<String, Object> parameters, Pageable pageable) {
		long total = count(count, parameters);
		TypedQuery<T> query = query(content, type, parameters);
		if (pageable.isPaged()) {
			query.setFirstResult(Math.toIntExact(pageable.getOffset()));
			query.setMaxResults(pageable.getPageSize());
		}
		return new PageImpl<>(query.getResultList(), pageable, total);
	}

	protected long count(String jpql, Map<String, Object> parameters) {
		return query(jpql, Long.class, parameters).getSingleResult();
	}

	protected <T> List<T> recent(String jpql, Class<T> type, Map<String, Object> parameters) {
		return query(jpql, type, parameters).setMaxResults(3).getResultList();
	}

	protected <T> Optional<T> one(String jpql, Class<T> type, Map<String, Object> parameters) {
		return query(jpql, type, parameters).getResultList().stream().findFirst();
	}
}
