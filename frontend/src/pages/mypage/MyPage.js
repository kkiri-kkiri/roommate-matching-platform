import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  Building2,
  Check,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  Heart,
  LockKeyhole,
  Moon,
  Pencil,
  Settings2,
  ShieldCheck,
  Sparkles,
  UserRound,
  Users,
} from "lucide-react";

import * as S from "./MyPageCss";

// Search filters share the survey's field names and answer options.
export const PROFILE_SECTIONS = [
  {
    id: "lifePattern",
    shortTitle: "수면 패턴",
    title: "수면 패턴",
    description: "서로 편안한 생활을 위해 수면과 방 사용 습관을 알려주세요.",
    fields: [
      {
        id: "sleepTime",
        label: "취침시간",
        type: "single",
        options: [
          "22시 이전",
          "23시 ~ 24시",
          "24시 ~ 01시",
          "01시 ~ 02시",
          "02시 ~ 03시",
          "03시 이후",
        ],
        required: true,
      },
      {
        id: "wakeTime",
        label: "기상시간",
        type: "single",
        options: [
          "06시 이전",
          "07시 ~ 08시",
          "08시 ~ 09시",
          "09시 ~ 10시",
          "10시 이후",
          "수업 시간에 따라 유동적",
        ],
        required: true,
      },
      {
        id: "sleepTalking",
        label: "잠꼬대",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "snoring",
        label: "코골이",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "teethGrinding",
        label: "이갈이",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "sleepSensitivity",
        label: "잠귀",
        type: "single",
        options: ["밝음 (소리에 잘 깨요)", "보통", "어두움 (잘 깨지 않아요)"],
        required: true,
      },
    ],
  },
  {
    id: "livingHabits",
    shortTitle: "생활 습관",
    title: "생활 습관",
    description: "공용 공간을 함께 사용하는 나의 생활 방식을 선택해주세요.",
    fields: [
      {
        id: "indoorEating",
        label: "실내 취식",
        type: "single",
        options: ["가능", "불가능", "냄새 안나는 음식 가능"],
        required: true,
      },
      {
        id: "smoking",
        label: "흡연 여부",
        type: "single",
        options: ["비흡연", "일반담배", "전자담배"],
        required: true,
      },
      {
        id: "lightingPreference",
        label: "조명 환경",
        type: "single",
        options: ["밝음 선호", "어두움 선호", "상관 없음"],
        required: true,
      },
      {
        id: "phoneCall",
        label: "전화",
        type: "single",
        options: ["항상 밖에서", "방에서는 짧게만", "상관없음"],
        required: true,
      },
      {
        id: "mediaSound",
        label: "소리(음악, OTT 등)",
        type: "single",
        options: [
          "항상 이어폰 사용해주세요",
          "공부·과제할 때 아니면 상관없음",
          "소리만 작게 해주세요",
          "자유롭게 하세요",
        ],
        required: true,
      },
      {
        id: "showerTime",
        label: "샤워 시간대",
        type: "single",
        options: ["아침", "저녁", "유동적"],
        required: true,
      },
      {
        id: "showerDuration",
        label: "샤워 소요시간",
        type: "single",
        options: ["10분", "20분", "30분", "40분", "그 이상"],
        required: true,
      },
    ],
  },
  {
    id: "dormLife",
    shortTitle: "청소 및 공동생활",
    title: "청소 및 공동생활",
    description: "룸메이트와 공유하게 될 생활 방식에 대해 알려주세요.",
    fields: [
      {
        id: "cleaningFrequency",
        label: "청소",
        type: "single",
        options: [
          "안함",
          "주 1회",
          "주 2회",
          "주 3회",
          "주 4회",
          "주 5회",
          "매일",
          "유동적",
        ],
        required: true,
      },
      {
        id: "cleaningStyle",
        label: "청소 방식",
        type: "single",
        options: ["같이 청소", "각자 영역만 청소", "상황에 따라 협의"],
        required: true,
      },
      {
        id: "dormStayDays",
        label: "기숙사 상주 주기",
        type: "multi",
        options: ["월", "화", "수", "목", "금", "토", "일"],
        allShortcut: true,
        required: true,
      },
      {
        id: "mealPlan",
        label: "신청한 기숙사 식사",
        type: "multi",
        options: ["조식", "중식", "석식", "신청 안 함"],
        exclusiveOptions: ["신청 안 함"],
        required: true,
      },
      {
        id: "roommateInteraction",
        label: "룸메이트와의 교류 정도",
        type: "single",
        options: ["친하게 지내고 싶음", "적당히 대화", "필요한 대화만"],
        required: true,
      },
      {
        id: "temperaturePreference",
        label: "온도 선호",
        type: "single",
        options: ["추위 타요", "더위 타요", "보통"],
        required: true,
      },
      {
        id: "bugHandling",
        label: "벌레",
        type: "single",
        options: ["잘 잡음", "못 잡음", "작은 건 잡음"],
        required: true,
      },
      {
        id: "scentProductUse",
        label: "향수·디퓨저 등 향 제품 사용·허용",
        type: "single",
        options: ["자주 사용", "가끔 사용", "사용 안 함", "상관없음"],
        required: true,
      },
      {
        id: "desktopUse",
        label: "데스크탑 사용·허용",
        type: "single",
        options: ["사용해요", "사용하지 않아요", "특정 시간 제외하면 상관없음"],
        required: true,
      },
      {
        id: "sharedItems",
        label: "공용물품 사용",
        type: "single",
        options: ["적극적으로 공유", "일부만 공유", "각자 사용"],
        required: true,
      },
    ],
  },
];

export const PROFILE_SURVEYS_STORAGE_KEY = "roomieProfileSurveys";

export function getSavedSurveyProfiles() {
  try {
    const profiles = JSON.parse(
      window.localStorage.getItem(PROFILE_SURVEYS_STORAGE_KEY) || "[]",
    );
    return Array.isArray(profiles)
      ? profiles.filter(
          (profile) =>
            typeof profile?.id === "string" &&
            profile.profile &&
            profile.answers &&
            typeof profile.answers === "object" &&
            !Array.isArray(profile.answers),
        )
      : [];
  } catch {
    return [];
  }
}

