import type { Agreement, AgreementGroup, SignupRole } from '../types';

const TERMS: Agreement = {
  id: 'terms',
  title: '서비스 이용 약관 동의',
  description: '‘가지’동행 서비스 이용을 위한 기본적인 사항을 규정합니다.',
  required: true,
};

const PRIVACY: Agreement = {
  id: 'privacy',
  title: '개인정보 수집·이용 관련 동의',
  description: '회원 관리, 서비스 제공, 고객지원 등을 위해 필요한 개인정보를 수집·이용합니다.',
  required: true,
};

const SENSITIVE: Agreement = {
  id: 'sensitive',
  title: '민감정보 수집·이용 관련 동의(병원·진료 정보)',
  description: '병원 동행 서비스 제공을 위해 환자의 건강 상태·진료 내용 등 필요한 정보를 수집·이용합니다.',
  required: true,
};

const THIRD_PARTY: Agreement = {
  id: 'third-party',
  title: '매칭을 위한 개인정보 제3자 제공 동의',
  description: '매칭 및 동행 진행을 위해 상대방에게 필요한 프로필·연락처 등을 제공합니다.',
  required: true,
};

const LOCATION: Agreement = {
  id: 'location',
  title: '위치정보 이용·제공 동의',
  description:
    '동행 진행 중 실시간 위치 공유를 위해 위치 정보를 이용하며, 매칭된 의뢰인에게 제공될 수 있습니다.',
  required: true,
};

const MARKETING: Agreement = {
  id: 'marketing',
  title: '마케팅 정보 수신 동의',
  description: '이벤트, 신규 서비스, 혜택 등 유용한 정보를 이메일 또는 알림으로 받아볼 수 있습니다.',
  required: false,
};

export const AGREEMENT_GROUPS: Record<SignupRole, AgreementGroup> = {
  CLIENT: {
    requiredTitle: '필수 동의 항목(의뢰인)',
    required: [TERMS, PRIVACY, SENSITIVE, THIRD_PARTY],
    optional: [MARKETING],
  },
  ESCORT: {
    requiredTitle: '필수 동의 항목(동행매니저)',
    required: [TERMS, PRIVACY, THIRD_PARTY, LOCATION],
    optional: [MARKETING],
  },
};
