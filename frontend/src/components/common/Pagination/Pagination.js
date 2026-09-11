import { ChevronLeft, ChevronRight } from "lucide-react";

import * as S from "./PaginationCss";

function getVisiblePageNumbers(currentPage, totalPages, maxVisiblePages = 5) {
    if (totalPages <= maxVisiblePages) {
        return Array.from({ length: totalPages }, (_, index) => index + 1);
    }

    const start = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    const end = Math.min(totalPages, start + maxVisiblePages - 1);
    const adjustedStart = Math.max(1, end - maxVisiblePages + 1);

    return Array.from({ length: end - adjustedStart + 1 }, (_, index) => adjustedStart + index);
}

function Pagination({ currentPage, totalPages, onPageChange }) {
    if (totalPages <= 1) {
        return null;
    }

    const pageNumbers = getVisiblePageNumbers(currentPage, totalPages, 5);

    return (
        <S.Pagination aria-label="프로필 페이지 네비게이션">
            <S.PageNavButton
                type="button"
                aria-label="이전 페이지"
                disabled={currentPage === 1}
                onClick={() => onPageChange(Math.max(1, currentPage - 1))}
            >
                <ChevronLeft size={16} aria-hidden="true" />
            </S.PageNavButton>

            {pageNumbers.map((pageNumber) => (
                <S.PageNumberButton
                    key={pageNumber}
                    type="button"
                    aria-label={`${pageNumber}페이지`}
                    aria-current={pageNumber === currentPage ? "page" : undefined}
                    $active={pageNumber === currentPage}
                    onClick={() => onPageChange(pageNumber)}
                >
                    {pageNumber}
                </S.PageNumberButton>
            ))}

            <S.PageNavButton
                type="button"
                aria-label="다음 페이지"
                disabled={currentPage === totalPages}
                onClick={() => onPageChange(Math.min(totalPages, currentPage + 1))}
            >
                <ChevronRight size={16} aria-hidden="true" />
            </S.PageNavButton>
        </S.Pagination>
    );
}

export default Pagination;
