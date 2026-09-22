import styled, { css } from "styled-components";

export const CATEGORY_STYLES = {
  school: { color: "#245fc5", background: "#edf3ff" },
  dormitory: { color: "#006a65", background: "#e6f7f3" },
  roommate: { color: "#7550ae", background: "#f2edfc" },
  personal: { color: "#906016", background: "#fff5df" },
};

const categoryStyle = (category) => {
  const id = category || "personal";
  const fallback = CATEGORY_STYLES[id] || CATEGORY_STYLES.personal;
  return {
    color: `var(--category-${id}-color, ${fallback.color})`,
    background: `var(--category-${id}-background, ${fallback.background})`,
    text: `var(--category-${id}-text, ${fallback.color})`,
  };
};
const focus = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.45);
    outline-offset: 3px;
  }
`;
const button = css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid transparent;
  border-radius: 12px;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-decoration: none;
  cursor: pointer;
  transition:
    background 160ms,
    border-color 160ms,
    box-shadow 160ms;
  &:disabled {
    opacity: 0.45;
    cursor: default;
  }
  ${focus}
`;
const card = css`
  min-width: 0;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;
const input = css`
  display: block;
  width: 100%;
  min-width: 0;
  min-height: 46px;
  padding: 11px 13px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: #ffffff;
  color: #1a1c1c;
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  transition:
    border-color 160ms,
    box-shadow 160ms;
  &::placeholder {
    color: #747781;
  }
  &:focus {
    outline: none;
    border-color: #00a79f;
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12);
  }
  &[aria-invalid="true"] {
    border-color: #ba1a1a;
  }
  @media (max-width: 480px) {
    font-size: 16px;
  }
`;

export const Page = styled.main`
  min-height: 100vh;
  padding: 40px 20px 80px;
  background: #faf9f6;
  color: #1a1c1c;
  font-family:
    "Plus Jakarta Sans",
    Pretendard,
    "Noto Sans KR",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
  line-height: 1.5;
  &,
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }
  button,
  input,
  select,
  textarea {
    font-family: inherit;
  }
  button,
  a,
  input,
  select,
  textarea {
    -webkit-tap-highlight-color: transparent;
  }
  svg {
    flex-shrink: 0;
  }
  @media (max-width: 700px) {
    padding: 24px 16px 48px;
  }
  @media (prefers-reduced-motion: reduce) {
    *,
    *::before,
    *::after {
      transition: none !important;
      animation: none !important;
      scroll-behavior: auto !important;
    }
  }
`;
export const Content = styled.div`
  width: min(100%, 1200px);
  min-width: 0;
  margin: 0 auto;
`;
export const Eyebrow = styled.p`
  margin: 0 0 10px;
  color: #006a65;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
`;
export const PrimaryButton = styled.button`
  ${button}
  background: #0c3273;
  color: #ffffff;
  &:hover:not(:disabled) {
    background: #001d4f;
    box-shadow: 0 4px 12px rgba(12, 50, 115, 0.12);
  }
`;
export const SecondaryButton = styled.button`
  ${button}
  border: 1.5px solid #00a79f;
  background: #ffffff;
  color: #006a65;
  &:hover:not(:disabled) {
    background: #e6f7f3;
  }
`;
export const GhostButton = styled.button`
  ${button}
  background: transparent;
  color: #5e616b;
  &:hover:not(:disabled) {
    background: #f3f3f3;
    color: #0c3273;
  }
`;
export const IconButton = styled(GhostButton)`
  flex-shrink: 0;
  width: 44px;
  padding: 10px;
`;
export const Workspace = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
  gap: 24px;
  @media (max-width: 1050px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;
export const CalendarCard = styled.section`
  ${card}
`;
export const Toolbar = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 24px 24px 12px;
  > button {
    grid-column: 3;
    justify-self: end;
  }
  @media (max-width: 600px) {
    padding: 16px 12px 8px;
    gap: 8px;
  }
  @media (max-width: 400px) {
    grid-template-columns: minmax(0, 1fr);
    > button {
      grid-column: 1;
    }
  }
`;
export const MonthNavigation = styled.div`
  grid-column: 2;
  justify-self: center;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  @media (max-width: 380px) {
    gap: 0;
  }
  @media (max-width: 400px) {
    grid-column: 1;
  }
`;
export const MonthTitle = styled.h2`
  min-width: 142px;
  margin: 0 8px;
  color: #001d4f;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
  text-align: center;
  white-space: nowrap;
  @media (max-width: 600px) {
    min-width: 120px;
    margin: 0;
    font-size: 18px;
  }
`;
export const Filters = styled.fieldset`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  margin: 0;
  padding: 0 24px 20px;
  border: 0;
  @media (max-width: 600px) {
    gap: 6px;
    padding: 0 12px 16px;
  }
`;
export const SettingsList = styled.div`
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
`;
export const SettingsRow = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px 44px;
  align-items: start;
  gap: 12px;
  > button {
    margin-top: 28px;
  }
  input[readonly] {
    background: #f3f3f3;
    color: #5e616b;
  }
  @media (max-width: 480px) {
    gap: 8px;
  }
