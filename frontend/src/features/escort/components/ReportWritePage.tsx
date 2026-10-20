'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { AlertTriangle, ChevronLeft } from 'lucide-react';
import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { useActionDialog } from '@/components/ui/useActionDialog';
import { useRequireAuth } from '@/features/auth';
import { writeReport } from '@/features/report';
import { fetchReportTarget } from '../api';
import { formatClockTime } from '../lib/date';
import { DEPARTMENTS } from '../model/departments';
import { clearReportDraft, loadReportDraft, saveReportDraft, type ReportDraft, type ReportDraftFields } from '../model/reportDraft';
import type { ReportTarget } from '../types';

type TargetResult =
  | { status: 'notFound' }
  | { status: 'error'; message: string }
  | { status: 'ready'; target: ReportTarget };

type DraftState = { key: number; loaded?: ReportDraft; savedAt?: number };

type FormErrorKey = 'department' | 'purpose' | 'directions';
type FormErrors = Partial<Record<FormErrorKey, string>>;

function readDraftState(applicationId: number): DraftState {
  const loaded = loadReportDraft(applicationId);
  return { key: applicationId, loaded, savedAt: loaded?.savedAt };
}

function readFields(form: HTMLFormElement): ReportDraftFields {
  const data = new FormData(form);
  const text = (key: string) => String(data.get(key) ?? '');
  const inputDepartment = text('department').trim();
  const department = DEPARTMENTS.find((option) => option.label === inputDepartment || option.value === inputDepartment)?.value ?? inputDepartment;
  return {
    department,
    purpose: text('purpose'),
    summary: text('directions'),
    notes: text('specialNotes'),
  };
}

function validate(fields: ReportDraftFields): FormErrors {
  const errors: FormErrors = {};
  if (!fields.department.trim()) {
    errors.department = '진료 과목을 선택해 주세요.';
  } else if (!DEPARTMENTS.some((option) => option.value === fields.department)) {
    errors.department = '목록에 있는 진료 과목을 선택해 주세요.';
  }
  if (!fields.purpose.trim()) errors.purpose = '진료 목적을 입력해 주세요.';
  if (!fields.summary.trim()) errors.directions = '진료 내용을 상세히 입력해 주세요.';
  return errors;
}

function sameMinute(a: number | undefined, b: number | undefined): boolean {
  if (a === undefined || b === undefined) return a === b;
  return Math.floor(a / 60_000) === Math.floor(b / 60_000);
}

