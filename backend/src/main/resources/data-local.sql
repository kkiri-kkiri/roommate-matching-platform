-- Local reference data from frontend/src/data/facultyOptions.js and SignupPage.js.
-- Insert missing rows only; preserve existing IDs and active flags.

INSERT INTO dormitory (dormitory_code, dormitory_name, is_active)
SELECT 'NARAE', '나래', true
WHERE NOT EXISTS (SELECT 1 FROM dormitory WHERE dormitory_name = '나래');

INSERT INTO dormitory (dormitory_code, dormitory_name, is_active)
SELECT 'HOYEON', '호연', true
WHERE NOT EXISTS (SELECT 1 FROM dormitory WHERE dormitory_name = '호연');

INSERT INTO dormitory (dormitory_code, dormitory_name, is_active)
SELECT 'BIBONG', '비봉', true
WHERE NOT EXISTS (SELECT 1 FROM dormitory WHERE dormitory_name = '비봉');

INSERT INTO dormitory (dormitory_code, dormitory_name, is_active)
SELECT 'CHANGJO', '창조', true
WHERE NOT EXISTS (SELECT 1 FROM dormitory WHERE dormitory_name = '창조');

INSERT INTO faculty (faculty_name, is_active)
SELECT '인문융합공공인재학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '인문융합공공인재학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '문예창작미디어콘텐츠홍보전공', true FROM faculty f
WHERE f.faculty_name = '인문융합공공인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '문예창작미디어콘텐츠홍보전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '영미언어문화전공', true FROM faculty f
WHERE f.faculty_name = '인문융합공공인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '영미언어문화전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '행정학전공', true FROM faculty f
WHERE f.faculty_name = '인문융합공공인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '행정학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '노동복지전공', true FROM faculty f
WHERE f.faculty_name = '인문융합공공인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '노동복지전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '법경영학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '법경영학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '법학전공', true FROM faculty f
WHERE f.faculty_name = '법경영학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '법학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '경영학전공', true FROM faculty f
WHERE f.faculty_name = '법경영학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '경영학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '웰니스산업융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '웰니스산업융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '의류산업학전공', true FROM faculty f
WHERE f.faculty_name = '웰니스산업융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '의류산업학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '복지상담학전공', true FROM faculty f
WHERE f.faculty_name = '웰니스산업융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '복지상담학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '식품영양학전공', true FROM faculty f
WHERE f.faculty_name = '웰니스산업융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '식품영양학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '식물자원조경학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '식물자원조경학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '식물생명환경전공', true FROM faculty f
WHERE f.faculty_name = '식물자원조경학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '식물생명환경전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '조경학전공', true FROM faculty f
WHERE f.faculty_name = '식물자원조경학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '조경학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '동물생명융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '동물생명융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '동물생명과학전공', true FROM faculty f
WHERE f.faculty_name = '동물생명융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '동물생명과학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '동물응용과학전공', true FROM faculty f
WHERE f.faculty_name = '동물생명융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '동물응용과학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '생명공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '생명공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '원예생명공학전공', true FROM faculty f
WHERE f.faculty_name = '생명공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '원예생명공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '응용생명공학전공', true FROM faculty f
WHERE f.faculty_name = '생명공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '응용생명공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '건설환경공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '건설환경공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '토목공학전공', true FROM faculty f
WHERE f.faculty_name = '건설환경공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '토목공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '환경공학전공', true FROM faculty f
WHERE f.faculty_name = '건설환경공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '환경공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '사회안전시스템공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '사회안전시스템공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '안전공학전공', true FROM faculty f
WHERE f.faculty_name = '사회안전시스템공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '안전공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '지역자원시스템공학전공', true FROM faculty f
WHERE f.faculty_name = '사회안전시스템공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '지역자원시스템공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '식품생명화학공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '식품생명화학공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '식품생명공학전공', true FROM faculty f
WHERE f.faculty_name = '식품생명화학공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '식품생명공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '화학공학전공', true FROM faculty f
WHERE f.faculty_name = '식품생명화학공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '화학공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '컴퓨터응용수학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '컴퓨터응용수학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '소프트웨어&서비스컴퓨팅전공', true FROM faculty f
WHERE f.faculty_name = '컴퓨터응용수학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '소프트웨어&서비스컴퓨팅전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '소프트웨어융합전공', true FROM faculty f
WHERE f.faculty_name = '컴퓨터응용수학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '소프트웨어융합전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '정보보안전공', true FROM faculty f
WHERE f.faculty_name = '컴퓨터응용수학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '정보보안전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '응용수학전공', true FROM faculty f
WHERE f.faculty_name = '컴퓨터응용수학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '응용수학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT 'ICT로봇기계공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = 'ICT로봇기계공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'ICT로봇공학전공', true FROM faculty f
WHERE f.faculty_name = 'ICT로봇기계공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'ICT로봇공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '기계공학전공', true FROM faculty f
WHERE f.faculty_name = 'ICT로봇기계공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '기계공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '전자전기공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '전자전기공학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '전자공학전공', true FROM faculty f
WHERE f.faculty_name = '전자전기공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '전자공학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '전기공학전공', true FROM faculty f
WHERE f.faculty_name = '전자전기공학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '전기공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '건축융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '건축융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '건축학전공(5년제)', true FROM faculty f
WHERE f.faculty_name = '건축융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '건축학전공(5년제)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '건축공학전공', true FROM faculty f
WHERE f.faculty_name = '건축융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '건축공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '디자인예술스포츠학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '디자인예술스포츠학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '스포츠과학전공', true FROM faculty f
WHERE f.faculty_name = '디자인예술스포츠학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '스포츠과학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '시각미디어디자인전공', true FROM faculty f
WHERE f.faculty_name = '디자인예술스포츠학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '시각미디어디자인전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '제품공간디자인전공', true FROM faculty f
WHERE f.faculty_name = '디자인예술스포츠학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '제품공간디자인전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '실용음악전공', true FROM faculty f
WHERE f.faculty_name = '디자인예술스포츠학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '실용음악전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '귀금속보석공예전공', true FROM faculty f
WHERE f.faculty_name = '디자인예술스포츠학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '귀금속보석공예전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '복지융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '복지융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '사회복지학전공', true FROM faculty f
WHERE f.faculty_name = '복지융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '사회복지학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '한국수어교육전공', true FROM faculty f
WHERE f.faculty_name = '복지융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '한국수어교육전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '유아특수보육학전공', true FROM faculty f
WHERE f.faculty_name = '복지융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '유아특수보육학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '의료재활공학전공', true FROM faculty f
WHERE f.faculty_name = '복지융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '의료재활공학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '사회통합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '사회통합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '공공행정전공', true FROM faculty f
WHERE f.faculty_name = '사회통합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '공공행정전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '상담심리교육전공', true FROM faculty f
WHERE f.faculty_name = '사회통합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '상담심리교육전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '특수체육학전공', true FROM faculty f
WHERE f.faculty_name = '사회통합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '특수체육학전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT 'AI반도체융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = 'AI반도체융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'AI반도체융합전공', true FROM faculty f
WHERE f.faculty_name = 'AI반도체융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'AI반도체융합전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT 'HK자율전공학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = 'HK자율전공학부');

