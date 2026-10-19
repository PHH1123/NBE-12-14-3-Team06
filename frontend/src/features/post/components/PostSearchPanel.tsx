'use client';

import { Search } from 'lucide-react';
import { type FormEvent } from 'react';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { PAY_OPTIONS, PERIOD_OPTIONS, REGION_OPTIONS, SORT_OPTIONS } from '../model';
import type { PostFilters } from '../types';

type Props = {
  filters: PostFilters;
  keywordInput: string;
  onKeywordInputChange: (value: string) => void;
  onSearch: () => void;
  onFilterChange: (patch: Partial<PostFilters>) => void;
  onReset: () => void;
};

export default function PostSearchPanel({
  filters,
  keywordInput,
  onKeywordInputChange,
  onSearch,
  onFilterChange,
  onReset,
}: Props) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSearch();
  };

  return (
    <form onSubmit={handleSubmit} className="mb-8 space-y-6 rounded-2xl border border-[#E1E5E8] bg-white p-5 shadow-xs sm:p-6">
      <div className="relative">
        <label htmlFor="post-search-input" className="sr-only">공고 검색</label>
        <Search aria-hidden="true" className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-[#67717C]" />
        <input
          id="post-search-input"
          type="search"
          value={keywordInput}
          onChange={(event) => onKeywordInputChange(event.target.value)}
          placeholder="병원명, 지역, 공고 제목으로 검색"
          className="h-[52px] w-full rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] pl-12 pr-4 text-base text-[#202832] outline-none transition-colors placeholder:text-[#67717C] focus-visible:border-[#426B93] focus-visible:bg-white focus-visible:ring-2 focus-visible:ring-[#426B93]/20"
        />
      </div>
      <div className="grid grid-cols-1 items-end gap-4 border-t border-[#E1E5E8] pt-5 sm:grid-cols-2 lg:grid-cols-[repeat(4,minmax(0,1fr))_auto]">
        <DropdownSelect size="filter" id="post-region" label="지역 선택" value={filters.region} options={REGION_OPTIONS} onValueChange={(region) => onFilterChange({ region })} />
        <DropdownSelect size="filter" id="post-period" label="동행 날짜" value={filters.period} options={PERIOD_OPTIONS} onValueChange={(period) => onFilterChange({ period: period as PostFilters['period'] })} />
        <DropdownSelect size="filter" id="post-pay" label="희망 시급" value={filters.pay} options={PAY_OPTIONS} onValueChange={(pay) => onFilterChange({ pay: pay as PostFilters['pay'] })} />
        <DropdownSelect size="filter" id="post-sort" label="정렬 순서" value={filters.sort} options={SORT_OPTIONS} onValueChange={(sort) => onFilterChange({ sort: sort as PostFilters['sort'] })} />
        <div className="flex gap-2 sm:col-span-2 lg:col-span-1">
          <button type="button" onClick={onReset} className="h-[46px] flex-1 whitespace-nowrap rounded-lg border border-[#E1E5E8] bg-white px-4 text-sm font-semibold text-[#14243A] transition-colors duration-200 hover:bg-[#F7F8F8] lg:flex-none">초기화</button>
          <button type="submit" className="h-[46px] flex-1 whitespace-nowrap rounded-lg bg-[#14243A] px-4 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[#1F3757] lg:flex-none">검색</button>
        </div>
      </div>
    </form>
  );
}
