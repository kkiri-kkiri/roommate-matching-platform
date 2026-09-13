import { useState } from "react";

const IMAGE_DIRECTORY = "/img/hankyung-profile";

// 원본과 같은 파일명의 최대 320px 프로필용 이미지를 사용합니다.
const CHARACTER_FILES = [
  "AI반도체융합전공", "ICT로봇공학", "건축공학", "건축학(5년제)", "경영학",
  "공공행정", "귀금속보석공예", "기계공학", "동물자원과학", "디자인", "모던음악학",
  "문예창작미디어콘텐츠홍보", "법학", "복지상담학", "사회복지학", "상담심리교육",
  "생물산업응용", "소프트웨어융학", "스포츠과학", "시각미디어디자인", "식물생명환경",
  "식품생명공학", "식품영양학", "안전공학", "영미언어문화", "원예생명공학",
  "유아특수보육학", "응용생명공학", "응용수학", "의료재활공학", "의류산업학",
  "전기공학", "전자공학", "정보보안", "제품공간디자인", "조경학", "지역시스템공학",
  "컴퓨터공학", "토목공학", "특수체육학", "한국수어교육학", "행정학", "화학공학", "환경공학",
];

function normalizeMajor(major) {
  return typeof major === "string"
    ? major.normalize("NFC").replace(/\s*·\s*\d{2,4}학번.*$/, "")
      .replace(/\s+/g, "").replace(/전공(?=\(|$)/g, "").replace(/과$/, "")
    : "";
}

const MAJOR_IMAGES = new Map(CHARACTER_FILES.map((name) => [normalizeMajor(name), `${name}.jpg`]));
const MAJOR_ALIASES = {
  소프트웨어융합: "소프트웨어융학",
  "소프트웨어&서비스컴퓨팅": "소프트웨어융학",
  소프트웨어공학: "소프트웨어융학",
  소프트웨어학부: "소프트웨어융학",
  지역자원시스템공학: "지역시스템공학",
  한국수어교육: "한국수어교육학",
  정보보호학: "정보보안",
  시각디자인: "시각미디어디자인",
  디자인학: "디자인",
};

// 이미지가 없던 화면에서도 기존 마이페이지와 같은 기본 아바타를 사용합니다.
const DEFAULT_PROFILE_IMAGE = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160"><rect width="160" height="160" rx="24" fill="#e8eef9"/><circle cx="80" cy="66" r="31" fill="#f2c5a7"/><path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="#363a46"/><path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="#0c3273"/><circle cx="68" cy="67" r="2.4" fill="#3d3532"/><circle cx="92" cy="67" r="2.4" fill="#3d3532"/><path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round"/></svg>',
)}`;

export function getProfileImage(major, fallbackSrc = DEFAULT_PROFILE_IMAGE) {
  const normalized = normalizeMajor(major);
  const key = Object.prototype.hasOwnProperty.call(MAJOR_ALIASES, normalized)
    ? MAJOR_ALIASES[normalized] : normalized;
  const file = MAJOR_IMAGES.get(key);
  return file ? `${process.env.PUBLIC_URL || ""}${IMAGE_DIRECTORY.split("/").map(encodeURIComponent).join("/")}/${encodeURIComponent(file)}`
    : fallbackSrc || DEFAULT_PROFILE_IMAGE;
}

export default function ProfileImage({ as: Image = "img", major, fallbackSrc, alt = "", ...props }) {
  const [failedSources, setFailedSources] = useState(() => new Set());
  const src = [getProfileImage(major, fallbackSrc), fallbackSrc, DEFAULT_PROFILE_IMAGE]
    .find((candidate) => candidate && !failedSources.has(candidate));

  return <Image loading="lazy" decoding="async" {...props} src={src} alt={alt} onError={() => {
    if (src) setFailedSources((current) => new Set([...current, src]));
  }} />;
}
