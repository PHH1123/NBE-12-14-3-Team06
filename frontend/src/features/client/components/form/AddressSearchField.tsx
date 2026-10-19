'use client';

import Image from 'next/image';
import { Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/lib/cn';
import { searchPlaces, type PlaceSearchResult } from '@/lib/kakaoMap';
import { FIELD } from './fields';

type Props = {
  id: string;
  placeholder: string;
  defaultValue?: string;
  onSelect: (result: PlaceSearchResult) => void;
  onClear?: () => void;
  formatSelected?: (result: PlaceSearchResult) => string;
  variant?: 'default' | 'compact';
};

/** 검색 결과에서 장소를 선택해야 서버에 필요한 주소와 좌표가 결정됩니다. */
export default function AddressSearchField({
  id,
  placeholder,
  defaultValue = '',
  onSelect,
  onClear,
  formatSelected,
  variant = 'default',
}: Props) {
  const [query, setQuery] = useState(defaultValue);
  const [results, setResults] = useState<PlaceSearchResult[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const requestId = useRef(0);
  const root = useRef<HTMLDivElement>(null);
  const compact = variant === 'compact';

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, []);

  const runSearch = async () => {
    if (!query.trim()) {
      setError('검색할 병원명이나 주소를 입력해주세요.');
      return;
    }
    const currentRequest = ++requestId.current;
    setSearching(true);
    setError('');
    try {
      const found = await searchPlaces(query);
      if (currentRequest !== requestId.current) return;
      setResults(found);
      setOpen(found.length > 0);
      if (found.length === 0) setError('검색 결과가 없습니다. 다른 말로 검색해보세요.');
    } catch (err) {
      if (currentRequest !== requestId.current) return;
      setError(err instanceof Error ? err.message : '검색에 실패했습니다.');
    } finally {
      if (currentRequest === requestId.current) setSearching(false);
    }
  };

  const handleSelect = (result: PlaceSearchResult) => {
    const display = formatSelected ? formatSelected(result) : result.address;
    setQuery(display);
    setOpen(false);
    setResults([]);
    setError('');
    onSelect(result);
  };

  return (
    <div ref={root} className="relative">
      <div className="relative">
        <input
          id={id}
          type="text"
          value={query}
          onChange={(event) => {
            ++requestId.current;
            setSearching(false);
            setQuery(event.target.value);
            setOpen(false);
            setResults([]);
            setError('');
            onClear?.();
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              void runSearch();
            }
          }}
          placeholder={placeholder}
          aria-label={placeholder}
          autoComplete="off"
          className={compact
            ? 'w-full h-[48px] pl-11 pr-4 bg-white border border-[#E1E5E8] rounded-xl text-sm text-[#14243A] placeholder:text-[#9AA2AA] focus:border-[#426B93] focus-visible:outline-2 focus-visible:outline-[#426B93] focus-visible:outline-offset-2'
            : cn(FIELD, 'pr-12')}
        />
        <button
          type="button"
          onClick={() => void runSearch()}
          disabled={searching}
          aria-label={searching ? '검색 중' : '장소 검색'}
          title="장소 검색"
          className={compact
            ? 'absolute top-1/2 left-3.5 -translate-y-1/2 text-[#67717C] hover:text-[#14243A] disabled:opacity-50'
            : 'absolute top-1/2 right-4 -translate-y-1/2 disabled:opacity-50'}
        >
          {compact ? <Search className="h-4 w-4" /> : <Image src="/icons/search.svg" alt="" width={14} height={14} />}
        </button>
      </div>
      {error && <p role="alert" className={compact ? 'mt-1.5 text-xs font-medium text-[#B4413C]' : 'mt-1.5 px-4 text-sm font-medium text-[#b91d1d]'}>{error}</p>}
      {open && results.length > 0 && (
        <ul className={compact
          ? 'absolute z-30 mt-1.5 max-h-56 w-full overflow-y-auto rounded-xl border border-[#E1E5E8] bg-white shadow-lg divide-y divide-[#E1E5E8]'
          : 'absolute z-10 mt-1.5 max-h-64 w-full overflow-y-auto rounded-[20px] border border-line bg-white p-2 shadow-card'}>
          {results.map((result) => (
            <li key={`${result.name}-${result.address}`}>
              <button type="button" onClick={() => handleSelect(result)}
                className={compact
                  ? 'w-full p-3 text-left text-xs hover:bg-[#F7F8F8]'
                  : 'flex w-full flex-col gap-0.5 rounded-[12px] px-3 py-2 text-left hover:bg-line-soft'}>
                <span className={compact ? 'block font-semibold text-[#14243A]' : 'block text-sm font-semibold text-brand'}>{result.name || result.address}</span>
                <span className={compact ? 'block text-[#67717C] text-[11px] mt-0.5' : 'block text-xs text-brand-muted'}>{result.address}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
