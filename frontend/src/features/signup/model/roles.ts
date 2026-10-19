import type { RoleOption } from '../types';

export const ROLE_OPTIONS: RoleOption[] = [
  {
    role: 'CLIENT',
    title: '병원 동행이 필요해요',
    description: ['혼자 가기 힘든 병원,', '믿을 수 있는 동행매니저와 함께하세요.'],
    image: '/images/signup/role-client-photo.jpg',
    imageAlt: '병원에서 어르신을 안내하는 동행 매니저',
    cta: '의뢰인으로 가입하기',
    variant: 'solid',
    href: '/signup/terms?role=CLIENT',
    benefits: [
      '원하는 날짜와 시간을 선택해 동행을 요청 할 수 있어요',
      '지원한 동행 매니저를 확인하고 선택할 수 있어요',
      '동행이 완료되면 보고서를 받을 수 있어요',
    ],
  },
  {
    role: 'ESCORT',
    title: '동행 매니저로 활동하고 싶어요',
    description: ['원하는 공고를 찾아 지원하고,', '내 일정에 맞춰 동행 활동을 시작하세요.'],
    image: '/images/signup/role-escort-photo.jpg',
    imageAlt: '병원 밖에서 어르신과 함께 걷는 동행 매니저',
    cta: '동행 매니저로 가입하기',
    variant: 'ghost',
    href: '/signup/terms?role=ESCORT',
    benefits: [
      '원하는 공고를 찾아 지원할 수 있어요',
      '내 일정에 맞춰 자유롭게 활동할 수 있어요',
      '다양한 경험과 리뷰를 통해 신뢰받을 수 있어요',
    ],
  },
];
