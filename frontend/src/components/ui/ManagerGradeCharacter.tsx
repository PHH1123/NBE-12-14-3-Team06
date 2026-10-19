import type { EscortGrade } from '@/features/application';

type IllustrationProps = { className?: string; bare?: boolean };

const GajiSeedIllustration = ({ className = "w-10 h-10 shrink-0", bare = false }: IllustrationProps) => (
  <svg
    viewBox={bare ? "15 10 18 28" : "0 0 48 48"}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    aria-label="씨앗 캐릭터"
  >
    {/* Soft neutral sage container */}
    {!bare && <rect width="48" height="48" rx="12" fill="#F4F6F3" />}
    {!bare && <circle cx="24" cy="24" r="16" fill="#E6EDE4" />}
    {/* Subtle earthen mound */}
    {!bare && <ellipse cx="24" cy="35" rx="10" ry="2.5" fill="#DDD7CE" />}
    {/* Soft sage seed body */}
    <path
      d="M24 16C19.8 20.2 18 25 18 28.5C18 32 20.7 34.5 24 34.5C27.3 34.5 30 32 30 28.5C30 25 28.2 20.2 24 16Z"
      fill="#5E7D68"
    />
    {/* Delicate sage micro sprout */}
    <path
      d="M24 16C22.5 13 20 11.5 18.5 12C20.5 13.8 22.8 15.2 24 16Z"
      fill="#88A891"
    />
    <path
      d="M24 16C25.2 13.5 27 12.8 28.5 13.2C27.2 14.6 25.5 15.5 24 16Z"
      fill="#75987E"
    />
    {/* Friendly eyes */}
    <circle cx="21.5" cy="27.5" r="1.3" fill="#FFFFFF" />
    <circle cx="21.5" cy="27.5" r="0.6" fill="#2B3B30" />
    <circle cx="26.5" cy="27.5" r="1.3" fill="#FFFFFF" />
    <circle cx="26.5" cy="27.5" r="0.6" fill="#2B3B30" />
    {/* Warm smile */}
    <path
      d="M22.5 30.5C23.5 31.5 24.5 31.5 25.5 30.5"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    {/* Soft cream-peach cheeks */}
    <circle cx="19.8" cy="29.2" r="1" fill="#D4B9A7" opacity="0.65" />
    <circle cx="28.2" cy="29.2" r="1" fill="#D4B9A7" opacity="0.65" />
  </svg>
);

const GajiSproutIllustration = ({ className = "w-10 h-10 shrink-0", bare = false }: IllustrationProps) => (
  <svg
    viewBox={bare ? "12 10 24 29" : "0 0 48 48"}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    aria-label="새싹 캐릭터"
  >
    {/* Muted natural green container */}
    {!bare && <rect width="48" height="48" rx="12" fill="#F0F5F1" />}
    {!bare && <circle cx="24" cy="24" r="16" fill="#DFECE1" />}
    {/* Stem in natural green */}
    <path
      d="M24 36V22"
      stroke="#486D51"
      strokeWidth="2.8"
      strokeLinecap="round"
    />
    {/* Left leaf */}
    <path
      d="M23 25C17 25 14 19 15 14C19 14 23 19 23 25Z"
      fill="#587F61"
    />
    {/* Right leaf */}
    <path
      d="M25 23C31 23 34 17 33 12C29 12 25 17 25 23Z"
      fill="#6B9475"
    />
    {/* Sprout face */}
    <circle cx="24" cy="27.2" r="6" fill="#486D51" />
    <circle cx="22" cy="26.2" r="1.1" fill="#FFFFFF" />
    <circle cx="22" cy="26.2" r="0.55" fill="#1E3324" />
    <circle cx="26" cy="26.2" r="1.1" fill="#FFFFFF" />
    <circle cx="26" cy="26.2" r="0.55" fill="#1E3324" />
    <path
      d="M22.5 28.7C23.5 29.7 24.5 29.7 25.5 28.7"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    <circle cx="20.8" cy="27.7" r="0.8" fill="#A1C6A9" opacity="0.75" />
    <circle cx="27.2" cy="27.7" r="0.8" fill="#A1C6A9" opacity="0.75" />
  </svg>
);