const PREFERENCE_STEPS = [
  {
    id: "basic",
    shortTitle: "기본·학적",
    title: "기본·학적 정보",
    description: "함께 생활하고 싶은 룸메이트의 기본 조건을 선택해 주세요.",
    fields: [
      {
        id: "grade",
        label: "학년",
        type: "multi",
        options: ["1학년", "2학년", "3학년", "4학년", "5학년", "상관없음"],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "age",
        label: "나이",
        type: "multi",
        options: ["연하", "동갑", "연상", "상관없음"],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "department",
        label: "학부·학과",
        type: "single",
        options: [
          "같은 전공",
          "같은 학부의 다른 전공",
          "다른 학부",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "sleep",
    shortTitle: "수면 패턴",
    title: "수면 패턴",
    description: "서로 편안하게 쉴 수 있도록 선호하는 수면 습관을 알려주세요.",
    fields: [
      {
        id: "bedtime",
        label: "선호하는 취침시간",
        type: "multi",
        options: [
          "22시 이전",
          "23시",
          "자정",
          "1시",
          "2시",
          "3시 이후",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "wakeTime",
        label: "선호하는 기상시간",
        type: "multi",
        options: [
          "6시 이전",
          "6시 ~ 7시",
          "7시 ~ 8시",
          "8시 ~ 9시",
          "9시",
          "10시 이후",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "sleepTalking",
        label: "잠꼬대 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
      {
        id: "snoring",
        label: "코골이 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
      {
        id: "teethGrinding",
        label: "이갈이 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "habits",
    shortTitle: "생활 습관",
    title: "생활 습관",
    description:
      "공용 공간을 함께 관리할 룸메이트의 생활 습관을 선택해 주세요.",
    fields: [
      {
        id: "smoking",
        label: "흡연 여부",
        type: "single",
        options: ["비흡연자만", "전자담배까지 허용", "상관없음"],
      },
      {
        id: "lightingPreference",
        label: "조명 환경",
        type: "single",
        options: ["밝음 선호", "어두움 선호", "상관 없음"],
        required: true,
      },
      {
        id: "showerTime",
        label: "샤워 시간대",
        type: "single",
        options: ["아침", "저녁", "상관없음"],
        required: true,
      },
      {
        id: "showerDuration",
        label: "샤워 소요시간",
        type: "single",
        options: [
          "10분 이하",
          "20분 이하",
          "30분 이하",
          "40분 이하",
          "상관없음",
        ],
        required: true,
      },
    ],
  },
  {
    id: "dormitory",
    shortTitle: "청소 및 공동생활",
    title: "청소 및 공동생활",
    description: "룸메이트와 어느 정도 일상을 나누고 싶은지 알려주세요.",
    fields: [
      {
        id: "cleaningFrequency",
        label: "선호하는 청소 주기",
        type: "multi",
        options: [
          "안함",
          "주 1회",
          "주 2회",
          "주 3회",
          "주 4회",
          "주 5회",
          "매일",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "cleaningStyle",
        label: "청소 방식",
        type: "single",
        options: [
          "같이 청소하는 사람",
          "각자 영역만 청소하는 사람",
          "상황에 따라 협의하는 사람",
          "상관없음",
        ],
      },
      {
        id: "interaction",
        label: "룸메이트와의 교류 정도",
        type: "single",
        options: [
          "친하게 지내고 싶은 사람",
          "적당히 대화하고 싶은 사람",
          "필요한 대화만 하고 싶은 사람",
          "상관없음",
        ],
      },
      {
        id: "meal",
        label: "기숙사 식사",
        type: "single",
        options: [
          "같이 먹고 싶음",
          "각자 먹고 싶음",
          "시간 될 때만 같이 먹고 싶음",
          "상관없음",
        ],
      },
      {
        id: "sharedItems",
        label: "공용물품 사용",
        type: "single",
        options: [
          "적극적으로 공유하는 사람",
          "일부만 공유하는 사람",
          "각자 사용하는 사람",
          "상관없음",
        ],
      },
      {
        id: "temperature",
        label: "온도 선호",
        type: "single",
        options: [
          "추위를 타는 사람",
          "더위를 타는 사람",
          "보통인 사람",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "requirements",
    shortTitle: "필수조건",
    title: "필수조건 설정",
    description:
      "매칭할 때 반드시 만족해야 하는 조건을 마지막으로 확인해 주세요.",
    fields: [
      {
        id: "requiredConditions",
        label: "반드시 만족해야 하는 조건",
        type: "multi",
        options: [
          "학과·학부",
          "나이",
          "학년",
          "취침시간",
          "기상시간",
          "잠꼬대",
          "코골이",
          "이갈이",
          "청소 주기",
          "청소 방식",
          "흡연 여부",
          "온도 선호",
          "룸메이트와의 교류 정도",
          "기숙사 식사",
          "공용물품 사용",
          "필수조건 없음",
        ],
        exclusiveOptions: ["필수조건 없음"],
      },
    ],
  },
];

const FACULTY_OPTIONS = [
  {
    name: "인문융합공공인재학부",
    majors: [
      "문예창작미디어콘텐츠홍보전공",
      "영미언어문화전공",
      "행정학전공",
      "노동복지전공",
    ],
  },
  { name: "법경영학부", majors: ["법학전공", "경영학전공"] },
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
  { name: "AI반도체융합학부", majors: ["AI반도체융합전공"] },
  { name: "HK자율전공학부", majors: [] },
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

const MBTI_DIMENSIONS = [
  { id: "mbtiEnergy", label: "에너지 방향", options: ["E", "I"] },
  { id: "mbtiInformation", label: "인식 방식", options: ["N", "S"] },
  { id: "mbtiDecision", label: "판단 방식", options: ["F", "T"] },
  { id: "mbtiLifestyle", label: "생활 양식", options: ["P", "J"] },
];

const PROFILE_DEFAULTS = {
  sleepTime: "23시 ~ 24시",
  wakeTime: "07시 ~ 08시",
  sleepTalking: "없음",
  snoring: "없음",
  teethGrinding: "없음",
  sleepSensitivity: "보통",
  indoorEating: "냄새 안나는 음식 가능",
  smoking: "비흡연",
  lightingPreference: "어두움 선호",
  phoneCall: "항상 밖에서",
  mediaSound: "항상 이어폰 사용해주세요",
  showerTime: "저녁",
  showerDuration: "20분",
  cleaningFrequency: "주 2회",
  cleaningStyle: "상황에 따라 협의",
  roommateInteraction: "적당히 대화",
  mealPlan: ["조식", "석식"],
  dormStayDays: ["월", "화", "수", "목", "금"],
  temperaturePreference: "보통",
  bugHandling: "작은 건 잡음",
  scentProductUse: "사용 안 함",
  desktopUse: "사용하지 않아요",
  sharedItems: "일부만 공유",
};

const PREFERENCE_DEFAULTS = {
  department: "상관없음",
  age: ["상관없음"],
  grade: ["상관없음"],
  mbtiEnergy: "상관없음",
  mbtiInformation: "상관없음",
  mbtiDecision: "상관없음",
  mbtiLifestyle: "상관없음",
  bedtime: ["23시", "자정"],
  wakeTime: ["7시 ~ 8시", "8시 ~ 9시"],
  sleepTalking: "적음까지 괜찮음",
  snoring: "없음만 가능",
  teethGrinding: "적음까지 괜찮음",
  smoking: "비흡연자만",
  lightingPreference: "어두움 선호",
  showerTime: "저녁",
  showerDuration: "20분 이하",
  cleaningFrequency: ["주 1회", "주 2회"],
  cleaningStyle: "상황에 따라 협의하는 사람",
  interaction: "적당히 대화하고 싶은 사람",
  meal: "시간 될 때만 같이 먹고 싶음",
  sharedItems: "일부만 공유하는 사람",
  temperature: "보통인 사람",
  requiredConditions: ["흡연 여부", "코골이"],
};

const PREFERENCE_SECTIONS = PREFERENCE_STEPS.filter(
  (step) => step.id !== "requirements",
);

const REQUIREMENT_LABELS = {
  department: "학부·학과",
  age: "나이",
  grade: "학년",
  bedtime: "취침시간",
  wakeTime: "기상시간",
  sleepTalking: "잠꼬대",
  snoring: "코골이",
  teethGrinding: "이갈이",
  cleaningFrequency: "청소 주기",
  cleaningStyle: "청소 방식",
  smoking: "흡연 여부",
  temperature: "온도 선호",
  interaction: "룸메이트와의 교류 정도",
  meal: "기숙사 식사",
  sharedItems: "공용물품 사용",
};

const INITIAL_ACCOUNT = {
  loginId: "haru24",
  nickname: "차분한하루",
  schoolEmail: "haru24@hknu.ac.kr",
  gender: "여자",
  dormitory: "나래",
  faculty: "컴퓨터응용수학부",
  major: "소프트웨어융합전공",
  grade: "3학년",
  mbtiEnergy: "I",
  mbtiInformation: "N",
  mbtiDecision: "F",
  mbtiLifestyle: "J",
};

const MENU_ITEMS = [
  { id: "profile", label: "프로필", icon: UserRound },
  { id: "survey", label: "내 설문", icon: ClipboardList },
  { id: "preferences", label: "선호 룸메 설문", icon: Users },
  { id: "likes", label: "좋아요", icon: Heart },
  { id: "account", label: "계정 정보", icon: Settings2 },
];

function createAvatar(background, shirt, hair = "#363a46") {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160">
    <rect width="160" height="160" rx="24" fill="${background}"/>
    <circle cx="80" cy="66" r="31" fill="#f2c5a7"/>
    <path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="${hair}"/>
    <path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="${shirt}"/>
    <circle cx="68" cy="67" r="2.4" fill="#3d3532"/><circle cx="92" cy="67" r="2.4" fill="#3d3532"/>
    <path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round"/>
  </svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

const MY_PHOTO = createAvatar("#e8eef9", "#0c3273");
const LIKED_PROFILES = [
  {
    id: "roommate-cloud",
    nickname: "포근한구름",
    year: "24",
    major: "소프트웨어융합전공",
    faculty: "컴퓨터응용수학부",
    dormitory: "나래",
    grade: "3학년",
    age: "동갑",
    photo: createAvatar("#dff6f2", "#00a79f"),
    message: "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
    tags: ["비흡연", "아침형", "주 1회 청소"],
    answers: {
      mbtiEnergy: "I",
      mbtiInformation: "N",
      mbtiDecision: "F",
      mbtiLifestyle: "J",
      bedtime: "23시",
      wakeTime: "7시 ~ 8시",
      sleepTalking: "없음",
      snoring: "없음",
      teethGrinding: "없음",
      smoking: "비흡연",
      lightingPreference: "어두움 선호",
      showerTime: "저녁",
      showerDuration: "20분",
      cleaningFrequency: "주 1회",
      cleaningStyle: "상황에 따라 협의하는 사람",
      interaction: "적당히 대화하고 싶은 사람",
      meal: "시간 될 때만 같이 먹고 싶음",
      sharedItems: "일부만 공유하는 사람",
      temperature: "보통인 사람",
    },
  },
  {
    id: "community-star",
    nickname: "새벽별",
    year: "23",
    major: "경영학전공",
    faculty: "법경영학부",
    dormitory: "호연",
    grade: "4학년",
    age: "연상",
    photo: createAvatar("#e6edff", "#3478f6", "#2c2625"),
    message: "늦게 자는 편이지만 방에서는 늘 이어폰을 사용해요.",
    tags: ["이어폰 사용", "깔끔해요", "개인 시간"],
    answers: {
      mbtiEnergy: "I",
      mbtiInformation: "S",
      mbtiDecision: "T",
      mbtiLifestyle: "P",
      bedtime: "2시",
      wakeTime: "10시 이후",
      sleepTalking: "적음",
      snoring: "적음",
      teethGrinding: "없음",
      smoking: "비흡연",
      lightingPreference: "어두움 선호",
      showerTime: "저녁",
      showerDuration: "30분",
      cleaningFrequency: "주 2회",
      cleaningStyle: "각자 영역만 청소하는 사람",
      interaction: "필요한 대화만 하고 싶은 사람",
      meal: "각자 먹고 싶음",
      sharedItems: "각자 사용하는 사람",
      temperature: "추위를 타는 사람",
    },
  },
  {
    id: "dormitory-afternoon",
    nickname: "느긋한오후",
    year: "24",
    major: "식품영양학전공",
    faculty: "웰니스산업융합학부",
    dormitory: "나래",
    grade: "3학년",
    age: "동갑",
    photo: createAvatar("#fce8ed", "#d8607a", "#4c3028"),
    message: "조용하고 여유로운 분위기에서 각자의 루틴을 지켜요.",
    tags: ["비흡연", "조용한 방", "주말 외출"],
    answers: {
      mbtiEnergy: "I",
      mbtiInformation: "N",
      mbtiDecision: "F",
      mbtiLifestyle: "P",
      bedtime: "자정",
      wakeTime: "8시 ~ 9시",
      sleepTalking: "없음",
      snoring: "없음",
      teethGrinding: "적음",
      smoking: "비흡연",
      lightingPreference: "밝음 선호",
      showerTime: "아침",
      showerDuration: "20분",
      cleaningFrequency: "주 1회",
      cleaningStyle: "상황에 따라 협의하는 사람",
      interaction: "필요한 대화만 하고 싶은 사람",
      meal: "각자 먹고 싶음",
      sharedItems: "일부만 공유하는 사람",
      temperature: "보통인 사람",
    },
  },
];

const asArray = (value) => (Array.isArray(value) ? value : [value]);
const hasAnswer = (value) =>
  asArray(value).some((item) => String(item || "").trim());
const isIndifferent = (value) =>
  asArray(value).some((item) => String(item).includes("상관"));
const displayAnswer = (value) =>
  Array.isArray(value) ? value.join(" · ") : value;
const profileHref = (profile) =>
  `/chat?room=${encodeURIComponent(profile.id)}&view=profile`;
const getMbti = (account) =>
  MBTI_DIMENSIONS.map(({ id }) => account[id]).join("");

function EmptyState({ type, onStart }) {
  const isLikes = type === "likes";
  const isPreference = type === "preferences";
  const Icon = isLikes ? Heart : isPreference ? Users : ClipboardList;
  return (
    <S.EmptyCard>
      <S.EmptyIcon>
        <Icon size={30} aria-hidden="true" />
      </S.EmptyIcon>
      <S.Badge $muted>{isLikes ? "좋아요 0명" : "미작성"}</S.Badge>
      <S.EmptyTitle>
        {isLikes
          ? "아직 마음에 담은 룸메이트가 없어요"
          : isPreference
            ? "어떤 룸메이트와 함께하고 싶나요?"
            : "아직 나의 설문을 작성하지 않았어요"}
      </S.EmptyTitle>
      {isLikes && (
        <S.EmptyText>
          마음이 맞을 것 같은 룸메이트를 찾아보세요. 좋아요한 프로필을 여기서
          모아볼 수 있어요.
        </S.EmptyText>
      )}
      <S.PrimaryButton
        {...(isLikes
          ? { as: Link, to: "/roommates" }
          : { type: "button", onClick: onStart })}
      >
        {isLikes
          ? "룸메이트 둘러보기"
          : isPreference
            ? "선호 룸메 설문 작성하기"
            : "내 설문 작성하기"}
        <ArrowRight size={16} aria-hidden="true" />
      </S.PrimaryButton>
    </S.EmptyCard>
  );
}

function SurveyContent({ type, answers, isEmpty, onSave }) {
  const isPreference = type === "preferences";
  const sections = isPreference ? PREFERENCE_SECTIONS : PROFILE_SECTIONS;
  const [draft, setDraft] = useState(null);
  const [errors, setErrors] = useState({});
  const formRef = useRef(null);
  const titleRef = useRef(null);
  const values = draft || answers;
  const requirements = isPreference
    ? values.requiredConditions.filter((item) => item !== "필수조건 없음")
    : [];

  const updateValue = (field, option) => {
    let value = option;
    if (field.type === "multi") {
      const selected = draft[field.id];
      const exclusive = field.exclusiveOptions || [];
      const regular = selected.filter((item) => !exclusive.includes(item));
      value = selected.includes(option)
        ? selected.filter((item) => item !== option)
        : exclusive.includes(option)
          ? [option]
          : [...regular, option];
    }
    setDraft((current) => ({
      ...current,
      [field.id]: value,
      ...(isPreference && (!hasAnswer(value) || isIndifferent(value))
        ? {
            requiredConditions: current.requiredConditions.filter(
              (item) => item !== REQUIREMENT_LABELS[field.id],
            ),
          }
        : {}),
    }));
    setErrors((current) => ({ ...current, [field.id]: undefined }));
  };

  const save = (event) => {
    event.preventDefault();
    const missing = sections
      .flatMap((section) => section.fields)
      .filter((field) => !hasAnswer(draft[field.id]));
    if (missing.length) {
      setErrors(
        Object.fromEntries(
          missing.map((field) => [
            field.id,
            `${field.label} 항목을 선택해 주세요.`,
          ]),
        ),
      );
      requestAnimationFrame(() =>
        formRef.current?.querySelector('[aria-invalid="true"]')?.focus(),
      );
      return;
    }
    const saved = isPreference
      ? {
          ...draft,
          requiredConditions: requirements.length
            ? requirements
            : ["필수조건 없음"],
        }
      : draft;
    if (onSave(saved) === false) return;
    setDraft(null);
    setErrors({});
    requestAnimationFrame(() => {
      window.scrollTo(0, 0);
      titleRef.current?.focus({ preventScroll: true });
    });
  };

  const startEditing = (focusRequirements = false) => {
    const emptyAnswers = Object.fromEntries(
      sections.flatMap((section) =>
        section.fields.map((field) => [
          field.id,
          field.type === "multi" ? [] : "",
        ]),
      ),
    );
    setDraft(
      isEmpty
        ? {
            ...emptyAnswers,
            ...(isPreference ? { requiredConditions: [] } : {}),
          }
        : { ...answers },
    );
    setErrors({});
    requestAnimationFrame(() =>
      formRef.current
        ?.querySelector(
          focusRequirements
            ? "[data-required-conditions] button:not(:disabled)"
            : "select, button",
        )
        ?.focus(),
    );
  };

  return (
    <S.Stack>
      <S.SectionHeading>
        <div>
          <S.SectionTitle ref={titleRef} tabIndex={-1}>
            {isPreference ? "선호 룸메 설문" : "내 설문"}
          </S.SectionTitle>
          <S.Description>
            {isPreference
              ? "함께하고 싶은 룸메이트의 모습을 정리했어요."
              : "나의 하루를 담은 생활 습관이에요."}
          </S.Description>
        </div>
        {!isEmpty && !draft && (
          <S.SecondaryButton onClick={() => startEditing()}>
            <Pencil size={16} aria-hidden="true" /> 설문 수정
          </S.SecondaryButton>
        )}
      </S.SectionHeading>
      {isEmpty && !draft ? (
        <EmptyState type={type} onStart={() => startEditing()} />
      ) : (
        <>
          <S.Stack as="form" ref={formRef} onSubmit={save} noValidate>
            {sections.map((section, index) => (
              <S.Card key={section.id}>
                <S.CardHeader>
                  <S.CardTitle>
                    {String(index + 1).padStart(2, "0")} · {section.title}
                  </S.CardTitle>
                  <S.Badge $muted>{section.fields.length}개 항목</S.Badge>
                </S.CardHeader>
                {draft ? (
                  <S.FormGrid>
                    {section.fields.map((field) => (
                      <S.Field key={field.id}>
                        {field.type === "multi" ? (
                          <S.Fieldset>
                            <S.Legend>
                              {field.label}{" "}
                              {requirements.includes(
                                REQUIREMENT_LABELS[field.id],
                              ) && <S.RequiredBadge>필수</S.RequiredBadge>}
                            </S.Legend>
                            <S.Helper>복수 선택 가능</S.Helper>
                            <S.ChoiceGroup>
                              {field.options.map((option) => (
                                <S.ChoiceButton
                                  key={option}
                                  type="button"
                                  $selected={draft[field.id].includes(option)}
                                  aria-pressed={draft[field.id].includes(
                                    option,
                                  )}
                                  aria-invalid={Boolean(errors[field.id])}
                                  aria-describedby={
                                    errors[field.id]
                                      ? `${type}-${field.id}-error`
                                      : undefined
                                  }
                                  onClick={() => updateValue(field, option)}
                                >
                                  {draft[field.id].includes(option) && (
                                    <Check size={14} aria-hidden="true" />
                                  )}
                                  {option}
                                </S.ChoiceButton>
                              ))}
                            </S.ChoiceGroup>
                          </S.Fieldset>
                        ) : (
                          <>
                            <S.Label htmlFor={`${type}-${field.id}`}>
                              {field.label}{" "}
                              {requirements.includes(
                                REQUIREMENT_LABELS[field.id],
                              ) && <S.RequiredBadge>필수</S.RequiredBadge>}
                            </S.Label>
                            <S.Select
                              id={`${type}-${field.id}`}
                              value={draft[field.id]}
                              onChange={(event) =>
                                updateValue(field, event.target.value)
                              }
                              aria-invalid={Boolean(errors[field.id])}
                              aria-describedby={
                                errors[field.id]
                                  ? `${type}-${field.id}-error`
                                  : undefined
                              }
                            >
                              <option value="">선택해 주세요</option>
                              {field.options.map((option) => (
                                <option key={option} value={option}>
                                  {option}
                                </option>
                              ))}
                            </S.Select>
                          </>
                        )}
                        {errors[field.id] && (
                          <S.FieldError
                            id={`${type}-${field.id}-error`}
                            role="alert"
                          >
                            {errors[field.id]}
                          </S.FieldError>
                        )}
                      </S.Field>
                    ))}
                  </S.FormGrid>
                ) : (
                  <S.SurveyGrid>
                    {section.fields.map((field) => (
                      <S.SurveyRow key={field.id}>
                        <dt>
                          {field.label}{" "}
                          {requirements.includes(
                            REQUIREMENT_LABELS[field.id],
                          ) && <S.RequiredBadge>필수</S.RequiredBadge>}
                        </dt>
                        <dd>{displayAnswer(values[field.id])}</dd>
                      </S.SurveyRow>
                    ))}
                  </S.SurveyGrid>
                )}
              </S.Card>
            ))}
            {isPreference && (
              <S.Card data-required-conditions>
                <S.CardHeader>
                  <S.CardTitle>
                    <ShieldCheck size={19} aria-hidden="true" /> 필수 조건
                  </S.CardTitle>
                  {!draft && (
                    <S.GhostButton
                      type="button"
                      onClick={() => startEditing(true)}
                    >
                      <Pencil size={15} aria-hidden="true" /> 조건 수정
                    </S.GhostButton>
                  )}
                </S.CardHeader>
                <S.Helper>
                  {draft
                    ? "구체적인 선호를 선택한 항목만 필수로 지정할 수 있어요. ‘상관없음’인 항목은 제외돼요."
                    : "이 조건은 추천할 때 꼭 충족해야 해요."}
                </S.Helper>
                {draft ? (
                  <S.ChoiceGroup>
                    {Object.entries(REQUIREMENT_LABELS).map(([id, label]) => (
                      <S.ChoiceButton
                        key={id}
                        type="button"
                        disabled={
                          !hasAnswer(draft[id]) || isIndifferent(draft[id])
                        }
                        $selected={requirements.includes(label)}
                        aria-pressed={requirements.includes(label)}
                        onClick={() =>
                          setDraft((current) => ({
                            ...current,
                            requiredConditions: requirements.includes(label)
                              ? requirements.filter((item) => item !== label)
                              : [...requirements, label],
                          }))
                        }
                      >
                        {requirements.includes(label) && (
                          <Check size={14} aria-hidden="true" />
                        )}
                        {label}
                      </S.ChoiceButton>
                    ))}
                    <S.ChoiceButton
                      type="button"
                      $selected={!requirements.length}
                      aria-pressed={!requirements.length}
                      onClick={() =>
                        setDraft((current) => ({
                          ...current,
                          requiredConditions: [],
                        }))
                      }
                    >
                      필수조건 없음
                    </S.ChoiceButton>
                  </S.ChoiceGroup>
                ) : (
                  <S.TagRow>
                    {requirements.length ? (
                      requirements.map((label) => (
                        <S.Badge key={label}>
                          <Check size={13} aria-hidden="true" />
                          {label} · 필수
                        </S.Badge>
                      ))
                    ) : (
                      <S.Tag>필수조건 없음</S.Tag>
                    )}
                  </S.TagRow>
                )}
              </S.Card>
            )}
            {draft && (
              <S.FormActions>
                <S.GhostButton
                  type="button"
                  onClick={() => {
                    setDraft(null);
                    setErrors({});
                    requestAnimationFrame(() => titleRef.current?.focus());
                  }}
                >
                  취소
                </S.GhostButton>
                <S.PrimaryButton type="submit">
                  <Check size={17} aria-hidden="true" /> 저장
                </S.PrimaryButton>
              </S.FormActions>
            )}
          </S.Stack>
        </>
      )}
    </S.Stack>
  );
}

function AccountContent({ account, onSave }) {
  const [draft, setDraft] = useState({
    ...account,
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [editingFields, setEditingFields] = useState({});
  const [passwordEditing, setPasswordEditing] = useState(false);
  const [errors, setErrors] = useState({});
  const formRef = useRef(null);
  const faculty = FACULTY_OPTIONS.find((item) => item.name === draft.faculty);
  const noMajor = Boolean(faculty && faculty.majors.length === 0);
  const basicFields = ["nickname"];
  const academicFields = ["dormitory", "grade", "faculty", "major"];
  const mbtiFields = MBTI_DIMENSIONS.map(({ id }) => id);
  const basicEditing = basicFields.some((id) => editingFields[id]);
  const academicEditing = academicFields.some((id) => editingFields[id]);
  const mbtiEditing = mbtiFields.some((id) => editingFields[id]);

  const update = (id, value) => {
    setDraft((current) => ({
      ...current,
      [id]: value,
      ...(id === "faculty"
        ? {
            major:
              FACULTY_OPTIONS.find((item) => item.name === value)?.majors
                .length === 0
                ? "세부전공 없음"
                : "",
          }
        : {}),
    }));
    setErrors((current) => ({
      ...current,
      [id]: undefined,
      ...(id === "faculty" ? { major: undefined } : {}),
    }));
  };

  const finishSection = (ids) => {
    const nextErrors = {};
    ids.forEach((id) => {
      if (!draft[id].trim())
        nextErrors[id] = `${id} 항목을 입력하거나 선택해 주세요.`;
    });
    if (
      ids.includes("faculty") &&
      faculty?.majors.length &&
      !faculty.majors.includes(draft.major)
    ) {
      nextErrors.faculty = "선택한 학부에 해당하는 전공도 확인해 주세요.";
    }
    if (ids.some((id) => MBTI_DIMENSIONS.some((item) => item.id === id))) {
      MBTI_DIMENSIONS.forEach(({ id, label, options }) => {
        if (ids.includes(id) && !options.includes(draft[id]))
          nextErrors[id] = `${label}을 선택해 주세요.`;
      });
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    onSave({
      ...account,
      ...Object.fromEntries(ids.map((id) => [id, draft[id]])),
    });
    setEditingFields((current) =>
      Object.fromEntries(Object.keys(current).map((id) => [id, false])),
    );
  };

  const setSectionEditing = (ids, value) => {
    setEditingFields((current) => ({
      ...current,
      ...Object.fromEntries(ids.map((id) => [id, value])),
    }));
    setErrors({});
  };

  const finishPassword = () => {
    const hasPasswordInput = Boolean(
      draft.currentPassword || draft.newPassword || draft.confirmPassword,
    );
    if (!hasPasswordInput) {
      setErrors({});
      setPasswordEditing(false);
      return;
    }
    const nextErrors = {};
    if (!draft.currentPassword.trim())
      nextErrors.currentPassword = "현재 비밀번호를 입력해 주세요.";
    if (!draft.newPassword.trim())
      nextErrors.newPassword = "변경할 비밀번호를 입력해 주세요.";
    else if (draft.newPassword.length < 8)
      nextErrors.newPassword = "비밀번호는 8자 이상 입력해 주세요.";
    if (!draft.confirmPassword)
      nextErrors.confirmPassword = "변경할 비밀번호를 한 번 더 입력해 주세요.";
    else if (draft.newPassword !== draft.confirmPassword)
      nextErrors.confirmPassword = "새 비밀번호가 일치하지 않아요.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    setPasswordEditing(false);
    setDraft((current) => ({
      ...current,
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    }));
  };

  const save = (event) => {
    event.preventDefault();
    if (passwordEditing) finishPassword();
  };

  const renderField = (
    id,
    label,
    { options, readOnly, type = "text", helper } = {},
  ) => {
    const isPassword = type === "password";
    const isEditing = isPassword ? passwordEditing : editingFields[id];
    return (
      <S.Field key={id}>
        <S.Label htmlFor={`account-${id}`}>
          {label}
          {!readOnly && !isPassword && " *"}
          {readOnly && <LockKeyhole size={12} aria-hidden="true" />}
        </S.Label>
        {readOnly || !isEditing ? (
          <S.ReadonlyValue>
            {isPassword ? "••••••••" : draft[id] || "-"}
          </S.ReadonlyValue>
        ) : options ? (
          <S.Select
            id={`account-${id}`}
            value={draft[id]}
            disabled={id === "major" && (!faculty || noMajor)}
            aria-required="true"
            aria-invalid={Boolean(errors[id])}
            aria-describedby={`account-${id}-help`}
            onChange={(event) => update(id, event.target.value)}
          >
            {noMajor && id === "major" ? (
              <option value="세부전공 없음">세부전공 없음</option>
            ) : (
              <>
                <option value="">
                  {id === "major" && !faculty
                    ? "학부를 먼저 선택해 주세요"
                    : `${label} 선택`}
                </option>
                {options.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </>
            )}
          </S.Select>
        ) : (
          <S.Input
            id={`account-${id}`}
            type={type}
            value={draft[id]}
            maxLength={id === "nickname" ? 20 : undefined}
            autoComplete={
              type === "password"
                ? id === "currentPassword"
                  ? "current-password"
                  : "new-password"
                : "off"
            }
            aria-required={!readOnly && type !== "password"}
            aria-invalid={Boolean(errors[id])}
            aria-describedby={`account-${id}-help`}
            onChange={(event) => update(id, event.target.value)}
          />
        )}
        {errors[id] ? (
          <S.FieldError id={`account-${id}-help`} role="alert">
            {errors[id]}
          </S.FieldError>
        ) : (
          <S.Helper id={`account-${id}-help`}>{helper || ""}</S.Helper>
        )}
      </S.Field>
    );
  };

  return (
    <S.Stack as="form" ref={formRef} onSubmit={save} noValidate>
      <S.SectionHeading>
        <div>
          <S.SectionTitle>계정 정보</S.SectionTitle>
          <S.Description>나의 정보를 최신 상태로 관리해 주세요.</S.Description>
        </div>
        <S.Badge $muted>필요한 항목만 수정할 수 있어요</S.Badge>
      </S.SectionHeading>
      <S.Card>
        <S.CardHeader>
          <S.CardTitle>
            <UserRound size={19} aria-hidden="true" /> 기본 정보
          </S.CardTitle>
          <S.SecondaryButton
            type="button"
            onClick={() =>
              basicEditing
                ? finishSection(basicFields)
                : setSectionEditing(basicFields, true)
            }
          >
            {basicEditing ? (
              <>
                <Check size={16} aria-hidden="true" /> 완료
              </>
            ) : (
              <>
                <Pencil size={16} aria-hidden="true" /> 수정
              </>
            )}
          </S.SecondaryButton>
        </S.CardHeader>
        <S.FormGrid>
          {renderField("loginId", "아이디", { readOnly: true })}
          {renderField("nickname", "닉네임")}
          {renderField("schoolEmail", "학교 이메일", { readOnly: true })}
          {renderField("gender", "성별", { readOnly: true })}
        </S.FormGrid>
      </S.Card>
      <S.Card>
        <S.CardHeader>
          <S.CardTitle>
            <Building2 size={19} aria-hidden="true" /> 생활관 및 학적 정보
          </S.CardTitle>
          <S.SecondaryButton
            type="button"
            onClick={() =>
              academicEditing
                ? finishSection(academicFields)
                : setSectionEditing(academicFields, true)
            }
          >
            {academicEditing ? (
              <>
                <Check size={16} aria-hidden="true" /> 완료
              </>
            ) : (
              <>
                <Pencil size={16} aria-hidden="true" /> 수정
              </>
            )}
          </S.SecondaryButton>
        </S.CardHeader>
        <S.FormGrid>
          {renderField("dormitory", "생활관", {
            options: ["나래", "호연", "비봉", "창조"],
          })}
          {renderField("grade", "학년", {
            options: ["1학년", "2학년", "3학년", "4학년", "5학년"],
          })}
          {renderField("faculty", "나의 학부", {
            options: FACULTY_OPTIONS.map((item) => item.name),
          })}
          {renderField("major", "나의 전공", {
            options: faculty?.majors || [],
            helper: noMajor ? "선택한 학부에는 세부전공이 없어요." : "",
          })}
        </S.FormGrid>
      </S.Card>
      <S.Card>
        <S.CardHeader>
          <S.CardTitle>
            <Sparkles size={19} aria-hidden="true" /> MBTI
            <S.MbtiValue>{getMbti(draft)}</S.MbtiValue>
          </S.CardTitle>
          <S.CardHeaderActions>
            <S.SecondaryButton
              type="button"
              onClick={() =>
                mbtiEditing
                  ? finishSection(mbtiFields)
                  : setSectionEditing(mbtiFields, true)
              }
            >
              {mbtiEditing ? (
                <>
                  <Check size={16} aria-hidden="true" /> 완료
                </>
              ) : (
                <>
                  <Pencil size={16} aria-hidden="true" /> 수정
                </>
              )}
            </S.SecondaryButton>
          </S.CardHeaderActions>
        </S.CardHeader>
        <S.MbtiGrid>
          {MBTI_DIMENSIONS.map(({ id, label, options }) => (
            <S.Fieldset key={id}>
              <S.Legend>{label} *</S.Legend>
              {!editingFields[id] ? (
                <S.ReadonlyValue>{draft[id]}</S.ReadonlyValue>
              ) : (
                <>
                  <S.ChoiceGroup>
                    {options.map((option) => (
                      <S.ChoiceButton
                        key={option}
                        type="button"
                        $selected={draft[id] === option}
                        aria-pressed={draft[id] === option}
                        aria-invalid={Boolean(errors[id])}
                        aria-describedby={
                          errors[id] ? `${id}-error` : undefined
                        }
                        onClick={() => update(id, option)}
                      >
                        {option}
                      </S.ChoiceButton>
                    ))}
                  </S.ChoiceGroup>
                </>
              )}
              {errors[id] && (
                <S.FieldError id={`${id}-error`} role="alert">
                  {errors[id]}
                </S.FieldError>
              )}
            </S.Fieldset>
          ))}
        </S.MbtiGrid>
      </S.Card>
      <S.Card>
        <S.CardHeader>
          <S.CardTitle>
            <LockKeyhole size={19} aria-hidden="true" /> 비밀번호 변경
          </S.CardTitle>
          <S.SecondaryButton
            type="button"
            onClick={() =>
              passwordEditing ? finishPassword() : setPasswordEditing(true)
            }
          >
            {passwordEditing ? (
              <>
                <Check size={16} aria-hidden="true" /> 완료
              </>
            ) : (
              <>
                <Pencil size={16} aria-hidden="true" /> 수정
              </>
            )}
          </S.SecondaryButton>
        </S.CardHeader>
        {!passwordEditing && (
          <S.PasswordSummary>
            <LockKeyhole size={18} aria-hidden="true" />
            <span>
              비밀번호는 안전하게 설정되어 있어요. 변경이 필요할 때 수정해
              주세요.
            </span>
          </S.PasswordSummary>
        )}
        {passwordEditing && (
          <>
            <S.Helper>변경을 원할 때만 세 항목을 모두 입력해 주세요.</S.Helper>
            <S.FormGrid>
              {renderField("currentPassword", "현재 비밀번호", {
                type: "password",
              })}
              {renderField("newPassword", "변경할 비밀번호", {
                type: "password",
              })}
              {renderField("confirmPassword", "변경할 비밀번호 확인", {
                type: "password",
              })}
            </S.FormGrid>
          </>
        )}
      </S.Card>
    </S.Stack>
  );
}

function MyPage({ currentUser }) {
  const surveyProfileId = `survey:${currentUser?.name || INITIAL_ACCOUNT.loginId}`;
  const [savedSurvey] = useState(() =>
    getSavedSurveyProfiles().find((profile) => profile.id === surveyProfileId),
  );
  const [searchParams] = useSearchParams();
  const activeTab = MENU_ITEMS.some(
    (item) => item.id === searchParams.get("tab"),
  )
    ? searchParams.get("tab")
    : "profile";

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [activeTab]);

  const [account, setAccount] = useState(() => ({
    ...INITIAL_ACCOUNT,
    nickname: currentUser?.name || INITIAL_ACCOUNT.nickname,
    ...savedSurvey?.profile,
  }));
  const [profileAnswers, setProfileAnswers] = useState(
    savedSurvey?.answers || PROFILE_DEFAULTS,
  );
  const [surveySaveError, setSurveySaveError] = useState("");
  const [preferences, setPreferences] = useState(PREFERENCE_DEFAULTS);
  const [likedIds, setLikedIds] = useState(() =>
    LIKED_PROFILES.map(({ id }) => id),
  );
  const [preview, setPreview] = useState({
    survey: "filled",
    preferences: "filled",
    likes: "filled",
  });
  const [likeMessage, setLikeMessage] = useState("");
  const lastRemoved = useRef(null);
  const likesTitleRef = useRef(null);
  const likedProfiles =
    preview.likes === "empty"
      ? []
      : LIKED_PROFILES.filter(({ id }) => likedIds.includes(id));
  const completeCount = [preview.survey, preview.preferences].filter(
    (value) => value === "filled",
  ).length;

  const removeLike = (profile) => {
    setLikedIds((current) => current.filter((id) => id !== profile.id));
    lastRemoved.current = profile.id;
    setLikeMessage(`${profile.nickname}님의 좋아요를 취소했어요.`);
    requestAnimationFrame(() => likesTitleRef.current?.focus());
  };

  const detailItems = [
    ["닉네임", account.nickname],
    ["성별", account.gender],
    ["생활관", `${account.dormitory}관`],
    ["학년", account.grade],
    ["나의 학부", account.faculty],
    ["나의 전공", account.major],
    ["MBTI", getMbti(account)],
  ];

  return (
    <S.Page>
      <S.Container>
        <S.Layout>
          <S.Sidebar>
            <S.SidebarProfile>
              <S.Avatar src={MY_PHOTO} alt="" />
              <div>
                <S.SidebarName>{account.nickname}</S.SidebarName>
                <S.Muted>
                  {account.dormitory}관 · {getMbti(account)}
                </S.Muted>
              </div>
            </S.SidebarProfile>
            <S.Menu aria-label="마이페이지 메뉴">
              {MENU_ITEMS.map(({ id, label, icon: Icon }) => (
                <S.MenuLink
                  key={id}
                  to={id === "profile" ? "/profile" : `/profile?tab=${id}`}
                  $active={activeTab === id}
                  aria-current={activeTab === id ? "page" : undefined}
                >
                  <Icon size={19} aria-hidden="true" />
                  <span>{label}</span>
                  {id === "likes" && (
                    <S.MenuCount>{likedProfiles.length}</S.MenuCount>
                  )}
                  {activeTab === id && (
                    <ChevronRight size={16} aria-hidden="true" />
                  )}
                </S.MenuLink>
              ))}
            </S.Menu>
            <S.SideNote>
              <ShieldCheck size={20} aria-hidden="true" />
              <strong>서로를 알아가는 첫걸음</strong>
              <S.Muted>
                나의 정보와 설문을 채우고 더 편안한 기숙사 생활을 준비해요.
              </S.Muted>
            </S.SideNote>
          </S.Sidebar>
          <S.Content
            aria-label={MENU_ITEMS.find(({ id }) => id === activeTab).label}
          >
            {activeTab === "profile" && (
              <S.Stack>
                <S.SectionHeading>
                  <div>
                    <S.SectionTitle>내 프로필</S.SectionTitle>
                    <S.Description>
                      룸메이트에게 보여줄 나의 기본 정보예요.
                    </S.Description>
                  </div>
                  <S.SecondaryButton as={Link} to="/profile?tab=account">
                    <Pencil size={16} aria-hidden="true" /> 프로필 수정
                  </S.SecondaryButton>
                </S.SectionHeading>
                <S.Card>
                  <S.ProfileHero>
                    <S.Avatar
                      $large
                      src={MY_PHOTO}
                      alt={`${account.nickname} 프로필`}
                    />
                    <div>
                      <S.Badge>
                        <BadgeCheck size={14} aria-hidden="true" />{" "}
                        한경국립대학교
                      </S.Badge>
                      <S.ProfileName>{account.nickname}</S.ProfileName>
                      <S.Muted>
                        {account.major} · {account.grade}
                      </S.Muted>
                      <S.TagRow>
                        <S.Tag>{account.dormitory}관</S.Tag>
                        <S.Tag>{getMbti(account)}</S.Tag>
                      </S.TagRow>
                    </div>
                  </S.ProfileHero>
                  <S.DetailGrid>
                    {detailItems.map(([label, value]) => (
                      <S.DetailItem key={label}>
                        <dt>{label}</dt>
                        <dd>{value}</dd>
                      </S.DetailItem>
                    ))}
                  </S.DetailGrid>
                </S.Card>
                <S.Card>
                  <S.CardHeader>
                    <S.CardTitle>
                      <ClipboardList size={19} aria-hidden="true" /> 나의 설문
                      현황
                    </S.CardTitle>
                    <S.Badge>{completeCount} / 2 완료</S.Badge>
                  </S.CardHeader>
                  <S.Helper>
                    두 가지 설문으로 나와 잘 맞는 룸메이트를 더 가까이
                    만나보세요.
                  </S.Helper>
                  <S.ProgressTrack
                    role="progressbar"
                    aria-label="설문 작성 현황"
                    aria-valuemin={0}
                    aria-valuemax={2}
                    aria-valuenow={completeCount}
                  >
                    <S.ProgressFill $value={completeCount * 50} />
                  </S.ProgressTrack>
                  <S.SummaryGrid>
                    {[
                      {
                        id: "survey",
                        icon: Moon,
                        title: "내 설문",
                        text: "나의 수면 패턴과 생활 습관",
                      },
                      {
                        id: "preferences",
                        icon: Users,
                        title: "선호 룸메 설문",
                        text: "함께하고 싶은 룸메이트의 모습",
                      },
                    ].map(({ id, icon: Icon, title, text }) => (
                      <S.SummaryCard
                        as={Link}
                        key={id}
                        to={`/profile?tab=${id}`}
                        onClick={() => window.scrollTo(0, 0)}
                      >
                        <S.CardHeader>
                          <Icon size={23} aria-hidden="true" />
                          <S.Badge $muted={preview[id] === "empty"}>
                            {preview[id] === "filled" ? "작성 완료" : "미작성"}
                          </S.Badge>
                        </S.CardHeader>
                        <S.SummaryTitle>{title}</S.SummaryTitle>
                        <S.SummaryText>{text}</S.SummaryText>
                        <S.SummaryFooter>
                          {preview[id] === "filled"
                            ? "답변 확인하기"
                            : "설문 알아보기"}
                          <ArrowRight size={16} aria-hidden="true" />
                        </S.SummaryFooter>
                      </S.SummaryCard>
                    ))}
                  </S.SummaryGrid>
                </S.Card>
              </S.Stack>
            )}
            {(activeTab === "survey" || activeTab === "preferences") && (
              <>
              {activeTab === "survey" && surveySaveError && (
                <S.Banner $error role="alert">{surveySaveError}</S.Banner>
              )}
              <SurveyContent
                key={`${activeTab}-${preview[activeTab]}`}
                type={activeTab}
                answers={activeTab === "survey" ? profileAnswers : preferences}
                isEmpty={preview[activeTab] === "empty"}
                onSave={(savedAnswers) => {
                  if (activeTab === "survey") {
                    const savedProfile = {
                      id: surveyProfileId,
                      profile: {
                        nickname: account.nickname,
                        major: account.major,
                        dormitory: account.dormitory,
                        grade: account.grade,
                      },
                      answers: savedAnswers,
                    };
                    try {
                      window.localStorage.setItem(
                        PROFILE_SURVEYS_STORAGE_KEY,
                        JSON.stringify([
                          ...getSavedSurveyProfiles().filter(
                            (profile) => profile.id !== surveyProfileId,
                          ),
                          savedProfile,
                        ]),
                      );
                    } catch {
                      setSurveySaveError("설문을 저장하지 못했어요. 브라우저 저장소 설정을 확인한 뒤 다시 시도해 주세요.");
                      return false;
                    }
                    setSurveySaveError("");
                    setProfileAnswers(savedAnswers);
                  } else {
                    setPreferences(savedAnswers);
                  }
                  setPreview((current) => ({
                    ...current,
                    [activeTab]: "filled",
                  }));
                }}
              />
              </>
            )}
            {activeTab === "likes" && (
              <S.Stack>
                <S.SectionHeading>
                  <div>
                    <S.SectionTitle ref={likesTitleRef} tabIndex={-1}>
                      좋아요 <S.Badge>{likedProfiles.length}명</S.Badge>
                    </S.SectionTitle>
                    <S.Description>
                      마음에 담아둔 룸메이트를 다시 만나보세요.
                    </S.Description>
                  </div>
                  <S.SubtleLink to="/roommates">
                    룸메이트 둘러보기{" "}
                    <ArrowRight size={16} aria-hidden="true" />
                  </S.SubtleLink>
                </S.SectionHeading>
                {likeMessage && (
                  <S.Banner role="status">
                    <CheckCircle2 size={19} aria-hidden="true" />
                    <span>{likeMessage}</span>
                    {lastRemoved.current && (
                      <S.GhostButton
                        type="button"
                        onClick={() => {
                          const id = lastRemoved.current;
                          setLikedIds((current) =>
                            current.includes(id) ? current : [...current, id],
                          );
                          lastRemoved.current = null;
                          setLikeMessage("좋아요를 다시 담았어요.");
                        }}
                      >
                        되돌리기
                      </S.GhostButton>
                    )}
                  </S.Banner>
                )}
                {!likedProfiles.length ? (
                  <EmptyState type="likes" />
                ) : (
                  <S.LikeGrid>
                    {likedProfiles.map((profile) => (
                      <S.LikeCard key={profile.id}>
                        <S.LikeTop>
                          <S.Avatar
                            $large
                            src={profile.photo}
                            alt={`${profile.nickname} 프로필`}
                          />
                          <S.IconButton
                            type="button"
                            aria-label={`${profile.nickname} 좋아요 취소`}
                            aria-pressed="true"
                            onClick={() => removeLike(profile)}
                          >
                            <Heart
                              size={20}
                              fill="currentColor"
                              aria-hidden="true"
                            />
                          </S.IconButton>
                        </S.LikeTop>
                        <S.LikeName>{profile.nickname}</S.LikeName>
                        <S.LikeMeta>
                          {profile.year}학번 · {profile.major}
                        </S.LikeMeta>
                        <S.LikeMessage>{profile.message}</S.LikeMessage>
                        <S.TagRow>
                          {profile.tags.map((tag) => (
                            <S.Tag key={tag}>#{tag}</S.Tag>
                          ))}
                        </S.TagRow>
                        <S.LikeFooter>
                          <S.SecondaryButton
                            as={Link}
                            to={profileHref(profile)}
                            aria-label={`${profile.nickname} 프로필 보기`}
                          >
                            프로필 보기{" "}
                            <ArrowRight size={16} aria-hidden="true" />
                          </S.SecondaryButton>
                        </S.LikeFooter>
                      </S.LikeCard>
                    ))}
                  </S.LikeGrid>
                )}
              </S.Stack>
            )}
            {activeTab === "account" && (
              <AccountContent account={account} onSave={setAccount} />
            )}
            <S.PreviewPanel>
              <summary>
                <BookOpen size={15} aria-hidden="true" /> 화면 미리보기 설정{" "}
                <span>예시 데이터</span>
              </summary>
              <S.Helper>
                데이터가 있는 화면과 없는 화면을 확인할 수 있어요. 수정한 내용은
                이 페이지를 벗어나면 초기화돼요.
              </S.Helper>
              <S.PreviewOptions>
                {[
                  ["survey", "내 설문"],
                  ["preferences", "선호 룸메 설문"],
                  ["likes", "좋아요"],
                ].map(([id, label]) => (
                  <S.Field key={id}>
                    <S.Label htmlFor={`preview-${id}`}>{label}</S.Label>
                    <S.Select
                      id={`preview-${id}`}
                      value={preview[id]}
                      onChange={(event) => {
                        const value = event.target.value;
                        setPreview((current) => ({ ...current, [id]: value }));
                        if (id === "likes") {
                          setLikeMessage("");
                          lastRemoved.current = null;
                          if (value === "filled")
                            setLikedIds(
                              LIKED_PROFILES.map((profile) => profile.id),
                            );
                        }
                      }}
                    >
                      <option value="filled">
                        {id === "likes" ? "목록 있음" : "작성 완료"}
                      </option>
                      <option value="empty">
                        {id === "likes" ? "목록 없음" : "미작성"}
                      </option>
                    </S.Select>
                  </S.Field>
                ))}
              </S.PreviewOptions>
            </S.PreviewPanel>
          </S.Content>
        </S.Layout>
      </S.Container>
    </S.Page>
  );
}

export default MyPage;