INSERT INTO faculty (faculty_name, is_active)
SELECT '글로벌창의인재학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '글로벌창의인재학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '글로벌스포츠매니지먼트전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '글로벌스포츠매니지먼트전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '글로벌경영전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '글로벌경영전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '글로벌인공지능IT전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '글로벌인공지능IT전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '식물스마트팜전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '식물스마트팜전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '식물생의학전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '식물생의학전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '바이오에너지전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '바이오에너지전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '아시아문화경영전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '아시아문화경영전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'Bio-Mobility전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'Bio-Mobility전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'English Language, Literature and Language Technology전공', true FROM faculty f
WHERE f.faculty_name = '글로벌창의인재학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'English Language, Literature and Language Technology전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT '미래융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = '미래융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '노동복지전공', true FROM faculty f
WHERE f.faculty_name = '미래융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '노동복지전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '행정관리전공', true FROM faculty f
WHERE f.faculty_name = '미래융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '행정관리전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '레저스포츠매니지먼트전공', true FROM faculty f
WHERE f.faculty_name = '미래융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '레저스포츠매니지먼트전공');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '융합레포츠전공', true FROM faculty f
WHERE f.faculty_name = '미래융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '융합레포츠전공');

INSERT INTO faculty (faculty_name, is_active)
SELECT 'AI융합학부', true
WHERE NOT EXISTS (SELECT 1 FROM faculty WHERE faculty_name = 'AI융합학부');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'AI스마트팜융합전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'AI스마트팜융합전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '스마트헬스케어전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '스마트헬스케어전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, 'AI빅데이터융합전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = 'AI빅데이터융합전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '공공헬스케어IT융합전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '공공헬스케어IT융합전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '반도체융합전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '반도체융합전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '스마트애그리푸드시스템전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '스마트애그리푸드시스템전공(융합전공)');

INSERT INTO major (faculty_id, major_name, is_active)
SELECT f.faculty_id, '탄소중립바이오시스템융합전공(융합전공)', true FROM faculty f
WHERE f.faculty_name = 'AI융합학부'
AND NOT EXISTS (SELECT 1 FROM major m WHERE m.faculty_id = f.faculty_id AND m.major_name = '탄소중립바이오시스템융합전공(융합전공)');

