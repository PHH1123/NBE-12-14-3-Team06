'use client';

import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, Check } from 'lucide-react';
import type { RoleOption } from '../types';

type Props = { option: RoleOption };

export default function RoleCard({ option }: Props) {
  return (
    <article className="group flex min-w-0 flex-col justify-between space-y-6 rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs transition-[border-color,box-shadow] duration-300 hover:border-[#A6B2C2] hover:shadow-[0_3px_9px_rgba(30,39,57,0.10)] sm:p-8">
      <div className="space-y-4">
        <div className="relative aspect-[16/10] w-full overflow-hidden rounded-xl border border-[#E1E5E8] bg-[#14243A]">
          <Image src={option.image} alt={option.imageAlt} fill sizes="(max-width:768px) 100vw, 400px" className="object-cover transition-transform duration-300 group-hover:scale-[1.02]" />
        </div>
        <div>
          <span className={`rounded bg-[#EDF0F2] px-2.5 py-0.5 text-xs font-semibold text-[#426B93]`}>
            {option.role === 'CLIENT' ? '의뢰인 회원' : '동행 매니저 회원'}
          </span>
          <h2 className="mt-2 text-xl font-semibold text-[#14243A]">{option.title}</h2>
        </div>
        <ul className="space-y-2 text-sm text-[#67717C]">
          {option.benefits.map((benefit) => (
            <li key={benefit} className="flex items-start gap-2">
              <Check className="mt-0.5 h-4 w-4 shrink-0 text-[#426B93]" aria-hidden="true" />
              <span>{benefit}</span>
            </li>
          ))}
        </ul>
      </div>
      <Link href={option.href} className="flex h-[48px] w-full items-center justify-center gap-2 rounded-[10px] bg-[#14243A] text-sm font-semibold text-white transition-colors duration-200 group-hover:bg-[#426B93] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]">
        <span>{option.cta}</span>
        <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />
      </Link>
    </article>
  );
}