`;
export const ColorPicker = styled.input`
  display: block;
  appearance: none;
  -webkit-appearance: none;
  width: 46px;
  height: 46px;
  margin: 0;
  padding: 4px;
  border: 1px solid #e7e8ec;
  border-radius: 50%;
  background: #ffffff;
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color 160ms,
    box-shadow 160ms;
  &::-webkit-color-swatch-wrapper {
    padding: 0;
  }
  &::-webkit-color-swatch {
    border: 0;
    border-radius: 50%;
  }
  &::-moz-color-swatch {
    border: 0;
    border-radius: 50%;
  }
  &:hover {
    border-color: #b8bec9;
    box-shadow: 0 0 0 3px rgba(12, 50, 115, 0.05);
  }
  ${focus}
`;
export const FilterButton = styled.button`
  ${button}
  min-height: 44px;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 999px;
  border-color: ${({ $active, $category }) =>
    $active
      ? $category
        ? categoryStyle($category).color
        : "#0c3273"
      : "#e2e2e4"};
  background: ${({ $active, $category }) =>
    $active
      ? $category
        ? categoryStyle($category).background
        : "#0c3273"
      : "#ffffff"};
  color: ${({ $active, $category }) =>
    $active
      ? $category
        ? categoryStyle($category).text
        : "#ffffff"
      : "#5e616b"};
  font-size: 12px;
  &:hover {
    box-shadow: 0 2px 8px rgba(12, 50, 115, 0.08);
  }
  @media (max-width: 600px) {
    padding: 8px 9px;
    font-size: 11px;
  }
