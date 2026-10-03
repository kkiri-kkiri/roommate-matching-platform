package com.kkirikkiri.global.init;

import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunitySeedDataService {

    private final CommunityPostRepository communityPostRepository;
    private final JdbcTemplate jdbcTemplate;

    private record SeedPost(String title, String content) {}
    private record SeedBoard(String category, List<SeedPost> posts) {}

    private static final List<SeedBoard> BOARDS = List.of(
            new SeedBoard("자유게시판", List.of(
                    new SeedPost("기숙사 생활 꿀팁 공유해요", "이번 학기에 처음 입사했습니다. 기숙사 생활에 도움이 되는 준비물이나 팁을 알려주세요."),
                    new SeedPost("오늘 학식 메뉴 어땠나요?", "점심 학식을 먹어 보신 분 계신가요? 맛있었던 메뉴를 추천해 주세요."),
                    new SeedPost("같이 저녁 먹을 사람 구해요", "오늘 저녁에 학생식당에서 밥 먹을 분을 찾습니다. 혼자 먹기 심심해서 글 올려요."),
                    new SeedPost("학교 근처 카페 추천 부탁해요", "과제하기 좋은 조용한 카페를 찾고 있습니다. 콘센트가 있는 곳이면 더 좋아요."),
                    new SeedPost("시험 기간 공부 장소 추천", "도서관 자리가 꽉 찼네요. 늦게까지 공부할 수 있는 장소가 있을까요?"),
                    new SeedPost("주말에 산책 같이 가요", "주말 오후에 캠퍼스를 가볍게 걸으려고 합니다. 산책 좋아하는 분 같이 가요."),
                    new SeedPost("세탁실 덜 붐비는 시간 궁금해요", "저녁마다 세탁기가 사용 중이네요. 비교적 한산한 시간대를 알고 싶습니다."),
                    new SeedPost("아침 운동 함께 할 분", "수업 전에 운동장에서 가볍게 뛰려고 합니다. 부담 없이 함께 운동해요."),
                    new SeedPost("룸메이트와 생활 규칙 정하기", "청소나 취침 시간을 어떻게 정하면 좋을까요? 서로 편하게 지내는 방법을 공유해 주세요."),
                    new SeedPost("학교 주변 맛집 공유해요", "최근에 먹었던 맛있는 식당을 추천해 주세요. 학생이 가기 좋은 가격이면 좋겠습니다."),
                    new SeedPost("비 오는 날 빨래 어떻게 말리나요?", "방 안에 빨래를 널었더니 잘 마르지 않네요. 습기를 줄이는 팁이 궁금합니다."),
                    new SeedPost("기숙사 책상 정리 팁", "교재와 노트북을 놓으니 책상이 금방 꽉 찹니다. 공간을 효율적으로 쓰는 방법이 있을까요?"),
                    new SeedPost("같이 보드게임 할 분 구합니다", "주말 저녁에 휴게실에서 보드게임을 하려고 합니다. 처음 하시는 분도 환영해요."),
                    new SeedPost("공강 시간에 뭐 하시나요?", "수업 사이에 두 시간 정도 비는데 다들 어떻게 보내시는지 궁금합니다."),
                    new SeedPost("기숙사에서 쓰기 좋은 슬리퍼", "공용 공간을 오갈 때 편한 슬리퍼를 찾고 있습니다. 잘 미끄러지지 않는 제품 추천해 주세요."),
                    new SeedPost("밤에 출출할 때 간식 추천", "방에서 간단히 먹을 수 있는 간식을 찾습니다. 냄새가 강하지 않은 간식이면 좋겠어요."),
                    new SeedPost("영어 회화 스터디 모집", "일주일에 한 번 모여서 짧게 영어로 대화하려고 합니다. 초보자도 같이 연습해요."),
                    new SeedPost("분리수거 요일 확인 부탁드려요", "재활용품을 내놓는 장소와 시간을 정확히 알고 싶습니다. 아시는 분 알려주세요."),
                    new SeedPost("중간고사 끝나면 하고 싶은 것", "시험이 끝나면 무엇부터 하실 건가요? 저는 밀린 잠부터 자려고 합니다."),
                    new SeedPost("새 학기 목표 공유해요", "이번 학기에는 규칙적으로 운동하고 과제를 미리 끝내는 것이 목표입니다. 여러분의 목표도 들려주세요.")
            )),
            new SeedBoard("나눔게시판", List.of(
                    new SeedPost("깨끗한 파일철 나눔합니다", "사용하지 않는 파일철 두 개를 나눔합니다. 교재나 프린트 정리할 때 쓰기 좋아요."),
                    new SeedPost("여분 옷걸이 가져가세요", "플라스틱 옷걸이 다섯 개가 남아서 나눔합니다. 생활관 로비에서 전달할 수 있습니다."),
                    new SeedPost("전공 기초 교재 나눔", "기초 프로그래밍 교재를 나눔합니다. 필기가 조금 있지만 읽는 데 문제없습니다."),
                    new SeedPost("작은 수납 바구니 나눔", "책상 아래에 놓기 좋은 수납 바구니입니다. 깨끗하게 닦아서 전달해 드릴게요."),
                    new SeedPost("미개봉 볼펜 나눔합니다", "검정 볼펜 세 자루를 나눔합니다. 필요한 분은 댓글 남겨주세요."),
                    new SeedPost("남는 포스트잇 드려요", "사용하지 않은 포스트잇 두 묶음이 있습니다. 공부할 때 필요한 분께 드립니다."),
                    new SeedPost("책상용 휴대폰 거치대 나눔", "접이식 휴대폰 거치대를 나눔합니다. 기능에는 이상 없고 작은 사용 흔적이 있습니다."),
                    new SeedPost("세탁망 나눔합니다", "여분으로 구매한 세탁망 한 개를 나눔합니다. 아직 사용하지 않았습니다."),
                    new SeedPost("작은 거울 필요하신 분", "책상에 놓는 작은 거울입니다. 방 정리 중이라 필요한 분께 드리고 싶어요."),
                    new SeedPost("미사용 노트 두 권 나눔", "줄 있는 노트 두 권을 나눔합니다. 수업 필기용으로 사용하시면 좋겠습니다."),
                    new SeedPost("멀티탭 정리함 나눔해요", "케이블을 넣어 정리하는 플라스틱 정리함입니다. 크기를 확인하고 가져가세요."),
                    new SeedPost("교양 수업 참고 도서 나눔", "지난 학기에 읽었던 교양 참고 도서입니다. 필요한 분께 무료로 드립니다."),
                    new SeedPost("미개봉 물티슈 나눔", "방을 정리하다가 여분 물티슈를 찾았습니다. 미개봉 한 팩 나눔합니다."),
                    new SeedPost("문구 보관함 나눔합니다", "펜과 지우개를 넣는 작은 보관함입니다. 오늘 저녁 로비에서 전달 가능합니다."),
                    new SeedPost("깨끗한 에코백 드려요", "행사에서 받은 에코백을 나눔합니다. 사용하지 않아 상태가 좋습니다."),
                    new SeedPost("책 받침대 나눔", "접이식 독서대를 나눔합니다. 교재를 올려 놓고 공부할 때 편합니다."),
                    new SeedPost("여분 마스크 나눔해요", "개별 포장된 마스크 다섯 장을 나눔합니다. 필요하신 분 댓글 부탁드립니다."),
                    new SeedPost("작은 빨래 바구니 나눔", "사용하던 빨래 바구니를 깨끗이 세척했습니다. 직접 가져가실 수 있는 분께 드립니다."),
                    new SeedPost("케이블 정리 클립 나눔", "케이블 고정용 클립이 몇 개 남았습니다. 책상 정리하실 분 가져가세요."),
                    new SeedPost("기숙사 이사 상자 나눔", "접어서 보관한 튼튼한 종이 상자 두 개가 있습니다. 짐 정리할 때 필요한 분께 드립니다.")
            )),
            new SeedBoard("택배게시판", List.of(
                    new SeedPost("택배 수령 장소가 궁금합니다", "처음 택배를 주문했는데 어디에서 찾아가면 되나요? 생활관 수령 장소를 알려주세요."),
                    new SeedPost("택배 도착 알림 확인해 주세요", "로비 택배 보관대에 물품이 많이 쌓여 있습니다. 도착 문자를 받으신 분은 확인 부탁드려요."),
                    new SeedPost("택배 상자를 잘못 가져왔습니다", "제 물건인 줄 알고 상자를 가져왔다가 이름이 다른 것을 확인했습니다. 개봉하지 않고 보관대에 돌려놓았습니다."),
                    new SeedPost("택배 찾을 때 이름 확인 부탁드려요", "비슷한 크기의 상자가 많아 헷갈리기 쉽습니다. 받는 사람 이름을 꼭 확인하고 가져가 주세요."),
                    new SeedPost("주말에도 택배 수령 가능한가요?", "주말에 도착 예정인 물건이 있습니다. 주말 수령 시간이나 방법을 아시는 분 계신가요?"),
                    new SeedPost("반품 택배 접수 방법 질문", "반품할 상자를 어디에 놓아야 하는지 궁금합니다. 기사님께 직접 전달해야 할까요?"),
                    new SeedPost("택배 주소 작성 방법 공유", "생활관 이름과 호실까지 정확히 적으면 배송에 도움이 된다고 합니다. 주문 전 주소를 한 번 확인해 주세요."),
                    new SeedPost("냉장 식품 택배 수령 질문", "냉장 식품이 도착할 예정인데 수업이 늦게 끝납니다. 별도 보관 공간이 있는지 궁금합니다."),
                    new SeedPost("큰 택배 상자 수령 방법", "부피가 큰 물건을 주문했습니다. 일반 택배 보관대에서 찾을 수 있는지 알려주세요."),
                    new SeedPost("택배 도착 문자만 오고 물건이 없어요", "배송 완료 문자를 받았는데 보관대에서 찾지 못했습니다. 다른 수령 장소가 있는지 궁금합니다."),
                    new SeedPost("택배 포장재 분리수거 안내 부탁", "상자를 정리하면서 나온 완충재와 비닐을 어디에 버리면 되는지 알려주세요."),
                    new SeedPost("택배 보관대 정리 함께 해요", "물건을 찾은 뒤 빈 상자와 포장재는 가져가 주세요. 다음 사람이 쉽게 찾을 수 있도록 부탁드립니다."),
                    new SeedPost("배송 기사님 연락을 놓쳤어요", "수업 중이라 배송 연락을 받지 못했습니다. 이런 경우 보통 어디에서 물건을 확인하시나요?"),
                    new SeedPost("우편물 수령 위치 질문", "작은 등기 우편도 택배와 같은 장소에서 받는지 궁금합니다. 이용해 보신 분 알려주세요."),
                    new SeedPost("택배 수령 시 준비할 것", "본인 확인이 필요한 택배가 있다고 들었습니다. 학생증을 가져가면 될까요?"),
                    new SeedPost("배송 지연 경험 있으신가요?", "학교 주소로 주문한 물건이 며칠째 배송 중입니다. 최근에 비슷한 경험이 있는지 궁금합니다."),
                    new SeedPost("다른 생활관으로 배송된 경우", "택배가 다른 생활관으로 간 것 같습니다. 어느 곳에 문의하면 좋을지 알려주세요."),
                    new SeedPost("택배 상자 옮기는 것 도와주실 분", "무거운 책이 든 상자를 로비에서 방으로 옮기려고 합니다. 잠깐 도와주실 분을 찾습니다."),
                    new SeedPost("장기간 택배 보관 가능한가요?", "잠시 집에 다녀오는 동안 택배가 도착할 예정입니다. 보관 기간에 제한이 있는지 궁금합니다."),
                    new SeedPost("택배 수령 후 주변 정리 부탁드립니다", "보관대 주변에 테이프와 포장 비닐이 남아 있습니다. 개봉 후에는 정리까지 부탁드립니다.")
            )),
            new SeedBoard("민원게시판", List.of(
                    new SeedPost("복도 조명 점검 요청", "생활관 복도 조명 하나가 깜빡입니다. 저녁에 이동할 때 불편해서 점검을 요청드립니다."),
                    new SeedPost("공용 세탁기 작동 확인 부탁드립니다", "공용 세탁기 한 대가 작동 중 멈췄습니다. 사용 안내나 점검 여부를 확인해 주세요."),
                    new SeedPost("휴게실 청소 요청", "휴게실 테이블에 음식물 흔적이 남아 있습니다. 공용 공간 청소와 이용 안내를 부탁드립니다."),
                    new SeedPost("야간 소음 줄여 주세요", "늦은 밤 복도에서 큰 대화 소리가 들립니다. 취침 시간에는 소리를 조금 줄여 주세요."),
                    new SeedPost("화장실 수도꼭지 점검 요청", "공용 화장실 수도꼭지에서 물이 계속 떨어집니다. 누수 여부를 확인 부탁드립니다."),
                    new SeedPost("분리수거 공간 정리 요청", "재활용품이 넘쳐 이동하기 어렵습니다. 수거 일정 확인과 주변 정리를 요청드립니다."),
                    new SeedPost("생활관 출입문 닫힘 상태 확인", "출입문이 끝까지 닫히지 않는 경우가 있습니다. 잠금 장치와 문 상태 점검을 부탁드립니다."),
                    new SeedPost("공용 전자레인지 청소 부탁", "전자레인지 안에 음식물이 남아 있습니다. 사용 후 닦아 주시고 정기 점검도 부탁드립니다."),
                    new SeedPost("복도 창문 손잡이 수리 요청", "복도 창문 손잡이가 느슨해서 열고 닫기 어렵습니다. 시설 점검을 요청드립니다."),
                    new SeedPost("샤워실 온수 확인 부탁드립니다", "저녁 시간에 온수가 갑자기 차가워졌습니다. 공급 상태를 확인해 주시면 감사하겠습니다."),
                    new SeedPost("휴게실 의자 수리 요청", "휴게실 의자 한 개의 다리가 흔들립니다. 다치지 않도록 수리하거나 교체해 주세요."),
                    new SeedPost("건조기 사용 안내문 보완 요청", "건조기 사용 방법과 시간을 확인하기 어렵습니다. 안내문을 잘 보이는 곳에 붙여 주세요."),
                    new SeedPost("공용 공간 환기 요청", "공용 공간에 냄새가 오래 남는 경우가 있습니다. 환기 시간이나 관리 방법 안내를 부탁드립니다."),
                    new SeedPost("정수기 상태 점검 요청", "정수기 물이 평소보다 천천히 나옵니다. 필터와 기기 상태 확인을 요청드립니다."),
                    new SeedPost("계단 미끄럼 방지 조치 요청", "비 오는 날 계단 입구가 미끄럽습니다. 매트나 안내 표지 설치를 검토해 주세요."),
                    new SeedPost("방충망 보수 요청", "공용 복도 방충망에 작은 구멍이 있습니다. 벌레가 들어올 수 있어 보수를 요청드립니다."),
                    new SeedPost("자전거 보관 공간 정리 요청", "자전거가 통로를 막는 경우가 있습니다. 지정 구역 안내와 정리를 부탁드립니다."),
                    new SeedPost("공용 와이파이 연결 확인 요청", "휴게실에서 와이파이 연결이 자주 끊깁니다. 공유기 상태나 이용 방법을 확인해 주세요."),
                    new SeedPost("택배 보관 공간 개선 요청", "택배가 많을 때 통로가 좁아집니다. 보관 구역 표시나 선반 추가를 검토해 주세요."),
                    new SeedPost("시설 민원 처리 현황 안내 요청", "접수한 시설 민원의 처리 상황을 확인하고 싶습니다. 확인 방법을 안내해 주시면 감사하겠습니다.")
            ))
    );

    /** 기존 seed 회원 1~20번이 각 게시판에 한 글씩 작성한다. */
    @Transactional
    public int createPosts(User author, Dormitory dormitory, int index) {
        int createdCount = 0;
        for (SeedBoard board : BOARDS) {
            if (index < 1 || index > board.posts().size()) {
                continue;
            }
            SeedPost sample = board.posts().get(index - 1);
            String title = "[SEED] %02d %s".formatted(index, sample.title());
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM community_post
                    WHERE author_id = ? AND category = ? AND title = ?
                    """, Integer.class, author.getUserId(), board.category(), title);
            if (count != null && count > 0) {
                continue;
            }
            communityPostRepository.save(new CommunityPost(
                    author, dormitory, board.category(), title,
                    sample.content() + "\n\n※ 커뮤니티 기능 확인용 테스트 게시글입니다."
            ));
            createdCount++;
        }
        return createdCount;
    }

    /** seed 회원 삭제 전에 게시글과 연결된 외래 키를 정리한다. */
    @Transactional
    public void deletePosts(List<Long> seedUserIds) {
        if (seedUserIds.isEmpty()) {
            return;
        }
        List<Long> postIds = jdbcTemplate.queryForList(
                "SELECT post_id FROM community_post WHERE author_id IN (%s)"
                        .formatted(placeholders(seedUserIds)), Long.class, seedUserIds.toArray());
        if (postIds.isEmpty()) {
            return;
        }
        List<Long> commentIds = jdbcTemplate.queryForList(
                "SELECT comment_id FROM community_comment WHERE post_id IN (%s)"
                        .formatted(placeholders(postIds)), Long.class, postIds.toArray());

        updateForIds("DELETE FROM report WHERE comment_id IN (%s)", commentIds);
        updateForIds("DELETE FROM report WHERE post_id IN (%s)", postIds);
        updateForIds("UPDATE chat_room SET source_comment_id = NULL WHERE source_comment_id IN (%s)", commentIds);
        updateForIds("UPDATE chat_room SET source_post_id = NULL WHERE source_post_id IN (%s)", postIds);
        updateForIds("DELETE FROM comment_like WHERE comment_id IN (%s)", commentIds);
        // 답글을 먼저 삭제해 parent_comment_id 자기 참조를 정리한다.
        updateForIds("DELETE FROM community_comment WHERE post_id IN (%s) AND parent_comment_id IS NOT NULL", postIds);
        updateForIds("DELETE FROM community_comment WHERE post_id IN (%s)", postIds);
        updateForIds("DELETE FROM post_like WHERE post_id IN (%s)", postIds);
        updateForIds("DELETE FROM post_image WHERE post_id IN (%s)", postIds);
        updateForIds("DELETE FROM community_post WHERE post_id IN (%s)", postIds);
        log.info("Seed 커뮤니티 게시글 {}개 삭제 완료", postIds.size());
    }

    private void updateForIds(String sql, List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbcTemplate.update(sql.formatted(placeholders(ids)), ids.toArray());
        }
    }

    private String placeholders(List<Long> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }
}