export default function ReportWritePage() {
  const { confirm, dialog } = useActionDialog();
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const params = useParams<{ applicationId: string }>();
  const router = useRouter();
  const applicationId = Number(params.applicationId);
  const formRef = useRef<HTMLFormElement>(null);

  const [result, setResult] = useState<{ key?: number; data?: TargetResult }>({});
  const [draft, setDraft] = useState<DraftState>(() => readDraftState(applicationId));
  const [department, setDepartment] = useState(() => loadReportDraft(applicationId)?.department ?? '');
  const [errors, setErrors] = useState<FormErrors>({});
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  if (!Object.is(draft.key, applicationId)) {
    const nextDraft = readDraftState(applicationId);
    setDraft(nextDraft);
    setDepartment(nextDraft.loaded?.department ?? '');
  }

  useEffect(() => {
    if (!Number.isFinite(applicationId)) return;
    let ignore = false;
    fetchReportTarget(applicationId)
      .then((target) => {
        if (!ignore) setResult({ key: applicationId, data: target ? { status: 'ready', target } : { status: 'notFound' } });
      })
      .catch((error: unknown) => {
        if (!ignore) {
          setResult({
            key: applicationId,
            data: { status: 'error', message: error instanceof Error ? error.message : '동행 정보를 불러오지 못했습니다.' },
          });
        }
      });
    return () => { ignore = true; };
  }, [applicationId]);

  const state = result.key === applicationId ? result.data : undefined;

  const handleDraftChange = (event: FormEvent<HTMLFormElement>) => {
    const savedAt = saveReportDraft(applicationId, readFields(event.currentTarget));
    setDraft((prev) => (sameMinute(prev.savedAt, savedAt) ? prev : { ...prev, savedAt }));
    if (Object.keys(errors).length) setErrors({});
  };

  const handleDepartmentChange = (value: string) => {
    setDepartment(value);
    if (!formRef.current) return;
    const savedAt = saveReportDraft(applicationId, { ...readFields(formRef.current), department: value });
    setDraft((prev) => (sameMinute(prev.savedAt, savedAt) ? prev : { ...prev, savedAt }));
    if (Object.keys(errors).length) setErrors({});
  };

  const handleDiscardDraft = async () => {
    if (!await confirm({ title: '임시 저장된 내용을 지울까요?', description: '저장된 내용은 복구할 수 없습니다. 새로 작성하시겠습니까?', tone: 'danger', confirmLabel: '초기화하기' })) return;
    clearReportDraft(applicationId);
    setDraft({ key: applicationId });
    setDepartment('');
    setErrors({});
    setSubmitError('');
  };

  const handleFormSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    const found = validate(readFields(event.currentTarget));
    setErrors(found);
    if (Object.keys(found).length === 0) setShowConfirmModal(true);
  };

  const handleConfirmSubmit = async () => {
    if (submitting || !formRef.current || state?.status !== 'ready') return;
    const fields = readFields(formRef.current);
    const found = validate(fields);
    setErrors(found);
    if (Object.keys(found).length) {
      setShowConfirmModal(false);
      return;
    }

    setSubmitting(true);
    setSubmitError('');
    try {
      await writeReport(state.target.applicationId, {
        department: fields.department,
        purpose: fields.purpose.trim(),
        originContent: fields.summary.trim(),
        notes: fields.notes.trim(),
      });
      clearReportDraft(applicationId);
      setShowConfirmModal(false);
      router.push(`/escort/${state.target.applicationId}/report/done`);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : '보고서 제출에 실패했습니다.');
      setShowConfirmModal(false);
      setSubmitting(false);
    }
  };

  if (authLoading || !state) {
    return (
      <AppShell>
        <PageFeedback kind="loading" title="동행 정보를 불러오는 중입니다." />
      </AppShell>
    );
  }
  if (!user) return null;

  if (state.status !== 'ready') {
    return (
      <AppShell>
        <PageFeedback kind={state.status === 'error' ? 'error' : 'empty'}
          title={state.status === 'notFound' ? '동행 정보를 찾을 수 없습니다.' : '동행 현황을 불러오지 못했습니다.'}
          description={state.status === 'error' ? state.message : undefined}
          href="/mypage/applications" action="신청 목록으로 돌아가기" />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
          <div className="max-w-2xl mx-auto space-y-8">
          <Link
            href="/mypage/applications"
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A]"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>신청 목록으로 돌아가기</span>
          </Link>

            <div>
              <span className="text-xs font-semibold text-[#426B93] uppercase tracking-wider block mb-2">
                Post-Escort Documentation
              </span>
              <h1 className="text-3xl font-semibold text-[#14243A] tracking-tight">
                진료 보고서 작성
              </h1>
              <p className="mt-2 text-sm text-[#67717C] leading-relaxed">
                ※ 작성하신 내용은 보호자에게 전달되므로 정확하게 작성해주세요.
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
              <div className="p-4 bg-[#EDF0F2] rounded-xl text-xs text-[#67717C] space-y-1.5 border border-[#E1E5E8]">
                <div className="font-semibold text-[#14243A] flex items-center gap-1.5">
                  <AlertTriangle className="w-4 h-4 text-[#9A6B12]" />
                  보고서 작성 유의사항
                </div>
                <p>• 의뢰인의 개인정보(주민등록번호, 상세 금융정보 등)는 포함하지 말아주세요.</p>
                <p>• 허위 작성 시 서비스 이용에 제한이 있을 수 있습니다.</p>
              </div>

              {draft.loaded && (
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] px-4 py-3 text-xs text-[#67717C]">
                  <span>{formatClockTime(new Date(draft.loaded.savedAt))}에 임시 저장한 내용을 불러왔습니다.</span>
                  <button type="button" onClick={handleDiscardDraft} className="font-semibold text-[#426B93] underline underline-offset-2">
                    새로 작성하기
                  </button>
                </div>
              )}

              <form
                ref={formRef}
                key={`${applicationId}:${draft.loaded ? 'draft' : 'blank'}`}
                onSubmit={handleFormSubmit}
                onChange={handleDraftChange}
                noValidate
                className="space-y-5"
              >
                <div>
                  <label htmlFor="report-department" className="block text-xs font-semibold text-[#67717C] mb-1.5">
                    진료 과목 <span className="text-[#B4413C]">*</span>
                  </label>
                  <DropdownSelect
                    id="report-department"
                    name="department"
                    value={department}
                    onValueChange={handleDepartmentChange}
                    options={DEPARTMENTS}
                    placeholder="진료 과목을 선택해 주세요."
                    required
                  />
                  {errors.department && <p role="alert" className="text-xs text-[#B4413C] mt-1">{errors.department}</p>}
                </div>

                <div>
                  <label htmlFor="report-purpose" className="block text-xs font-semibold text-[#67717C] mb-1.5">
                    진료 목적 <span className="text-[#B4413C]">*</span>
                  </label>
                  <input
                    id="report-purpose"
                    name="purpose"
                    type="text"
                    defaultValue={draft.loaded?.purpose ?? ''}
                    placeholder="예: 정기 혈압약 처방 및 엑스레이 검사 결과 상담"
                    aria-invalid={Boolean(errors.purpose)}
                    className="w-full h-[48px] px-3.5 bg-white border border-[#E1E5E8] rounded-xl text-sm focus:border-[#426B93] focus:outline-none focus:ring-2 focus:ring-[#426B93]/20"
                  />
                  {errors.purpose && <p role="alert" className="text-xs text-[#B4413C] mt-1">{errors.purpose}</p>}
                </div>

                <div>
                  <label htmlFor="report-directions" className="block text-xs font-semibold text-[#67717C] mb-1.5">
                    의사 전달사항 <span className="text-[#B4413C]">*</span>
                  </label>
                  <textarea
                    id="report-directions"
                    name="directions"
                    rows={4}
                    defaultValue={draft.loaded?.summary ?? ''}
                    placeholder="담당 의사 선생님이 설명하신 핵심 소견 및 복약 변경 내용을 상세히 입력해주세요."
                    aria-invalid={Boolean(errors.directions)}
                    className="w-full p-3.5 bg-white border border-[#E1E5E8] rounded-xl text-sm focus:border-[#426B93] focus:outline-none focus:ring-2 focus:ring-[#426B93]/20 resize-none"
                  />
                  {errors.directions && <p role="alert" className="text-xs text-[#B4413C] mt-1">{errors.directions}</p>}
                </div>

                <div>
                  <label htmlFor="report-special-notes" className="block text-xs font-semibold text-[#67717C] mb-1.5">
                    특이사항 (선택)
                  </label>
                  <textarea
                    id="report-special-notes"
                    name="specialNotes"
                    rows={3}
                    defaultValue={draft.loaded?.notes ?? ''}
                    placeholder="원내 이동 중 어르신의 컨디션, 보행 이상 징후 등 특이사항을 적어주세요."
                    className="w-full p-3.5 bg-white border border-[#E1E5E8] rounded-xl text-sm focus:border-[#426B93] focus:outline-none focus:ring-2 focus:ring-[#426B93]/20 resize-none"
                  />
                </div>

                {submitError && <p role="alert" className="text-xs text-[#B4413C]">{submitError}</p>}

                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={submitting}
                    className="w-full h-[52px] rounded-[10px] bg-[#14243A] text-white font-semibold text-base hover:bg-[#1f3757] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#426B93] shadow-xs disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    {submitting ? '제출 중...' : '제출하기'}
                  </button>
                </div>
              </form>
            </div>
            <p className="text-xs text-[#67717C] text-center" aria-live="polite">
              작성 중인 내용은 이 브라우저에 임시 저장됩니다.
              {draft.savedAt !== undefined && ` (${formatClockTime(new Date(draft.savedAt))} 저장됨)`}
            </p>
          </div>

          {showConfirmModal && (
            <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4" role="presentation">
              <div role="dialog" aria-modal="true" aria-labelledby="report-confirm-title" className="bg-white rounded-2xl max-w-sm w-full p-6 text-center space-y-4 shadow-xl">
                <h3 id="report-confirm-title" className="text-lg font-semibold text-[#14243A]">보고서를 제출하시겠습니까?</h3>
                <p className="text-xs text-[#67717C]">
                  제출 후에는 수정할 수 없습니다.
                  <br />
                  작성한 진료 내용은 의뢰인이 확인할 수 있습니다.
                </p>
                <div className="flex gap-2 pt-2">
                  <button
                    type="button"
                    disabled={submitting}
                    onClick={() => setShowConfirmModal(false)}
                    className="flex-1 h-[44px] rounded-lg border border-[#E1E5E8] text-xs font-semibold text-[#67717C] disabled:opacity-60"
                  >
                    수정하기
                  </button>
                  <button
                    type="button"
                    disabled={submitting}
                    onClick={handleConfirmSubmit}
                    className="flex-1 h-[44px] rounded-lg bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757] disabled:opacity-60"
                  >
                    {submitting ? '제출 중...' : '제출 완료'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
      {dialog}
    </AppShell>
  );
}