`;
export const ColorDot = styled.span`
  display: inline-block;
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${({ $category }) => categoryStyle($category).color};
`;
export const CalendarSurface = styled.div`
  min-width: 0;
  padding: 0 16px;
  --fc-border-color: #eeeef0;
  --fc-page-bg-color: #ffffff;
  --fc-neutral-bg-color: #fafafa;
  --fc-today-bg-color: #f0faf7;
  --fc-small-font-size: 12px;
  --fc-event-selected-overlay-color: rgba(12, 50, 115, 0.08);
  .fc {
    width: 100%;
    font-size: 13px;
  }
  .fc table {
    width: 100%;
    table-layout: fixed;
  }
  .fc .fc-scrollgrid {
    border: 0;
  }
  .fc .fc-scrollgrid-section > td {
    border-right: 0;
  }
  .fc .fc-col-header-cell {
    height: 40px;
    border-left: 0;
    border-right: 0;
    background: #fafafa;
    vertical-align: middle;
  }
  .fc .fc-col-header-cell-cushion {
    padding: 8px 0;
    color: #747781;
    font-size: 12px;
    font-weight: 600;
    text-decoration: none;
  }
  .fc .fc-col-header-cell.fc-day-sun .fc-col-header-cell-cushion {
    color: #aa4d4d;
  }
  .fc .fc-col-header-cell.fc-day-sat .fc-col-header-cell-cushion {
    color: #245fc5;
  }
  .fc .fc-daygrid-day {
    cursor: pointer;
  }
  .fc .fc-daygrid-day-frame {
    min-height: 104px;
  }
  .fc .fc-daygrid-day-top {
    justify-content: flex-start;
    padding: 5px 4px 2px;
  }
  .fc .fc-daygrid-day-number {
    padding: 0;
    color: inherit;
    text-decoration: none;
  }
  .fc .fc-daygrid-day-events {
    margin: 0 3px 5px;
  }
  .fc .fc-day-selected {
    background: #f2f5fb;
  }
  .fc .fc-day-selected .fc-daygrid-day-frame {
    box-shadow: inset 0 0 0 2px #0c3273;
  }
  .fc .fc-day-other .fc-daygrid-day-top {
    opacity: 0.4;
  }
  .fc .fc-daygrid-event {
    min-width: 0;
    margin-top: 3px;
    padding: 3px 5px;
    overflow: hidden;
    border-radius: 5px;
    font-size: 12px;
    line-height: 17px;
    cursor: pointer;
  }
  .fc .fc-daygrid-event-harness {
    min-width: 0;
  }
  .fc .fc-event-main {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .fc .fc-event-title {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .fc .fc-event-time {
    font-weight: 500;
  }
  .fc .fc-daygrid-more-link {
    padding: 4px;
    color: #0c3273;
    font-size: 11px;
    font-weight: 600;
  }
  .fc .fc-event:focus-visible,
  .fc .fc-daygrid-more-link:focus-visible {
    outline: 2px solid #006a65;
    outline-offset: 2px;
  }
  .fc .fc-popover {
    max-width: min(320px, calc(100vw - 40px));
    overflow: hidden;
    border: 1px solid #e2e2e4;
    border-radius: 12px;
    box-shadow: 0 8px 20px rgba(12, 50, 115, 0.12);
  }
  .fc .fc-popover-header {
    padding: 12px;
    background: #f3f3f3;
    color: #001d4f;
  }
  .fc .fc-popover-body {
    min-width: 220px;
    padding: 8px;
  }
  @media (max-width: 600px) {
    padding: 0 6px;
    .fc {
      font-size: 11px;
    }
    .fc .fc-daygrid-day-frame {
      min-height: 76px;
    }
    .fc .fc-daygrid-day-top {
      padding: 3px 0 1px;
    }
    .fc .fc-daygrid-day-events {
      margin: 0 1px 3px;
    }
    .fc .fc-daygrid-event {
      padding: 2px 3px;
      font-size: 10px;
      line-height: 15px;
    }
    .fc .fc-daygrid-more-link {
      padding: 3px 1px;
      font-size: 10px;
    }
  }
`;
export const DayNumber = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: ${({ $selected, $today }) =>
    $selected ? "#0c3273" : $today ? "#e0f5ef" : "transparent"};
  color: ${({ $selected, $today }) =>
    $selected ? "#ffffff" : $today ? "#006a65" : "#444650"};
  font: inherit;
  font-size: 12px;
  font-weight: ${({ $selected, $today }) => ($selected || $today ? 700 : 500)};
  cursor: pointer;
  &:hover {
    box-shadow: inset 0 0 0 1px #00a79f;
  }
  ${focus}
`;
export const CalendarEvent = styled.span`
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  > span {
    font-size: 0.9em;
    font-weight: 600;
  }
  @media (max-width: 600px) {
    > span {
      display: none;
    }
  }
`;
export const Sidebar = styled.aside`
  ${card}
  padding: 24px;
  > button {
    width: 100%;
    margin-top: 20px;
  }
  @media (max-width: 600px) {
    padding: 20px;
  }
`;
export const AgendaHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  > div {
    min-width: 0;
  }
  p {
    margin: 4px 0 0;
    color: #747781;
    font-size: 12px;
  }
`;
export const AgendaTitle = styled.h2`
  margin: 0;
  color: #001d4f;
  font-size: 18px;
  font-weight: 700;
  line-height: 28px;
  word-break: keep-all;
  ${focus}
`;
export const CountBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 28px;
  min-height: 28px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #e6f7f3;
  color: #006a65;
  font-size: 12px;
  font-weight: 700;
`;
export const Tabs = styled.div`
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  padding: 4px;
  border-radius: 12px;
  background: #f3f3f3;
`;
export const TabButton = styled.button`
  ${button}
  flex: 1;
  min-width: 0;
  padding: 8px;
  border-radius: 9px;
  background: ${({ $active }) => ($active ? "#ffffff" : "transparent")};
  color: ${({ $active }) => ($active ? "#0c3273" : "#747781")};
  box-shadow: ${({ $active }) =>
    $active ? "0 2px 6px rgba(12, 50, 115, 0.06)" : "none"};
  font-size: 12px;
  &:hover {
    color: #0c3273;
  }
`;
export const AgendaList = styled.ul`
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 620px;
  margin: 0;
  padding: 3px;
  overflow-y: auto;
  list-style: none;
  scrollbar-width: thin;
`;
export const AgendaItem = styled.li`
  min-width: 0;
`;
export const AgendaButton = styled.button`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 16px;
  border: 1px solid #eeeef0;
  border-radius: 12px;
  background: #ffffff;
  color: #1a1c1c;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background 160ms,
    border-color 160ms;
  &:hover {
    border-color: #c4c6d2;
    background: #fafbfdf5;
  }
  ${focus}
`;
export const AgendaMeta = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
  color: #747781;
  font-size: 11px;
`;
export const EventTitle = styled.strong`
  display: block;
  width: 100%;
  color: #1a1c1c;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  overflow-wrap: anywhere;
`;
export const EventTime = styled.span`
  display: flex;
  align-items: center;
  gap: 6px;
  color: #747781;
  font-size: 12px;
  line-height: 20px;
`;
export const CategoryBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border-radius: 999px;
  background: ${({ $category }) => categoryStyle($category).background};
  color: ${({ $category }) => categoryStyle($category).text};
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
`;
export const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
  padding: 28px 12px;
  text-align: center;
  h3 {
    margin: 6px 0 0;
    color: #001d4f;
    font-size: 16px;
    line-height: 24px;
  }
  p {
    max-width: 300px;
    margin: 0;
    color: #747781;
    font-size: 13px;
    line-height: 22px;
    word-break: keep-all;
  }
  button {
    margin-top: 4px;
  }
  ${CalendarCard} > & {
    min-height: 100px;
    padding: 12px 24px 24px;
  }
`;
export const EmptyIcon = styled.div`
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #e6f7f3;
  color: #006a65;
`;
export const Status = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px 16px;
  border: 1px solid #bce5da;
  border-radius: 12px;
  background: #e6f7f3;
  color: #006a65;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
  &:empty {
    margin: 0;
    padding: 0;
    border: 0;
  }
`;
export const Dialog = styled.dialog`
  width: min(560px, calc(100vw - 32px));
  max-width: 560px;
  max-height: calc(100vh - 32px);
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 28px;
  overflow-y: auto;
  border: 0;
  border-radius: 24px;
  background: #ffffff;
  color: #1a1c1c;
  font-family: inherit;
  line-height: 1.5;
  box-shadow: 0 16px 48px rgba(12, 50, 115, 0.18);
  &::backdrop {
    background: rgba(12, 50, 115, 0.4);
  }
  ${focus}
  @media (max-width: 480px) {
    padding: 20px;
    border-radius: 20px;
  }
`;
export const DialogHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
`;
export const DialogTitle = styled.h2`
  margin: 0;
  color: #001d4f;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
  word-break: keep-all;
`;
export const DialogDescription = styled.p`
  margin: 0 0 24px;
  color: #747781;
  font-size: 14px;
  line-height: 24px;
  word-break: keep-all;
`;
export const DialogBody = styled.div`
  min-width: 0;
`;
export const Form = styled.form`
  margin-top: 24px;
  > p:first-child {
    margin: 0 0 16px;
  }
`;
export const FormGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px 16px;
  @media (max-width: 480px) {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
`;
export const Field = styled.div`
  min-width: 0;
  grid-column: ${({ $wide }) => ($wide ? "1 / -1" : "auto")};
  label {
    display: block;
    margin-bottom: 8px;
    color: #444650;
    font-size: 14px;
    font-weight: 600;
    line-height: 20px;
  }
  label > span {
    color: #747781;
    font-size: 12px;
    font-weight: 400;
  }
`;
export const Input = styled.input`
  ${input}
`;
export const Select = styled.select`
  ${input} cursor: pointer;
  padding-right: 28px;
`;
export const Textarea = styled.textarea`
  ${input} min-height: 112px;
  resize: vertical;
`;
export const FieldError = styled.p`
  margin: 6px 0 0;
  color: #ba1a1a;
  font-size: 12px;
  line-height: 20px;
`;
export const FormHint = styled.p`
  margin: 8px 0 0;
  color: #747781;
  font-size: 12px;
  line-height: 20px;
`;
export const CategoryPreview = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: #747781;
  font-size: 12px;
  line-height: 20px;
`;
export const DialogActions = styled.div`
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #eeeeee;
  @media (max-width: 480px) {
    > button {
      flex: 1;
      padding-right: 12px;
      padding-left: 12px;
    }
  }
`;
export const DangerButton = styled.button`
  ${button} background: #ba1a1a;
  color: #ffffff;
  &:hover:not(:disabled) {
    background: #93000a;
  }
`;
export const DetailTitle = styled.h3`
  margin: 20px 0;
  color: #001d4f;
  font-size: 22px;
  font-weight: 700;
  line-height: 32px;
  overflow-wrap: anywhere;
`;
export const DetailList = styled.dl`
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 14px 16px;
  margin: 0;
  padding: 20px;
  border-radius: 12px;
  background: #f8f9fb;
  > div {
    display: contents;
  }
  dt {
    color: #747781;
    font-size: 13px;
    line-height: 22px;
  }
  dd {
    margin: 0;
    color: #444650;
    font-size: 14px;
    line-height: 22px;
    overflow-wrap: anywhere;
  }
  dd svg {
    margin-right: 6px;
    vertical-align: middle;
  }
  @media (max-width: 480px) {
    grid-template-columns: 62px minmax(0, 1fr);
    gap: 12px;
    padding: 16px;
  }
`;
export const DetailDescription = styled.div`
  margin: 24px 0 0;
  color: #444650;
  font-size: 14px;
  line-height: 26px;
  overflow-wrap: anywhere;
  h3 {
    margin: 0 0 8px;
    font-size: 14px;
    font-weight: 600;
  }
  p {
    margin: 0;
    white-space: pre-wrap;
  }
`;
