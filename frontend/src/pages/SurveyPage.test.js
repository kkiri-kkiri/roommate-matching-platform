import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";

import SurveyPage from "./SurveyPage";
import { FACULTY_OPTIONS, SURVEY_STEPS } from "./surveyData";

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function fillRequiredFields(stepIndex) {
  const step = SURVEY_STEPS[stepIndex];

  step.fields.forEach((field) => {
    if (!field.required) return;

    if (field.type === "singleSelect" || field.type === "multiSelect") {
      const group = screen.getByRole("group", {
        name: new RegExp(
          `^${escapeRegExp(field.label)}\\s*필수(?:\\s|$)`,
        ),
      });
      const input = within(group).getByRole(
        field.type === "multiSelect" ? "checkbox" : "radio",
        { name: field.options[0] },
      );
      fireEvent.click(input);
      return;
    }

    if (field.type === "facultySelect") {
      fireEvent.change(screen.getByRole("combobox", { name: /나의 학부/ }), {
        target: { value: FACULTY_OPTIONS[0].name },
      });
      return;
    }

    if (field.type === "majorSelect") {
      fireEvent.change(screen.getByRole("combobox", { name: /나의 전공/ }), {
        target: { value: FACULTY_OPTIONS[0].majors[0] },
      });
      return;
    }

    if (field.type === "number" || field.type === "text") {
      fireEvent.change(
        screen.getByRole("textbox", {
          name: (accessibleName) => accessibleName.startsWith(field.label),
        }),
        {
          target: { value: field.id === "birthYear" ? "2005" : "20260001" },
        },
      );
      return;
    }

    if (field.type === "mbti") {
      field.dimensions.forEach((dimension) => {
        fireEvent.click(
          screen.getByRole("button", { name: dimension.options[0] }),
        );
      });
    }
  });
}

function completeStep(stepIndex, nextStepTitle) {
  fillRequiredFields(stepIndex);
  fireEvent.click(screen.getByRole("button", { name: "다음" }));
  expect(
    screen.getByRole("heading", { name: nextStepTitle }),
  ).toBeInTheDocument();
}

describe("SurveyPage", () => {
  test("renders the first step and blocks progress when required fields are empty", () => {
    render(<SurveyPage />);

    expect(
      screen.getByRole("heading", { name: "기본·학적 정보" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("progressbar", { name: "설문 작성 진행률" }),
    ).toHaveAttribute(
      "aria-valuenow",
      String(Math.round(100 / (SURVEY_STEPS.length + 1))),
    );
    expect(screen.getByLabelText(/나의 전공/)).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "다음" }));

    expect(
      screen.getByText(
        "필수 항목을 확인해주세요. 첫 번째 미입력 항목으로 이동했습니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("성별 항목을 선택하거나 입력해주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("MBTI 네 가지 기준을 모두 선택해주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "기본·학적 정보" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "생활 패턴" }),
    ).not.toBeInTheDocument();
  });

  test("filters and enables majors by faculty and handles a faculty without majors", () => {
    render(<SurveyPage />);

    const facultySelect = screen.getByLabelText(/나의 학부/);
    const majorSelect = screen.getByLabelText(/나의 전공/);

    expect(majorSelect).toBeDisabled();

    fireEvent.change(facultySelect, {
      target: { value: "컴퓨터응용수학부" },
    });

    expect(majorSelect).toBeEnabled();
    expect(
      within(majorSelect).getByRole("option", { name: "정보보안전공" }),
    ).toBeInTheDocument();
    expect(
      within(majorSelect).queryByRole("option", { name: "법학전공" }),
    ).not.toBeInTheDocument();

    fireEvent.change(majorSelect, {
      target: { value: "정보보안전공" },
    });
    expect(majorSelect).toHaveValue("정보보안전공");

    fireEvent.change(facultySelect, {
      target: { value: "법경영학부" },
    });

    expect(majorSelect).toHaveValue("");
    expect(
      within(majorSelect).getByRole("option", { name: "법학전공" }),
    ).toBeInTheDocument();
    expect(
      within(majorSelect).queryByRole("option", { name: "정보보안전공" }),
    ).not.toBeInTheDocument();

    fireEvent.change(facultySelect, {
      target: { value: "HK자율전공학부" },
    });

    expect(majorSelect).toBeDisabled();
    expect(majorSelect).toHaveDisplayValue("세부전공 없음");
    expect(
      screen.getByText("선택한 학부는 별도 세부전공이 없어요."),
    ).toBeInTheDocument();
  });

  test("exclusive multi-select options replace and are replaced by regular options", () => {
    render(<SurveyPage />);

    completeStep(0, "생활 패턴");
    completeStep(1, "청소·생활 습관");
    completeStep(2, "기숙사 생활");

    const breakfast = screen.getByLabelText("조식");
    const lunch = screen.getByLabelText("중식");
    const noMealPlan = screen.getByLabelText("신청 안 함");

    fireEvent.click(breakfast);
    expect(breakfast).toBeChecked();
    expect(noMealPlan).not.toBeChecked();

    fireEvent.click(noMealPlan);
    expect(noMealPlan).toBeChecked();
    expect(breakfast).not.toBeChecked();

    fireEvent.click(lunch);
    expect(lunch).toBeChecked();
    expect(noMealPlan).not.toBeChecked();
    expect(breakfast).not.toBeChecked();
  });
});
