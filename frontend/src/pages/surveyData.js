export const GENDER_OPTIONS = ["남자", "여자"];

export const DORMITORY_OPTIONS = ["나래", "호연", "비룡", "창조"];

export const FACULTY_OPTIONS = [
  {
    name: "인문융합공공인재학부",
    majors: [
      "문예창작미디어콘텐츠홍보전공",
      "영미언어문화전공",
      "행정학전공",
      "노동복지전공",
    ],
  },
  {
    name: "법경영학부",
    majors: ["법학전공", "경영학전공"],
  },
  {
    name: "웰니스산업융합학부",
    majors: ["의류산업학전공", "복지상담학전공", "식품영양학전공"],
  },
  {
    name: "식물자원조경학부",
    majors: ["식물생명환경전공", "조경학전공"],
  },
  {
    name: "동물생명융합학부",
    majors: ["동물생명과학전공", "동물응용과학전공"],
  },
  {
    name: "생명공학부",
    majors: ["원예생명공학전공", "응용생명공학전공"],
  },
  {
    name: "건설환경공학부",
    majors: ["토목공학전공", "환경공학전공"],
  },
  {
    name: "사회안전시스템공학부",
    majors: ["안전공학전공", "지역자원시스템공학전공"],
  },
  {
    name: "식품생명화학공학부",
    majors: ["식품생명공학전공", "화학공학전공"],
  },
  {
    name: "컴퓨터응용수학부",
    majors: [
      "소프트웨어&서비스컴퓨팅전공",
      "소프트웨어융합전공",
      "정보보안전공",
      "응용수학전공",
    ],
  },
  {
    name: "ICT로봇기계공학부",
    majors: ["ICT로봇공학전공", "기계공학전공"],
  },
  {
    name: "전자전기공학부",
    majors: ["전자공학전공", "전기공학전공"],
  },
  {
    name: "건축융합학부",
    majors: ["건축학전공(5년제)", "건축공학전공"],
  },
  {
    name: "디자인예술스포츠학부",
    majors: [
      "스포츠과학전공",
      "시각미디어디자인전공",
      "제품공간디자인전공",
      "실용음악전공",
      "귀금속보석공예전공",
    ],
  },
  {
    name: "복지융합학부",
    majors: [
      "사회복지학전공",
      "한국수어교육전공",
      "유아특수보육학전공",
      "의료재활공학전공",
    ],
  },
  {
    name: "사회통합학부",
    majors: ["공공행정전공", "상담심리교육전공", "특수체육학전공"],
  },
  {
    name: "AI반도체융합학부",
    majors: ["AI반도체융합전공"],
  },
  {
    name: "HK자율전공학부",
    majors: [],
  },
  {
    name: "글로벌창의인재학부",
    majors: [
      "글로벌스포츠매니지먼트전공",
      "글로벌경영전공",
      "글로벌인공지능IT전공",
      "식물스마트팜전공",
      "식물생의학전공",
      "바이오에너지전공",
      "아시아문화경영전공",
      "Bio-Mobility전공",
      "English Language, Literature and Language Technology전공",
    ],
  },
  {
    name: "미래융합학부",
    majors: [
      "노동복지전공",
      "행정관리전공",
      "레저스포츠매니지먼트전공",
      "융합레포츠전공",
    ],
  },
  {
    name: "AI융합학부",
    majors: [
      "AI스마트팜융합전공(융합전공)",
      "스마트헬스케어전공(융합전공)",
      "AI빅데이터융합전공(융합전공)",
      "공공헬스케어IT융합전공(융합전공)",
      "반도체융합전공(융합전공)",
      "스마트애그리푸드시스템전공(융합전공)",
      "탄소중립바이오시스템융합전공(융합전공)",
    ],
  },
];

export const MBTI_DIMENSIONS = [
  {
    id: "mbtiEnergy",
    label: "에너지 방향",
    options: ["E", "I"],
  },
  {
    id: "mbtiInformation",
    label: "인식 방식",
    options: ["N", "S"],
  },
  {
    id: "mbtiDecision",
    label: "판단 방식",
    options: ["F", "T"],
  },
  {
    id: "mbtiLifestyle",
    label: "생활 양식",
    options: ["P", "J"],
  },
];

