package exhibition;

import java.time.LocalDate;
import java.util.*;
import com.kkirikkiri.survey.dto.*;

/** Entirely fictional content. Stable slot keys, never display names, identify maintenance targets. */
public final class ExhibitionCatalog {
    private ExhibitionCatalog() {}
    public static final String VERSION = "exhibition-v2-campus";
    public static final int LEGACY_STUDENT_COUNT = 30;
    public static final int STUDENT_COUNT = 50;
    public static final String MODERATOR_KEY = "admin/moderator";
    public record Student(int slot, String nickname, String gender, String dormitory, String faculty,
            String major, short birthYear, String admissionYear, String grade, String mbti,
            ProfileSurveyRequest survey, RoommatePreferenceRequest preference) {
        public boolean moderator() { return slot == 0; }
        public String email() { return moderator() ? "exhibition_admin@hknu.ac.kr" : "seed_user%02d@hknu.ac.kr".formatted(slot); }
        public String key() { return moderator() ? MODERATOR_KEY : "user/" + slot; }
    }
    public record Post(int slot, String category, String title, String content, String comment, String reply,
            boolean anonymous) {
        public String key() { return "post/" + category + "/" + slot; }
    }
    public record Conversation(String key, int caller, int target, String sourcePostKey, boolean fromComment,
            List<String> messages) {}
    public record Event(String code, int slot, String title, int dayOffset, String start, String end, String description) {
        public String key() { return "event/" + slot + "/" + code; }
    }
    private static final String[] NAMES = {"걍집가고싶다", "말차라떼", "잠좀자자", "냥냥펀치", "배고픈감자", "자전거도둑아님",
            "아침못먹음", "감자8호", "필름값비싸", "무음모드", "귤까먹는중", "아아연하게", "산책갈사람", "탭만서른개",
            "낯가림심함", "컨트롤제트", "소금빵두개", "향수안씀", "실습끝", "마감살려줘", "에어팟어딨지", "도서관출석",
            "금요일엔집", "양말한짝", "오늘도밤샘", "과제는내일", "민초조아", "침대최고", "알람하나만", "밥먹고할게",
            "공강어디감", "딸기우유", "충전기빌려줘", "후드집업", "학식두그릇", "책갈피분실", "종강만기다림", "새벽편의점",
            "우산또두고옴", "떡볶이순한맛", "계단은힘들어", "노트북무거워", "바닐라빈", "오늘은일찍잠", "점심뭐먹지",
            "버스놓쳤다", "조별과제중", "고양이목격", "물마시는중", "산책한바퀴"};
    private static final String[] INTRO = {
            "아침 수업이 많아 자정 전에는 쉬는 편이에요. 시험 기간에는 서로 조용히 응원해요.",
            "차와 책을 좋아해요. 공용 공간은 주말에 함께 정리하면 좋겠습니다.",
            "천천히 친해지는 편입니다. 불편한 점은 쌓아두지 않고 이야기하고 싶어요.",
            "기숙사 첫 학기예요. 서로 일정부터 편하게 맞춰 가면 좋겠어요.",
            "이어폰과 책을 늘 챙겨 다닙니다. 잠들기 전 조용한 시간을 좋아해요.",
            "주말에는 자전거를 타러 나가요. 청소 당번은 번갈아 맡으면 좋겠어요.",
            "아침 식사는 꼭 챙겨요. 같은 시간 수업이면 가끔 같이 걸어가요.",
            "책상에 작은 화분 하나를 두고 있어요. 환기와 정리를 중요하게 생각합니다.",
            "사진 동아리 활동을 해요. 늦게 들어오는 날은 미리 알려드릴게요.",
            "방에서는 차분하게 쉬는 편이에요. 통화는 복도보다 밖에서 합니다.",
            "간식은 나눠 먹어도 개인 물건은 먼저 물어봐 주면 좋아요.",
            "수업 없는 오후에는 과제를 합니다. 서로 쉬는 시간을 존중하고 싶어요.",
            "저녁 산책으로 하루를 마무리해요. 서로 생활 리듬이 맞으면 좋겠습니다.",
            "할 일을 메모하는 습관이 있어요. 공동 구매 비용도 깔끔하게 정산해요.",
            "처음에는 조금 낯을 가리지만 같이 밥 먹으며 친해지고 싶어요.",
            "그림 그리는 걸 좋아합니다. 작업할 때도 소음은 조심할게요.",
            "동네 빵집 찾는 게 취미예요. 쉬는 날 맛있는 곳을 함께 찾아요.",
            "향이 강한 제품은 잘 쓰지 않아요. 방은 편안한 휴식 공간이면 좋겠어요.",
            "늦은 실습이 있는 날이 있어요. 돌아와서는 조용히 씻고 쉴게요.",
            "작업 일정이 유동적입니다. 마감 주간에는 미리 생활 시간을 공유해요.",
            "걷기와 음악 듣기를 좋아해요. 음악은 늘 이어폰으로 듣습니다.",
            "도서관에서 공부하고 방에서는 쉬어요. 서로의 취향을 존중하고 싶어요.",
            "주중에는 기숙사, 주말에는 집에 자주 가요. 일정은 미리 알려드려요.",
            "빨래는 몰아서 하기보다 틈틈이 해요. 건조대 자리도 나눠 써요.",
            "밤에 집중이 잘되는 편이에요. 책상 조명은 가림막을 사용합니다.",
            "과제 때문에 늦게 자기도 해요. 룸메이트가 자면 최대한 조용히 지낼게요.",
            "게임은 가끔 하지만 음성 채팅은 방에서 하지 않아요.",
            "시험 기간에는 도서관에 오래 있어요. 생활 규칙을 먼저 함께 정하고 싶어요.",
            "평일과 주말 일정이 조금 달라요. 알람은 짧게 끄는 편입니다.",
            "각자 시간을 보내다가 가끔 야식 먹는 사이면 좋아요. 쓰레기는 바로 정리해요.",
            "공강이면 도서관 아니면 카페에 있어요. 방에서는 조용히 쉬고 싶고 청소는 날짜 정해서 같이 해요!",
            "아침 수업 있는 날은 일찍 자요. 냉장고 간식은 이름 붙여두는 편이고 같이 먹을 땐 따로 말할게요.",
            "노트북으로 과제할 일이 많아요. 밤에는 이어폰 쓰고 조명도 낮출게요. 불편한 건 편하게 얘기해 주세요.",
            "옷이 좀 많아서 제 공간 안에 잘 정리하려고 해요ㅋㅋ 빨래는 주중에 하고 건조대는 바로 치워요.",
            "학식 같이 먹을 사람 있으면 좋아요. 매번 같이 다니기보단 시간 맞을 때 편하게 보는 사이면 좋겠어요.",
            "책 읽는 거 좋아하고 잠귀가 밝아요. 소등 시간만 서로 맞으면 다른 건 대화하면서 정하고 싶어요.",
            "동아리 끝나고 늦게 오는 날이 있어요. 들어오기 전에 톡 남기고 씻을 때도 최대한 조심할게요.",
            "밤에 과제가 잘되는 편인데 밤샘은 잘 안 해요. 통화는 밖에서 하고 방에서는 이어폰 꼭 써요.",
            "비 오는 날엔 방에서 영화 봐요. 이어폰 쓰니까 소리 걱정은 없고 환기하는 시간만 같이 맞춰요.",
            "요리 영상 보는 거 좋아해요. 방에서는 냄새 안 나는 간식만 먹고 쓰레기는 그날 버리는 편이에요.",
            "운동하고 돌아오면 바로 씻어요. 아침에 바쁜 날은 전날에 말할게요. 공용 세제는 같이 사도 좋아요.",
            "실습 과제가 많아서 짐이 좀 있어요. 책상 밖으로 안 넘치게 정리하고 주말엔 집에 자주 가요.",
            "커피는 좋아하지만 방에 향 강한 건 잘 안 둬요. 서로 물건 빌릴 때 한마디씩 해주면 좋겠어요.",
            "이번 학기는 생활 패턴 좀 잡으려고요. 자정 전후로 자고 아침 알람은 바로 끄려고 노력해요.",
            "처음엔 조용한데 친해지면 말 많아져요ㅋㅋ 각자 쉬는 시간은 지키면서 가끔 밥 같이 먹어요.",
            "통학하다가 기숙사 처음 들어와요. 생활 규칙 먼저 같이 정하고 안 맞는 건 그때그때 얘기하고 싶어요.",
            "팀플 회의는 스터디룸에서 해요. 방에서는 쉬거나 혼자 과제하고 청소는 주말에 몰아서 합니다.",
            "산책하다 고양이 만나면 사진 찍는 게 취미예요. 주중엔 일찍 자고 주말엔 조금 늦잠 자요.",
            "더위를 좀 타서 환기 자주 해요. 창문 여는 건 먼저 물어볼게요. 작은 소음은 크게 신경 안 써요.",
            "저녁 먹고 한 바퀴 걷는 편이에요. 룸메랑 매일 붙어 있진 않아도 편하게 인사하는 사이면 좋겠어요."};
    private static final String[][] ACADEMICS = {
            {"컴퓨터응용수학부", "소프트웨어융합전공"}, {"법경영학부", "경영학전공"},
            {"건설환경공학부", "환경공학전공"}, {"디자인예술스포츠학부", "시각미디어디자인전공"},
            {"웰니스산업융합학부", "식품영양학전공"}, {"컴퓨터응용수학부", "소프트웨어&서비스컴퓨팅전공"}};
    private static final String[] MBTI = {"ISFJ", "INTJ", "INFP", "ENTP", "ESFJ", "ISTP", "ENFP", "ISTJ", "INFJ", "ESTJ"};
    private static final String[] DORMS = {"나래", "호연", "비봉", "창조"};
    private static final String[] WEIGHTS = {"department", "age", "grade", "bedtime", "wakeTime", "sleepTalking",
            "snoring", "teethGrinding", "smoking", "lightingPreference", "showerTime", "showerDuration",
            "cleaningFrequency", "cleaningStyle", "interaction", "meal", "sharedItems", "temperature"};