const GajiFlowerIllustration = ({ className = "w-10 h-10 shrink-0", bare = false }: IllustrationProps) => (
  <svg
    viewBox={bare ? "10 9 28 27" : "0 0 48 48"}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    aria-label="꽃 캐릭터"
  >
    {/* Soft dusty lavender container */}
    {!bare && <rect width="48" height="48" rx="12" fill="#F5F2F8" />}
    {!bare && <circle cx="24" cy="24" r="16" fill="#EAE4F0" />}
    {/* Petals in dusty muted purple */}
    <circle cx="24" cy="14.5" r="4.8" fill="#A193B2" />
    <circle cx="32.5" cy="20.5" r="4.8" fill="#A193B2" />
    <circle cx="30.5" cy="29.5" r="4.8" fill="#A193B2" />
    <circle cx="17.5" cy="29.5" r="4.8" fill="#A193B2" />
    <circle cx="15.5" cy="20.5" r="4.8" fill="#A193B2" />
    {/* Center character face */}
    <circle cx="24" cy="23" r="7.2" fill="#5E4E70" />
    {/* Happy gentle eyes */}
    <path
      d="M21 21.5C21.5 20.8 22.5 20.8 23 21.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    <path
      d="M25 21.5C25.5 20.8 26.5 20.8 27 21.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    {/* Smile */}
    <path
      d="M22.5 24.5C23.5 25.6 24.5 25.6 25.5 24.5"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    {/* Cheeks */}
    <circle cx="20.5" cy="23.5" r="0.9" fill="#C4B5D1" opacity="0.8" />
    <circle cx="27.5" cy="23.5" r="0.9" fill="#C4B5D1" opacity="0.8" />
  </svg>
);

const GajiEggplantIllustration = ({ className = "w-10 h-10 shrink-0", bare = false }: IllustrationProps) => (
  <svg
    viewBox={bare ? "13 5 22 35" : "0 0 48 48"}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    aria-label="가지 캐릭터"
  >
    {/* Soft neutral-lavender container ensuring high contrast and visibility */}
    {!bare && <rect width="48" height="48" rx="12" fill="#F0EEF5" />}
    {!bare && <circle cx="24" cy="24" r="16" fill="#E2DEEC" />}
    {/* Deep muted eggplant purple body */}
    <path
      d="M24 15C18.5 15 15 22 15 28C15 34 18.8 38 24 38C29.2 38 33 34 33 28C33 22 29.5 15 24 15Z"
      fill="#3E3452"
    />
    {/* Muted olive-sage calyx (green cap) */}
    <path
      d="M24 9.5C24 11.5 24.5 13.5 24 15C23 14 20 14 17.5 16C19.2 14.5 21 13.5 22 13C21 12 20 10.5 20 9.5C21.5 10.5 23 11.5 24 12C25 11.5 26.5 10.5 28 9.5C28 10.5 27 12 26 13C27 13.5 28.8 14.5 30.5 16C28 14 25 14 24 15Z"
      fill="#52745D"
    />
    {/* Stem tip */}
    <path
      d="M24 10C24 7.8 25 7 26 6.5"
      stroke="#52745D"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
    {/* Highly legible face */}
    <circle cx="20.5" cy="23.5" r="1.3" fill="#FFFFFF" />
    <circle cx="20.5" cy="23.5" r="0.6" fill="#1C1824" />
    <circle cx="27.5" cy="23.5" r="1.3" fill="#FFFFFF" />
    <circle cx="27.5" cy="23.5" r="0.6" fill="#1C1824" />
    {/* Friendly smile */}
    <path
      d="M22 26.5C23.2 27.8 24.8 27.8 26 26.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    <circle cx="19.2" cy="25.2" r="0.9" fill="#947E9B" opacity="0.75" />
    <circle cx="28.8" cy="25.2" r="0.9" fill="#947E9B" opacity="0.75" />
    {/* Refined antique gold master emblem star */}
    <polygon
      points="24,31 25.2,33.2 27.5,33.5 25.8,35.1 26.3,37.3 24,36.1 21.7,37.3 22.2,35.1 20.5,33.5 22.8,33.2"
      fill="#C89B3C"
    />
  </svg>
);

type ManagerGradeCharacterProps = {
  grade: EscortGrade;
  className?: string;
  bare?: boolean;
};

/** Render grade characters with or without their decorative tile background. */
export default function ManagerGradeCharacter({ grade, className, bare = false }: ManagerGradeCharacterProps) {
  switch (grade) {
    case 'SEED': return <GajiSeedIllustration className={className} bare={bare} />;
    case 'SPROUT': return <GajiSproutIllustration className={className} bare={bare} />;
    case 'FLOWER': return <GajiFlowerIllustration className={className} bare={bare} />;
    case 'EGGPLANT': return <GajiEggplantIllustration className={className} bare={bare} />;
  }
}
