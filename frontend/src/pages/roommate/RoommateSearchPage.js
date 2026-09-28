import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Check,
  ChevronDown,
  Info,
  RotateCcw,
  SlidersHorizontal,
  UserRoundSearch,
  X,
} from "lucide-react";

import Pagination from "../../components/common/Pagination/Pagination";
import { getRecommendedRoommates, getRoommateDetail } from "../../api/roommateApi";
import ProfileCard from "../../components/common/ProfileCard/ProfileCard";
import * as S from "./RoommateSearchPageCss";
import {
  getSavedSurveyProfiles,
  PROFILE_SECTIONS,
  PROFILE_SURVEYS_STORAGE_KEY,
} from "../mypage/MyPage";

const FILTER_GROUPS = PROFILE_SECTIONS.flatMap((section) => section.fields);

const DEFAULT_FILTERS = FILTER_GROUPS.reduce(
  (filters, group) => ({ ...filters, [group.id]: [] }),
  {},
);
const PAGE_SIZE = 9;

function createProfilePhoto(background, skin, hair, shirt) {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160">
      <rect width="160" height="160" rx="24" fill="${background}" />
      <circle cx="80" cy="66" r="31" fill="${skin}" />
      <path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="${hair}" />
      <path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="${shirt}" />
      <circle cx="68" cy="67" r="2.4" fill="#3d3532" />
      <circle cx="92" cy="67" r="2.4" fill="#3d3532" />
      <path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round" />
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export const PROFILES = [
  {
    id: 1,
    nickname: "포근한구름",
    admissionYear: "24",
    major: "소프트웨어융합전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 94,
    message: "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
    tags: ["비흡연", "아침형", "주 1회 청소"],
    photo: createProfilePhoto("#dff6f2", "#f2c5a7", "#363a46", "#00a79f"),
  },
  {
    id: 2,
    nickname: "새벽별",
    admissionYear: "23",
    major: "경영학전공",
    dormitory: "호연",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 88,
    message: "늦게 자는 편이지만 방에서는 늘 이어폰을 사용해요.",
    tags: ["이어폰 사용", "깔끔해요", "개인 시간"],
    photo: createProfilePhoto("#e6edff", "#eab996", "#2c2625", "#3478f6"),
  },
  {
    id: 3,
    nickname: "민트초코",
    admissionYear: "25",
    major: "시각미디어디자인전공",
    dormitory: "비봉",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 82,
    message: "같이 밥도 먹고 소소한 이야기를 나누는 룸메를 찾아요.",
    tags: ["향 제품 안 써요", "중간형", "같이 청소"],
    photo: createProfilePhoto("#fff0e1", "#f4c7a4", "#71452f", "#ff9f4a"),
  },
  {
    id: 4,
    nickname: "정리왕",
    admissionYear: "22",
    major: "건축공학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "일반담배",
    interaction: "적당히",
    matchScore: 76,
    message: "공용 공간은 깔끔하게, 생활 규칙은 대화로 맞춰가요.",
    tags: ["일반담배", "매일 정리", "일찍 취침"],
    photo: createProfilePhoto("#efe8ff", "#dca77f", "#33251f", "#7657d6"),
  },
  {
    id: 5,
    nickname: "느긋한오후",
    admissionYear: "24",
    major: "식품영양학전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 69,
    message: "조용하고 여유로운 분위기에서 각자의 루틴을 지켜요.",
    tags: ["비흡연", "조용한 방", "주말 외출"],
    photo: createProfilePhoto("#fce8ed", "#f0bd9d", "#4c3028", "#d8607a"),
  },
  {
    id: 6,
    nickname: "한걸음",
    admissionYear: "25",
    major: "전자공학전공",
    dormitory: "호연",
    rhythm: "야행성",
    smoking: "전자담배",
    interaction: "친하게",
    matchScore: 63,
    message: "서로 배려할 부분을 먼저 이야기하는 룸메이트가 되고 싶어요.",
    tags: ["전자담배", "야행성", "대화 환영"],
    photo: createProfilePhoto("#e7f2ff", "#dba77f", "#252f3a", "#456fa8"),
  },
  {
    id: 7,
    nickname: "조용한숲",
    admissionYear: "21",
    major: "조경학전공",
    dormitory: "비봉",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 57,
    message: "말수는 적지만 필요한 일은 바로바로 소통하는 편이에요.",
    tags: ["아침형", "환기 자주", "각자 청소"],
    photo: createProfilePhoto("#e8f3e7", "#efc09e", "#3c312c", "#5d8e62"),
  },
  {
    id: 8,
    nickname: "따뜻한차",
    admissionYear: "23",
    major: "상담심리교육전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "일반담배",
    interaction: "친하게",
    matchScore: 54,
    message: "작은 불편도 쌓아두지 않고 편하게 이야기했으면 해요.",
    tags: ["일반담배", "대화 좋아요", "같이 식사"],
    photo: createProfilePhoto("#fff4d8", "#e8b18c", "#4d3429", "#d9a52e"),
  },
  {
    id: 9,
    nickname: "리드미",
    admissionYear: "24",
    major: "정보보호학전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 91,
    message: "시간 관리가 잘 되는 편이라 서로의 생활 루틴을 지키는 데 익숙해요.",
    tags: ["비흡연", "아침형", "정리정돈 잘함"],
    photo: createProfilePhoto("#edf8ff", "#efc09e", "#25313d", "#5ca7ff"),
  },
  {
    id: 10,
    nickname: "코지마루",
    admissionYear: "22",
    major: "경제학전공",
    dormitory: "호연",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 87,
    message: "가벼운 대화와 서로의 취미를 존중하는 분위기를 좋아해요.",
    tags: ["비흡연", "대화 좋아요", "룸메 소통"],
    photo: createProfilePhoto("#f2f9f4", "#f1c29b", "#2f2c2b", "#6bb36d"),
  },
  {
    id: 11,
    nickname: "밤새미",
    admissionYear: "26",
    major: "소프트웨어학부",
    dormitory: "비봉",
    rhythm: "야행성",
    smoking: "전자담배",
    interaction: "적당히",
    matchScore: 81,
    message: "낮에는 공부하고 밤에는 조용히 쉬는 편이라 적당한 조율이 잘 맞아요.",
    tags: ["전자담배", "야행성", "이어폰 필수"],
    photo: createProfilePhoto("#eef5ff", "#dca77f", "#322c3d", "#7b8fe0"),
  },
  {
    id: 12,
    nickname: "달빛인형",
    admissionYear: "23",
    major: "도시공학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 72,
    message: "조용한 환경을 선호하지만 필요한 순간에는 자연스럽게 소통하는 편이에요.",
    tags: ["비흡연", "조용한 분위기", "방 정리"],
    photo: createProfilePhoto("#fff9ef", "#f0c4a5", "#3f352f", "#ffbf69"),
  },
  {
    id: 13,
    nickname: "초록머리핀",
    admissionYear: "25",
    major: "환경공학전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 80,
    message: "책상 주변을 정리하는 데 신경 쓰고, 방도 서로 편하게 사용할 수 있게 맞춰가고 싶어요.",
    tags: ["비흡연", "중간형", "깔끔함"],
    photo: createProfilePhoto("#eefaf6", "#e9b98e", "#2f3b36", "#66bf9a"),
  },
  {
    id: 14,
    nickname: "코튼바람",
    admissionYear: "24",
    major: "수학과",
    dormitory: "호연",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 78,
    message: "말은 적지만 서로에게 필요한 부분은 바로 알려주는 편이라 안정감을 느껴요.",
    tags: ["비흡연", "아침형", "조용한 생활"],
    photo: createProfilePhoto("#f4f4ff", "#f0bf9b", "#3a2f3d", "#7b7ee6"),
  },
  {
    id: 15,
    nickname: "감성라떼",
    admissionYear: "22",
    major: "경찰행정학전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "일반담배",
    interaction: "친하게",
    matchScore: 74,
    message: "여유로운 하루와 규칙적인 생활을 함께 맞추는 걸 좋아해요.",
    tags: ["일반담배", "대화 좋아요", "감성적"],
    photo: createProfilePhoto("#fff2eb", "#efc3a4", "#362b2a", "#d6825c"),
  },
  {
    id: 16,
    nickname: "소나기꽃",
    admissionYear: "26",
    major: "심리학전공",
    dormitory: "비봉",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 86,
    message: "조용한 밤시간을 중요하게 생각해서, 서로의 공간을 존중하는 룸메가 되고 싶어요.",
    tags: ["비흡연", "야행성", "혼자 시간"],
    photo: createProfilePhoto("#f3f8ff", "#efc29a", "#2b3039", "#5a8fe9"),
  },
  {
    id: 17,
    nickname: "리프하트",
    admissionYear: "23",
    major: "생명과학전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 83,
    message: "자연스럽게 정보를 공유하고, 생활 습관도 함께 정리하는 편이에요.",
    tags: ["비흡연", "아침형", "잘 챙김"],
    photo: createProfilePhoto("#edf9f0", "#f1c39b", "#3b312e", "#73ae72"),
  },
  {
    id: 18,
    nickname: "모래시계",
    admissionYear: "21",
    major: "법학전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 77,
    message: "생활 패턴이 안정적이라서 서로의 루틴을 잘 맞추는 편이에요.",
    tags: ["비흡연", "정리 잘함", "중간형"],
    photo: createProfilePhoto("#fff5e7", "#eab58b", "#45372f", "#dca15c"),
  },
  {
    id: 19,
    nickname: "은빛바람",
    admissionYear: "25",
    major: "화학공학전공",
    dormitory: "호연",
    rhythm: "중간형",
    smoking: "전자담배",
    interaction: "친하게",
    matchScore: 89,
    message: "실험과 공부 일정이 겹치더라도 조용한 협업 스타일을 선호해요.",
    tags: ["전자담배", "중간형", "소통 원활"],
    photo: createProfilePhoto("#eef7ff", "#e7b28f", "#2d2b30", "#7aa5da"),
  },
  {
    id: 20,
    nickname: "맑은하늘",
    admissionYear: "24",
    major: "국어국문학전공",
    dormitory: "비봉",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 70,
    message: "외향적이진 않지만 필요한 대화는 꼭 하고, 방 안은 조용하게 유지하고 싶어요.",
    tags: ["비흡연", "아침형", "조용함"],
    photo: createProfilePhoto("#eaf7ff", "#ebc3a2", "#334856", "#62b7d9"),
  },
  {
    id: 21,
    nickname: "신선한환기",
    admissionYear: "26",
    major: "기계공학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 84,
    message: "규칙적인 시간표와 쾌적한 공간을 함께 만들고 싶어요.",
    tags: ["비흡연", "아침형", "환기 자주"],
    photo: createProfilePhoto("#ebfaf6", "#f0c29a", "#2f3630", "#68b392"),
  },
  {
    id: 22,
    nickname: "라이트체인지",
    admissionYear: "23",
    major: "산업디자인전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 92,
    message: "예민하지 않고 각자의 스타일을 존중하면서도 편하게 소통하는 편이에요.",
    tags: ["비흡연", "중간형", "대화 환영"],
    photo: createProfilePhoto("#f4f0ff", "#efc09d", "#342b40", "#8d7ce5"),
  },
  {
    id: 23,
    nickname: "솔방울",
    admissionYear: "24",
    major: "산업경영학전공",
    dormitory: "호연",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 75,
    message: "여유롭게 쉬는 시간도 중요해서, 서로를 방해하지 않는 배려를 좋아해요.",
    tags: ["비흡연", "야행성", "조용히"],
    photo: createProfilePhoto("#eef9f1", "#efc39a", "#2c372e", "#6abf8f"),
  },
  {
    id: 24,
    nickname: "다정한나무",
    admissionYear: "22",
    major: "미디어커뮤니케이션전공",
    dormitory: "비봉",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 88,
    message: "서로의 취미와 감정을 존중하는 분위기에서 편하게 지내고 싶어요.",
    tags: ["비흡연", "중간형", "친근함"],
    photo: createProfilePhoto("#f3f8ee", "#e9ba8d", "#313225", "#91ba64"),
  },
  {
    id: 25,
    nickname: "포카리스웨트",
    admissionYear: "21",
    major: "스포츠과학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 95,
    message: "운동 루틴도 있고 생활 리듬도 규칙적이라서, 같이 관리하는 느낌이 좋아요.",
    tags: ["비흡연", "아침형", "규칙적"],
    photo: createProfilePhoto("#fef6ee", "#e9b391", "#2f322d", "#f3af55"),
  },
  {
    id: 26,
    nickname: "트리플초코",
    admissionYear: "23",
    major: "디지털미디어전공",
    dormitory: "호연",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 68,
    message: "개인 공간을 소중히 여기면서도 필요한 순간에는 편하게 이야기할 수 있으면 좋겠어요.",
    tags: ["비흡연", "적당히", "방 정리"],
    photo: createProfilePhoto("#fdf4ff", "#efc39d", "#332d3d", "#d687cf"),
  },
  {
    id: 27,
    nickname: "라이트달",
    admissionYear: "24",
    major: "전자통신전공",
    dormitory: "나래",
    rhythm: "야행성",
    smoking: "전자담배",
    interaction: "친하게",
    matchScore: 71,
    message: "밤에는 조용하게 쉬고 낮에는 서로 배려하는 분위기라면 잘 맞을 것 같아요.",
    tags: ["전자담배", "야행성", "배려형"],
    photo: createProfilePhoto("#edf7ff", "#f0bd9d", "#2e3946", "#77a5d8"),
  },
  {
    id: 28,
    nickname: "비온뒤햇살",
    admissionYear: "23",
    major: "통계학전공",
    dormitory: "호연",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 76,
    message: "서로의 생활 패턴을 지켜가며 안정적인 분위기를 만드는 걸 좋아해요.",
    tags: ["비흡연", "중간형", "규칙적"],
    photo: createProfilePhoto("#edf8f4", "#efc2a1", "#2f3937", "#68b39a"),
  },
  {
    id: 29,
    nickname: "마시멜로우",
    admissionYear: "22",
    major: "국제통상학전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 85,
    message: "가벼운 대화와 분리된 공간을 모두 중요하게 생각해요.",
    tags: ["비흡연", "아침형", "대화 좋아요"],
    photo: createProfilePhoto("#f5f2ff", "#eec2a1", "#342733", "#b28ae8"),
  },
  {
    id: 30,
    nickname: "새벽보리",
    admissionYear: "26",
    major: "전자전기공학전공",
    dormitory: "비봉",
    rhythm: "야행성",
    smoking: "전자담배",
    interaction: "개인 시간",
    matchScore: 70,
    message: "밤에 조용히 쉬고 낮에는 각자 공부를 하며 루틴을 지키는 편이에요.",
    tags: ["전자담배", "야행성", "개인 시간"],
    photo: createProfilePhoto("#edf7ff", "#e9b78d", "#2f3449", "#6f95d8"),
  },
  {
    id: 31,
    nickname: "오로라지기",
    admissionYear: "24",
    major: "중어중문학전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 90,
    message: "대화는 자연스럽게 나누고, 서로의 일정을 배려하는 분위기를 좋아해요.",
    tags: ["비흡연", "중간형", "배려형"],
    photo: createProfilePhoto("#eef9ff", "#edc3a3", "#2a3039", "#76b7d9"),
  },
  {
    id: 32,
    nickname: "스카이브리즈",
    admissionYear: "25",
    major: "화학전공",
    dormitory: "호연",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 79,
    message: "생활 규칙을 미리 정해두면 훨씬 편하게 생활할 수 있어요.",
    tags: ["비흡연", "아침형", "생활 규칙"],
    photo: createProfilePhoto("#edf9f5", "#efc39d", "#2f3532", "#74a293"),
  },
  {
    id: 33,
    nickname: "라벤더휴식",
    admissionYear: "21",
    major: "식물자원학전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 84,
    message: "조용하게 지내되, 필요한 순간에는 서로 편하게 이야기할 수 있으면 좋아요.",
    tags: ["비흡연", "친근함", "정리 잘함"],
    photo: createProfilePhoto("#f2efff", "#ebc7a4", "#372d3e", "#9b87d9"),
  },
  {
    id: 34,
    nickname: "강한마루",
    admissionYear: "23",
    major: "철학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "일반담배",
    interaction: "적당히",
    matchScore: 66,
    message: "생활 스타일을 잘 맞추면 서로 편하게 지낼 수 있을 것 같아요.",
    tags: ["일반담배", "아침형", "정리배려"],
    photo: createProfilePhoto("#f4efe8", "#d7a27d", "#3c2d29", "#cb8d5d"),
  },
  {
    id: 35,
    nickname: "모던베리",
    admissionYear: "24",
    major: "신소재공학전공",
    dormitory: "비봉",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 88,
    message: "공부와 휴식 시간을 서로 존중하며 편안하게 생활하고 싶어요.",
    tags: ["비흡연", "아침형", "깔끔함"],
    photo: createProfilePhoto("#f7f4ff", "#f0bf9e", "#342f42", "#8f7de0"),
  },
  {
    id: 36,
    nickname: "숲속책상",
    admissionYear: "22",
    major: "생태환경공학전공",
    dormitory: "호연",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 73,
    message: "개인 시간이 중요하지만, 필요한 순간에는 충분히 소통하는 편이에요.",
    tags: ["비흡연", "개인 시간", "조용함"],
    photo: createProfilePhoto("#edf7ef", "#eabf8b", "#2f382d", "#7bb976"),
  },
  {
    id: 37,
    nickname: "달콤한벽",
    admissionYear: "25",
    major: "디자인학전공",
    dormitory: "나래",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 82,
    message: "서로의 취향을 존중하는 분위기라면 편하게 생활할 수 있을 것 같아요.",
    tags: ["비흡연", "야행성", "취향 존중"],
    photo: createProfilePhoto("#fff4f8", "#f0bd9f", "#382f39", "#d688a8"),
  },
  {
    id: 38,
    nickname: "티끌모음",
    admissionYear: "23",
    major: "경제통계학전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "전자담배",
    interaction: "친하게",
    matchScore: 92,
    message: "생활 습관을 잘 맞춰서 서로 부담 없이 지낼 수 있을 것 같아요.",
    tags: ["전자담배", "중간형", "생활 습관"],
    photo: createProfilePhoto("#f4f8ff", "#e6b18b", "#2e3643", "#7a96d0"),
  },
  {
    id: 39,
    nickname: "새벽청소",
    admissionYear: "24",
    major: "토목공학전공",
    dormitory: "비봉",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 77,
    message: "규칙적인 생활과 깔끔한 공간을 좋아하는 편이라 잘 맞을 것 같아요.",
    tags: ["비흡연", "아침형", "청결"],
    photo: createProfilePhoto("#ecf8f5", "#ebc3a0", "#2d342f", "#6fc0aa"),
  },
  {
    id: 40,
    nickname: "하양바람",
    admissionYear: "26",
    major: "미디어아트전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 81,
    message: "서로 말을 많이 하지 않아도 자연스럽게 맞춰가는 것이 편해요.",
    tags: ["비흡연", "중간형", "편한 분위기"],
    photo: createProfilePhoto("#f4f8ff", "#f0c39e", "#303541", "#7ea4d7"),
  },
  {
    id: 41,
    nickname: "멜로디풀",
    admissionYear: "22",
    major: "철학·심리학복합전공",
    dormitory: "창조",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 74,
    message: "각자 개인 시간을 존중하면서도 필요한 대화는 자연스럽게 나누고 싶어요.",
    tags: ["비흡연", "야행성", "배려"],
    photo: createProfilePhoto("#f8f3ff", "#edc2a2", "#392f3d", "#b88fe8"),
  },
  {
    id: 42,
    nickname: "코알라루틴",
    admissionYear: "25",
    major: "수학교육전공",
    dormitory: "호연",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 86,
    message: "생활 루틴을 정해두면 서로 편하고 안정적인 분위기를 만들 수 있어요.",
    tags: ["비흡연", "아침형", "안정적"],
    photo: createProfilePhoto("#f0f9f3", "#efc4a2", "#2c382f", "#73b789"),
  },
  {
    id: 43,
    nickname: "뷰티오후",
    admissionYear: "24",
    major: "패션디자인전공",
    dormitory: "비봉",
    rhythm: "중간형",
    smoking: "일반담배",
    interaction: "친하게",
    matchScore: 69,
    message: "사소한 습관도 자연스럽게 말할 수 있는 룸메를 찾고 있어요.",
    tags: ["일반담배", "중간형", "소통"],
    photo: createProfilePhoto("#fff4f0", "#e9b791", "#3a2c29", "#d66558"),
  },
  {
    id: 44,
    nickname: "루미너스",
    admissionYear: "23",
    major: "소프트웨어공학전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 93,
    message: "서로의 시간을 존중하면서도 필요한 안내는 자연스럽게 주고받고 싶어요.",
    tags: ["비흡연", "아침형", "배려"],
    photo: createProfilePhoto("#edf9ff", "#eec9a7", "#2a313e", "#6fa9de"),
  },
  {
    id: 45,
    nickname: "민트웨이브",
    admissionYear: "26",
    major: "환경학과",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 80,
    message: "서로의 생활 루틴을 토대로 가장 편안한 조화를 찾고 싶어요.",
    tags: ["비흡연", "중간형", "청결"],
    photo: createProfilePhoto("#eefaf6", "#edc5a4", "#2a3633", "#6ec3a1"),
  },
];

function RoommateSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") === "recommended" ? "recommended" : "all";
  const setActiveTab = (tab) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (tab === "recommended") next.set("tab", "recommended");
      else next.delete("tab");
      return next;
    }, { replace: true });
  };
  const [selectedTag, setSelectedTag] = useState(null);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [draftFilters, setDraftFilters] = useState(DEFAULT_FILTERS);
  const [expandedCategories, setExpandedCategories] = useState({
    lifePattern: true,
    livingHabits: false,
    dormLife: false,
  });
  const [savedProfiles, setSavedProfiles] = useState(getSavedSurveyProfiles);
  const [recommendations, setRecommendations] = useState({ status: "loading", profiles: [], error: "" });
  useEffect(() => {
    if (activeTab !== "recommended") return;
    const controller = new AbortController();
    setRecommendations({ status: "loading", profiles: [], error: "" });
    (async () => {
      try {
        const page = await getRecommendedRoommates(controller.signal);
        if (!Array.isArray(page?.content)) throw new Error("추천 목록 응답을 확인할 수 없습니다.");
        const profiles = await Promise.all(page.content.map(async (item) => {
          if (!Number.isSafeInteger(item.id) || item.id <= 0) throw new Error("추천 사용자 정보를 확인할 수 없습니다.");
          const detail = await getRoommateDetail(item.id, controller.signal);
          if (detail?.id !== item.id) throw new Error("추천 사용자 정보를 확인할 수 없습니다.");
          return { ...item, userId: item.id, source: "backend", answers: detail.answers || {},
            message: item.introduction, photo: item.profileImageUrl };
        }));
        if (!controller.signal.aborted) setRecommendations({ status: "success", profiles, error: "" });
      } catch (error) {
        if (!controller.signal.aborted) setRecommendations({ status: "error", profiles: [], error: error.message });
      }
    })();
    return () => controller.abort();
  }, [activeTab]);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const tabRefs = useRef({});
  const filterDialogRef = useRef(null);

  useEffect(() => {
    const refreshProfiles = (event) => {
      if (event.key === null || event.key === PROFILE_SURVEYS_STORAGE_KEY) {
        setSavedProfiles(getSavedSurveyProfiles());
      }
    };
    window.addEventListener("storage", refreshProfiles);
    return () => window.removeEventListener("storage", refreshProfiles);
  }, []);

  useEffect(() => {
    if (!isFilterOpen) return;

    const dialog = filterDialogRef.current;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";

    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
    };
  }, [isFilterOpen]);

  const activeFilterEntries = useMemo(
    () =>
      FILTER_GROUPS.flatMap((group) =>
        filters[group.id].map((value) => ({ id: group.id, label: group.label, value })),
      ),
    [filters],
  );

  const filteredProfiles = useMemo(() => {
    const profiles = [
      ...PROFILES,
      ...savedProfiles.map(({ id, profile, answers }) => ({
        ...profile,
        id,
        answers,
        photo: createProfilePhoto("#e8eef9", "#f2c5a7", "#363a46", "#0c3273"),
        tags: [answers.smoking, answers.sleepTime, answers.cleaningFrequency].filter(Boolean),
      })),
    ];
    const profilesForTab = activeTab === "recommended" ? recommendations.profiles : profiles;

    return profilesForTab.filter((profile) =>
      (!selectedTag || profile.tags.includes(selectedTag)) &&
      FILTER_GROUPS.every((group) => {
        const selectedValues = filters[group.id];
        if (selectedValues.length === 0) return true;
        const answer = profile.answers?.[group.id] ?? profile[group.id];
        const answerValues = Array.isArray(answer) ? answer : [answer];
        return selectedValues.some((value) => answerValues.includes(value));
      }),
    );
  }, [activeTab, filters, savedProfiles, selectedTag, recommendations.profiles]);

  const totalPages = Math.max(1, Math.ceil(filteredProfiles.length / PAGE_SIZE));

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, selectedTag, filters, savedProfiles]);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const visibleProfiles = useMemo(
    () => filteredProfiles.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE),
    [currentPage, filteredProfiles],
  );

  const updateDraftFilter = (filterId, value) => {
    setDraftFilters((currentFilters) => ({
      ...currentFilters,
      [filterId]: currentFilters[filterId].includes(value)
        ? currentFilters[filterId].filter((selected) => selected !== value)
        : [...currentFilters[filterId], value],
    }));
  };

  const openFilters = () => {
    setDraftFilters(
      Object.fromEntries(Object.entries(filters).map(([id, values]) => [id, [...values]])),
    );
    setIsFilterOpen(true);
  };

  const applyFilters = () => {
    setFilters(draftFilters);
    setIsFilterOpen(false);
  };

  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setSelectedTag(null);
  };

  const removeFilter = (filterId, value) => {
    setFilters((currentFilters) => ({
      ...currentFilters,
      [filterId]: currentFilters[filterId].filter((selected) => selected !== value),
    }));
  };

  const handleTabKeyDown = (event, currentTab) => {
    let nextTab;

    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      nextTab = currentTab === "all" ? "recommended" : "all";
    } else if (event.key === "Home") {
      nextTab = "all";
    } else if (event.key === "End") {
      nextTab = "recommended";
    } else {
      return;
    }

    event.preventDefault();
    setActiveTab(nextTab);
    tabRefs.current[nextTab]?.focus();
  };

  const handlePageChange = (nextPage) => {
    setCurrentPage(nextPage);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <S.Page>
      <S.PageReset />

      <S.Content>
        <S.DiscoveryControls>
          <S.TabsRow>
            <S.TabsArea>
              <S.TabList role="tablist" aria-label="프로필 목록 구분">
                <S.TabButton
                  ref={(button) => {
                    tabRefs.current.all = button;
                  }}
                  id="all-profile-tab"
                  type="button"
                  role="tab"
                  $active={activeTab === "all"}
                  aria-selected={activeTab === "all"}
                  aria-controls="roommate-profile-panel"
                  tabIndex={activeTab === "all" ? 0 : -1}
                  onClick={() => setActiveTab("all")}
                  onKeyDown={(event) => handleTabKeyDown(event, "all")}
                >
                  전체 프로필
                </S.TabButton>

                <S.TabButton
                  ref={(button) => {
                    tabRefs.current.recommended = button;
                  }}
                  id="recommended-profile-tab"
                  type="button"
                  role="tab"
                  $active={activeTab === "recommended"}
                  aria-selected={activeTab === "recommended"}
                  aria-controls="roommate-profile-panel"
                  aria-label="추천 프로필"
                  aria-describedby="recommendation-tooltip"
                  tabIndex={activeTab === "recommended" ? 0 : -1}
                  onClick={() => setActiveTab("recommended")}
                  onKeyDown={(event) =>
                    handleTabKeyDown(event, "recommended")
                  }
                >
                  추천 프로필
                  <S.TooltipHost>
                    <S.TooltipIcon aria-hidden="true">
                      <Info size={17} />
                    </S.TooltipIcon>
                    <S.Tooltip id="recommendation-tooltip" role="tooltip">
                      매칭 점수 상위 9명
                    </S.Tooltip>
                  </S.TooltipHost>
                </S.TabButton>
              </S.TabList>
            </S.TabsArea>

          </S.TabsRow>

          <S.FilterStatus aria-live="polite">
            <S.FilterStatusContent>
              <S.FilterToggle
                type="button"
                aria-label="필터 열기"
                title="필터 열기"
                aria-haspopup="dialog"
                aria-expanded={isFilterOpen}
                aria-controls="roommate-filter-panel"
                onClick={openFilters}
              >
                <SlidersHorizontal size={18} aria-hidden="true" />
              </S.FilterToggle>

              {activeFilterEntries.length > 0 ? (
                <S.AppliedFilterList>
                  {activeFilterEntries.map((filter) => (
                    <S.AppliedFilter key={`${filter.id}-${filter.value}`}>
                      <span>
                        {filter.label} · {filter.value}
                      </span>
                      <button
                        type="button"
                        aria-label={`${filter.label} ${filter.value} 필터 취소`}
                        onClick={() => removeFilter(filter.id, filter.value)}
                      >
                        <X size={13} aria-hidden="true" />
                      </button>
                    </S.AppliedFilter>
                  ))}
                </S.AppliedFilterList>
              ) : (
                <S.NoFilterText>현재 적용된 필터가 없어요.</S.NoFilterText>
              )}
            </S.FilterStatusContent>

            <S.ResetFilterButton
              type="button"
              aria-label="필터 초기화"
              title="필터 초기화"
              disabled={activeFilterEntries.length === 0 && !selectedTag}
              onClick={resetFilters}
            >
              <RotateCcw size={15} aria-hidden="true" />
            </S.ResetFilterButton>
          </S.FilterStatus>
        </S.DiscoveryControls>

        <S.SearchLayout>
          <S.FilterPanel
            ref={filterDialogRef}
            id="roommate-filter-panel"
            aria-labelledby="roommate-filter-title"
            onCancel={(event) => {
              event.preventDefault();
              setIsFilterOpen(false);
            }}
            onClick={(event) => {
              if (event.target !== event.currentTarget) return;
              const bounds = event.currentTarget.getBoundingClientRect();
              if (
                event.clientX < bounds.left ||
                event.clientX > bounds.right ||
                event.clientY < bounds.top ||
                event.clientY > bounds.bottom
              ) {
                setIsFilterOpen(false);
              }
            }}
          >
            <S.FilterPanelHeader>
              <div>
                <S.FilterTitle id="roommate-filter-title">
                  <SlidersHorizontal size={18} aria-hidden="true" />
                  필터
                </S.FilterTitle>
                <S.FilterDescription>
                  필요한 항목만 복수 선택하세요. 전체는 조건 미적용이에요.
                </S.FilterDescription>
              </div>

              <S.FilterToggle
                type="button"
                aria-label="필터 닫기"
                onClick={() => setIsFilterOpen(false)}
              >
                <X size={18} aria-hidden="true" />
              </S.FilterToggle>
            </S.FilterPanelHeader>

            <S.FilterGroups>
              {PROFILE_SECTIONS.map((section) => {
                const expanded = expandedCategories[section.id];

                return (
                  <S.FilterCategory key={section.id}>
                    <S.FilterCategoryHeading>
                      <S.FilterCategoryToggle
                        type="button"
                        $expanded={expanded}
                        aria-expanded={expanded}
                        aria-controls={`filter-category-${section.id}`}
                        onClick={() => setExpandedCategories((current) => ({
                          ...current,
                          [section.id]: !current[section.id],
                        }))}
                      >
                        <span>{section.title}</span>
                        <small>{section.fields.length}개 항목</small>
                        <ChevronDown size={18} aria-hidden="true" />
                      </S.FilterCategoryToggle>
                    </S.FilterCategoryHeading>
                    <div id={`filter-category-${section.id}`} hidden={!expanded}>
                      {section.fields.map((group) => (
                        <S.FilterFieldset key={group.id}>
                          <S.FilterLegend>{group.label}</S.FilterLegend>
                          <S.FilterChoices>
                            {group.options.map((option) => {
                              if (
                                ["lightingPreference", "phoneCall", "scentProductUse"].includes(group.id) &&
                                option.replace(/\s/g, "") === "상관없음"
                              ) {
                                return null;
                              }

                              const selected = draftFilters[group.id].includes(option);

                              return (
                                <S.FilterChoice key={option} $selected={selected}>
                                  <S.HiddenInput
                                    type="checkbox"
                                    name={group.id}
                                    value={option}
                                    checked={selected}
                                    onChange={() => updateDraftFilter(group.id, option)}
                                  />
                                  <S.ChoiceIndicator $selected={selected}>
                                    {selected && <Check size={12} aria-hidden="true" />}
                                  </S.ChoiceIndicator>
                                  <span>{option}</span>
                                </S.FilterChoice>
                              );
                            })}
                          </S.FilterChoices>
                        </S.FilterFieldset>
                      ))}
                    </div>
                  </S.FilterCategory>
                );
              })}
            </S.FilterGroups>
            <S.FilterModalFooter>
              <S.ResetFilterButton type="button" onClick={() => setDraftFilters(DEFAULT_FILTERS)}>
                <RotateCcw size={15} aria-hidden="true" />
                초기화
              </S.ResetFilterButton>
              <S.FilterToggle type="button" onClick={applyFilters}>
                적용
              </S.FilterToggle>
            </S.FilterModalFooter>
          </S.FilterPanel>

          <S.ProfileResults
            id="roommate-profile-panel"
            role="tabpanel"
            tabIndex={0}
            aria-labelledby={
              activeTab === "recommended"
                ? "recommended-profile-tab"
                : "all-profile-tab"
            }
          >
            {activeTab === "recommended" && recommendations.status !== "success" ? (
              <S.EmptyState role={recommendations.status === "error" ? "alert" : "status"}>
                <h3>{recommendations.status === "loading" ? "추천 프로필을 불러오고 있어요" : "추천 프로필을 불러오지 못했어요"}</h3>
                <p>{recommendations.error || "잠시만 기다려 주세요."}</p>
              </S.EmptyState>
            ) : visibleProfiles.length > 0 ? (
              <>
                <S.ProfileGrid>
                  {visibleProfiles.map((profile) => (
                    <ProfileCard
                      key={profile.id}
                      profile={profile}
                      activeTab={activeTab}
                      showOverlay={activeTab === "recommended"}
                    />
                  ))}
                </S.ProfileGrid>

                <Pagination
                  currentPage={currentPage}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                />
              </>
            ) : (
              <S.EmptyState>
                <S.EmptyIcon>
                  <UserRoundSearch size={30} aria-hidden="true" />
                </S.EmptyIcon>
                <h3>{activeTab === "recommended" ? "조건에 맞는 추천 프로필이 없어요" : "조건에 맞는 프로필이 없어요"}</h3>
                <p>필터를 조금 넓히면 더 많은 룸메이트를 만날 수 있어요.</p>
                <S.EmptyResetButton type="button" onClick={resetFilters}>
                  <RotateCcw size={16} aria-hidden="true" />
                  필터 모두 해제
                </S.EmptyResetButton>
              </S.EmptyState>
            )}
          </S.ProfileResults>
        </S.SearchLayout>
      </S.Content>
    </S.Page>
  );
}

export default RoommateSearchPage;