export const SURVEY_OPTIONS = {
  grade: ["1학년", "2학년", "3학년", "4학년", "5학년"],
  sleepTime: [
    "22시 이전",
    "23시",
    "자정(24시)",
    "1시",
    "2시",
    "3시 이후",
  ],
  wakeTime: [
    "6시 이전",
    "7시",
    "8시",
    "9시",
    "10시 이후",
    "수업 시간에 따라 유동적",
  ],
  sleepHabitSeverity: ["없음", "적음", "보통", "심함"],
  sleepSensitivity: [
    "밝음 (소리에 잘 깨요)",
    "보통",
    "어두움 (잘 깨지 않아요)",
  ],
  lightingPreference: ["밝음 선호", "어두움 선호", "상관 없음"],
  phoneCall: ["항상 밖에서", "방에서는 짧게만", "상관없음"],
  dormStayDays: ["월", "화", "수", "목", "금", "토", "일"],
  hairDryerUse: ["사용해요", "사용하지 않아요"],
  cleaningFrequency: [
    "안함",
    "주 1회",
    "주 2회",
    "주 3회",
    "주 4회",
    "주 5회",
    "매일",
    "유동적",
  ],
  cleaningStyle: ["같이 청소", "각자 영역만 청소", "상황에 따라 협의"],
  indoorEating: ["가능", "불가능", "냄새 안나는 음식 가능"],
  smoking: ["비흡연", "일반담배", "전자담배"],
  mediaSound: [
    "항상 이어폰 사용해주세요",
    "공부·과제할 때 아니면 상관없음",
    "소리만 작게 해주세요",
    "자유롭게 하세요",
  ],
  showerTime: ["아침", "저녁", "유동적"],
  showerDuration: ["10분", "20분", "30분", "40분", "그 이상"],
  temperaturePreference: ["추위 타요", "더위 타요", "보통"],
  roommateInteraction: [
    "친하게 지내고 싶음",
    "적당히 대화",
    "필요한 대화만",
  ],
  mealPlan: ["조식", "중식", "석식", "신청 안 함"],
  bugHandling: ["잘 잡음", "못 잡음", "작은 건 잡음"],
  scentProductUse: [
    "자주 사용",
    "가끔 사용",
    "사용 안 함",
    "상관없음",
  ],
  desktopUse: [
    "사용해요",
    "사용하지 않아요",
    "특정 시간 제외하면 상관없음",
  ],
  sharedItems: ["적극적으로 공유", "일부만 공유", "각자 사용"],
  roommateAcademicPreference: [
    "같은 전공",
    "같은 학부의 다른 전공",
    "다른 학부",
    "상관없음",
  ],
  roommateAgePreference: ["연하", "동갑", "연상", "나이 상관없음"],
  roommateGradePreference: [
    "1학년",
    "2학년",
    "3학년",
    "4학년",
    "5학년",
    "6학년 이상",
    "학년 상관없음",
  ],
  roommateSleepHabitPreference: [
    "없음",
    "상관없음",
    "안 심하면 상관없음",
  ],
  roommateSmokingPreference: [
    "비흡연자만",
    "흡연 여부 상관없음",
    "전자담배만 허용",
  ],
  roommateMealPreference: ["같이", "각자", "시간 될 때만 같이"],
};