    public static List<Student> students() {
        List<Student> result = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            // Original answers stay stable. Demographics match the verified latest thirty-user DB.
            int rhythm = i < LEGACY_STUDENT_COUNT ? i / 6 : (i - LEGACY_STUDENT_COUNT) % 5;
            String[] academic = ACADEMICS[i % ACADEMICS.length];
            result.add(new Student(i + 1, NAMES[i], i < 30 || i % 2 == 0 ? "남자" : "여자", DORMS[(i / 3) % 4],
                    academic[0], academic[1], (short)(i < 30 ? 2000 + i % 5 : 2002 + (i - 30) % 5), "%02d".formatted(i < 30 ? 22 + i % 4 : 26 - i % 4),
                    (1 + i % 4) + "학년", MBTI[i % MBTI.length], survey(i, rhythm), preference(i, rhythm)));
        }
        return List.copyOf(result);
    }
    public static Student moderator() {
        return new Student(0, "전시안내", "여자", "나래", ACADEMICS[0][0], ACADEMICS[0][1],
                (short)2003, "23", "4학년", "ISFJ", survey(0, 0), preference(0, 0));
    }

    private static ProfileSurveyRequest survey(int i, int rhythm) {
        return new ProfileSurveyRequest(
                List.of("22시 ~ 23시", "23시 ~ 24시", "24시 ~ 01시", "01시 ~ 02시", "02시 ~ 03시").get(rhythm),
                List.of("07시 이전", "07시 ~ 08시", "08시 ~ 09시", "09시 ~ 10시", "10시 이후").get(rhythm),
                i % 7 == 0 ? "적음" : "없음", i % 8 == 0 ? "적음" : "없음", i % 9 == 0 ? "적음" : "없음",
                rhythm < 2 ? "밝음 (소리에 잘 깨요)" : "보통", i % 3 == 0 ? "가능" : "냄새 안나는 음식 가능",
                rhythm < 3 ? "비흡연" : i % 2 == 0 ? "전자담배" : "일반담배",
                rhythm < 2 ? "어두움 선호" : "밝음 선호", i % 2 == 0 ? "항상 밖에서" : "방에서는 짧게만",
                "항상 이어폰 사용해주세요", rhythm < 2 ? "아침" : "저녁", rhythm < 3 ? "20분" : "30분",
                rhythm < 2 ? "주 3회" : rhythm == 2 ? "주 2회" : "주 1회",
                rhythm < 3 ? "같이 청소" : "각자 영역만 청소",
                i % 3 == 0 ? List.of("월", "화", "수", "목", "금", "토", "일") : List.of("월", "화", "수", "목", "금"),
                i % 4 == 0 ? List.of("신청 안 함") : i % 2 == 0 ? List.of("조식", "석식") : List.of("석식"),
                rhythm < 3 ? "시간 될 때만 같이 먹고 싶음" : "각자 먹고 싶음",
                rhythm < 3 ? "적당히 대화" : "필요한 대화만", i % 2 == 0 ? "보통" : "추위 타요",
                i % 3 == 0 ? "가끔 사용" : "사용 안 함", i % 5 == 0 ? "사용해요" : "사용하지 않아요",
                rhythm < 3 ? "일부만 공유" : "각자 사용", INTRO[i]);
    }

    private static RoommatePreferenceRequest preference(int i, int rhythm) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (int w = 0; w < WEIGHTS.length; w++) weights.put(WEIGHTS[w], 1 + (i + w) % 5);
        weights.put("bedtime", 5); weights.put("wakeTime", 4); weights.put("smoking", 5);
        weights.put("cleaningFrequency", rhythm < 2 ? 5 : 2);
        return new RoommatePreferenceRequest("상관없음", List.of("상관없음"), List.of("상관없음"),
                List.of(List.of("22시 ~ 23시", "23시 ~ 24시", "24시 ~ 01시", "01시 ~ 02시", "02시 ~ 03시").get(rhythm)),
                List.of(List.of("07시 이전", "07시 ~ 08시", "08시 ~ 09시", "09시 ~ 10시", "10시 이후").get(rhythm)),
                "적음까지 괜찮음", "적음까지 괜찮음", "적음까지 괜찮음", rhythm < 3 ? "비흡연자만" : "상관없음",
                rhythm < 2 ? "어두움 선호" : "밝음 선호", rhythm < 2 ? "아침" : "저녁", rhythm < 3 ? "20분 이하" : "30분 이하",
                List.of(rhythm < 2 ? "주 3회" : rhythm == 2 ? "주 2회" : "주 1회"),
                rhythm < 3 ? "같이 청소하는 사람" : "각자 영역만 청소하는 사람",
                rhythm < 3 ? "적당히 대화하고 싶은 사람" : "필요한 대화만 하고 싶은 사람",
                rhythm < 3 ? "시간 될 때만 같이 먹고 싶음" : "각자 먹고 싶음",
                rhythm < 3 ? "일부만 공유하는 사람" : "각자 사용하는 사람", "상관없음", weights);
    }

    public static List<Post> posts() {
        List<Post> result = new ArrayList<>();
        addPosts(result, "자유게시판", FREE); addPosts(result, "나눔게시판", SHARE);
        addPosts(result, "택배게시판", PARCEL); addPosts(result, "민원게시판", FACILITY);
        return List.copyOf(result);
    }
    private static void addPosts(List<Post> result, String category, String data) {
        String[] lines = data.strip().split("\\R");
        if (lines.length != 20) throw new IllegalStateException("Expected twenty fictional posts per board");
        for (int i = 0; i < lines.length; i++) {
            String[] fields = lines[i].strip().split("\\|", -1);
            if (fields.length != 4) throw new IllegalStateException("Invalid fictional post");
            result.add(new Post(i + 1, category, fields[0], fields[1], fields[2], fields[3], i % 5 == 4));
        }
    }
    public static List<Conversation> conversations() {
        return List.of(
            new Conversation("chat/morning", 1, 2, null, false, List.of("안녕하세요 프로필 보고 연락드려요! 보통 몇 시에 주무세요?", "11시쯤요 월수금 1교시라ㅠ", "저도 일찍 자는 편이에요. 청소는 주말에 같이 하면 어떠세요?", "좋아요 토요일 오전 괜찮으세요?", "네 그때 해요! 알람은 최대한 빨리 끌게요ㅋㅋ")),
            new Conversation("chat/quiet", 3, 4, null, false, List.of("안녕하세요 룸메 아직 구하시나요? 잠귀가 좀 밝아서요 방에서 통화 자주 하세요?", "아뇨 긴 통화는 나가서 해요", "다행이다ㅠ 저도 잘 때는 조용한 게 좋아서요", "저도요! 대신 불편하면 바로 말해주세요 제가 모를 수도 있어서")),
            new Conversation("chat/laundry", 7, 8, null, false, List.of("건조대 챙겨오세요? 둘 다 가져오면 자리 없을 것 같아서요", "작은 거 하나 있어요 같이 쓰실래요?", "오 좋아요 저는 보통 화요일에 빨래해요", "전 주말에 몰아서 해요ㅋㅋ 그럼 제 거 가져갈게요")),
            new Conversation("chat/ventilation", 13, 14, null, false, List.of("안녕하세요 혹시 추위 많이 타세요? 방 온도 때문에 안 맞을까 봐요", "좀 타요ㅠ 잘 때 창문은 닫고 싶어요", "네 잘 때 닫는 건 저도 좋아요 환기만 잠깐씩 해도 될까요?", "그건 괜찮아요! 저도 환기는 해야 돼서")),
            new Conversation("chat/studio", 19, 20, null, false, List.of("목요일 실습 늦게 끝나세요? 저는 과제하다 늦게 들어갈 때 있어서요", "네 11시쯤 들어와요 씻는 소리 괜찮으실까요?", "그 시간이면 저도 안 자요ㅋㅋ 드라이기만 오래 안 쓰시면 돼요", "짧게 써요! 먼저 자는 날은 톡 주세요")),
            new Conversation("chat/night", 25, 26, null, false, List.of("아직 안 자요? 스탠드 좀 켜도 돼요?", "ㅇㅇ 나도 과제 중ㅋㅋ", "나 오늘도 밤샘각... 끝나면 불 끌게", "난 먼저 잘 수도 있어 이어폰만 부탁ㅠ")),
            new Conversation("chat/share", 2, 1, "post/나눔게시판/1", false, List.of("스탠드 아직 있나요? 나눔 글 보고 왔어요", "네 있어요 전구도 같이 드릴게요", "감사해요! 7시 반에 1층 괜찮으세요?", "넵 내려가면 톡할게요")),
            new Conversation("chat/anonymous-comment", 4, 3, "post/자유게시판/1", true, List.of("산책 글 댓글 보고 왔어요 도서관 뒤쪽으로 가면 되나요?", "네 건물 뒤로 돌아서 길 따라가면 돼요", "찾았어요ㅋㅋ 생각보다 가깝네요", "한 바퀴 돌고 오면 과제 할 만해요 잠깐이지만ㅋㅋ")));
    }
    public static List<Event> events() {
        List<Event> events = new ArrayList<>();
        for (int slot : List.of(1, 7, 19)) {
            events.add(new Event("room-rules", slot, "룸메이트 생활 규칙 정하기", 1, "19:30", "20:00", "소등 시간, 청소 순서와 공용물품을 함께 정해요."));
            events.add(new Event("shared-cleanup", slot, "공용 공간 정리", 3, "17:00", "17:30", "책상 주변 정리와 분리수거. 공동 구매 목록도 확인해요."));
            events.add(new Event("study-group", slot, "도서관 과제 모임", 5, "18:00", "20:00", "기숙사 친구들과 각자 과제를 하는 모임입니다."));
            events.add(new Event("laundry", slot, "세탁과 침구 정리", 7, "10:00", "11:00", "세탁망과 세제를 챙기고 건조대 공간을 비워요."));
        }
        return events;
    }
    public static void validateAnchor(LocalDate anchor) {
        if (anchor.isBefore(LocalDate.of(2000, 2, 1)) || anchor.isAfter(LocalDate.of(2099, 12, 1)))
            throw new IllegalArgumentException("Anchor must be between 2000-02-01 and 2099-12-01");
    }

    private static final String FREE = ExhibitionCampusCopy.FREE;
    private static final String SHARE = ExhibitionCampusCopy.SHARE;
    private static final String PARCEL = ExhibitionCampusCopy.PARCEL;
    private static final String FACILITY = ExhibitionCampusCopy.FACILITY;
}