export const SURVEY_STEPS = [
  {
    id: "basic",
    title: "기본·학적 정보",
    shortTitle: "기본 정보",
    description: "매칭에 필요한 학교 정보와 나의 기본 정보를 알려주세요.",
    fields: [
      {
        id: "gender",
        label: "성별",
        type: "singleSelect",
        options: GENDER_OPTIONS,
        required: true,
      },
      {
        id: "dormitory",
        label: "생활관",
        type: "singleSelect",
        options: DORMITORY_OPTIONS,
        required: true,
      },
      {
        id: "faculty",
        label: "나의 학부",
        type: "facultySelect",
        options: FACULTY_OPTIONS,
        required: true,
      },
      {
        id: "major",
        label: "나의 전공",
        type: "majorSelect",
        dependsOn: "faculty",
        emptyFacultyMessage: "세부전공 없음",
        required: true,
      },
      {
        id: "birthYear",
        label: "출생연도",
        type: "number",
        inputMode: "numeric",
        placeholder: "예: 2005",
        required: true,
      },
      {
        id: "studentId",
        label: "학번",
        type: "text",
        inputMode: "numeric",
        placeholder: "학번을 입력해주세요",
        required: true,
      },
      {
        id: "grade",
        label: "학년",
        type: "singleSelect",
        options: SURVEY_OPTIONS.grade,
        required: true,
      },
      {
        id: "mbti",
        label: "MBTI",
        type: "mbti",
        dimensions: MBTI_DIMENSIONS,
        required: true,
      },
    ],
  },
  {
    id: "lifePattern",
    title: "생활 패턴",
    shortTitle: "생활 패턴",
    description: "함께 생활할 때 중요한 수면 및 방 사용 습관을 알려주세요.",
    fields: [
      {
        id: "sleepTime",
        label: "취침시간",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sleepTime,
        required: true,
      },
      {
        id: "wakeTime",
        label: "기상시간",
        type: "singleSelect",
        options: SURVEY_OPTIONS.wakeTime,
        required: true,
      },
      {
        id: "sleepTalking",
        label: "잠꼬대",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sleepHabitSeverity,
        required: true,
      },
      {
        id: "snoring",
        label: "코골이",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sleepHabitSeverity,
        required: true,
      },
      {
        id: "teethGrinding",
        label: "이갈이",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sleepHabitSeverity,
        required: true,
      },
      {
        id: "sleepSensitivity",
        label: "잠귀",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sleepSensitivity,
        required: true,
      },
      {
        id: "lightingPreference",
        label: "조명 환경",
        type: "singleSelect",
        options: SURVEY_OPTIONS.lightingPreference,
        required: true,
      },
      {
        id: "phoneCall",
        label: "전화",
        type: "singleSelect",
        options: SURVEY_OPTIONS.phoneCall,
        required: true,
      },
      {
        id: "dormStayDays",
        label: "기숙사 상주 주기",
        type: "multiSelect",
        options: SURVEY_OPTIONS.dormStayDays,
        allShortcut: true,
        required: true,
      },
      {
        id: "hairDryerInRoom",
        label: "방 안에서 드라이기 사용",
        type: "singleSelect",
        options: SURVEY_OPTIONS.hairDryerUse,
        required: true,
      },
    ],
  },
  {
    id: "livingHabits",
    title: "청소·생활 습관",
    shortTitle: "생활 습관",
    description: "공용 공간을 함께 사용하는 방식을 알려주세요.",
    fields: [
      {
        id: "cleaningFrequency",
        label: "청소",
        type: "singleSelect",
        options: SURVEY_OPTIONS.cleaningFrequency,
        required: true,
      },
      {
        id: "cleaningStyle",
        label: "청소 방식",
        type: "singleSelect",
        options: SURVEY_OPTIONS.cleaningStyle,
        required: true,
      },
      {
        id: "indoorEating",
        label: "실내 취식",
        type: "singleSelect",
        options: SURVEY_OPTIONS.indoorEating,
        required: true,
      },
      {
        id: "smoking",
        label: "흡연 여부",
        type: "singleSelect",
        options: SURVEY_OPTIONS.smoking,
        required: true,
      },
      {
        id: "mediaSound",
        label: "소리(음악, OTT 등)",
        type: "singleSelect",
        options: SURVEY_OPTIONS.mediaSound,
        required: true,
      },
      {
        id: "showerTime",
        label: "샤워 시간대",
        type: "singleSelect",
        options: SURVEY_OPTIONS.showerTime,
        required: true,
      },
      {
        id: "showerDuration",
        label: "샤워 소요시간",
        type: "singleSelect",
        options: SURVEY_OPTIONS.showerDuration,
        required: true,
      },
      {
        id: "temperaturePreference",
        label: "온도 선호",
        type: "singleSelect",
        options: SURVEY_OPTIONS.temperaturePreference,
        required: true,
      },
    ],
  },
  {
    id: "dormLife",
    title: "기숙사 생활",
    shortTitle: "기숙사 생활",
    description: "룸메이트와 공유하게 될 생활 방식에 대해 알려주세요.",
    fields: [
      {
        id: "roommateInteraction",
        label: "룸메이트와의 교류 정도",
        type: "singleSelect",
        options: SURVEY_OPTIONS.roommateInteraction,
        required: true,
      },
      {
        id: "mealPlan",
        label: "신청한 기숙사 식사",
        type: "multiSelect",
        options: SURVEY_OPTIONS.mealPlan,
        exclusiveOptions: ["신청 안 함"],
        required: true,
      },
      {
        id: "bugHandling",
        label: "벌레",
        type: "singleSelect",
        options: SURVEY_OPTIONS.bugHandling,
        required: true,
      },
      {
        id: "scentProductUse",
        label: "향수·디퓨저 등 향 제품 사용·허용",
        type: "singleSelect",
        options: SURVEY_OPTIONS.scentProductUse,
        required: true,
      },
      {
        id: "desktopUse",
        label: "데스크탑 사용·허용",
        type: "singleSelect",
        options: SURVEY_OPTIONS.desktopUse,
        required: true,
      },
      {
        id: "sharedItems",
        label: "공용물품 사용",
        type: "singleSelect",
        options: SURVEY_OPTIONS.sharedItems,
        required: true,
      },
    ],
  },
  {
    id: "roommatePreference",
    title: "내가 원하는 룸메이트",
    shortTitle: "룸메이트 선호",
    description: "어떤 룸메이트와 함께 지내고 싶은지 알려주세요.",
    fields: [
      {
        id: "roommateAcademicPreference",
        label: "학과·학부",
        type: "singleSelect",
        options: SURVEY_OPTIONS.roommateAcademicPreference,
        required: true,
      },
      {
        id: "roommateAgePreference",
        label: "나이",
        type: "multiSelect",
        options: SURVEY_OPTIONS.roommateAgePreference,
        exclusiveOptions: ["나이 상관없음"],
        required: true,
      },
      {
        id: "roommateGradePreference",
        label: "학년",
        type: "multiSelect",
        options: SURVEY_OPTIONS.roommateGradePreference,
        exclusiveOptions: ["학년 상관없음"],
        required: true,
      },
      {
        id: "roommateSleepHabitPreference",
        label: "잠버릇",
        type: "singleSelect",
        options: SURVEY_OPTIONS.roommateSleepHabitPreference,
        required: true,
      },
      {
        id: "roommateSmokingPreference",
        label: "흡연 여부",
        type: "singleSelect",
        options: SURVEY_OPTIONS.roommateSmokingPreference,
        required: true,
      },
      {
        id: "roommateMealPreference",
        label: "기숙사 식사",
        type: "singleSelect",
        options: SURVEY_OPTIONS.roommateMealPreference,
        required: true,
      },
      {
        id: "roommateOtherWishes",
        label: "그 외에 원하는 점",
        type: "textarea",
        placeholder: "룸메이트에게 바라는 점을 자유롭게 입력해주세요.",
        maxLength: 300,
        required: false,
      },
    ],
  },
];

const multiSelectFieldIds = new Set(
  SURVEY_STEPS.flatMap((step) => step.fields)
    .filter((field) => field.type === "multiSelect")
    .map((field) => field.id),
);

export const INITIAL_SURVEY_VALUES = SURVEY_STEPS.flatMap(
  (step) => step.fields,
).reduce((values, field) => {
  if (field.type === "mbti") {
    field.dimensions.forEach((dimension) => {
      values[dimension.id] = "";
    });
    return values;
  }

  values[field.id] = multiSelectFieldIds.has(field.id) ? [] : "";
  return values;
}, {});

export const TOTAL_SURVEY_STEPS = SURVEY_STEPS.length;
