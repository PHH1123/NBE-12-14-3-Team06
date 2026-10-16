import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  /**
   * Next 서버의 gzip 압축을 끕니다.
   *
   * 알림 SSE(/api/v1/notification/connect)는 rewrite 로 8080 에 프록시되는데, 브라우저가
   * Accept-Encoding: gzip 을 보내면 Next 가 그 스트리밍 응답까지 gzip 으로 묶습니다.
   * 그러면 이벤트가 압축 버퍼에 갇혀 EventSource 가 하나도 받지 못합니다
   * (readyState 는 1(열림)이라 겉보기엔 멀쩡해서 찾기 어렵습니다).
   *
   * compression 미들웨어는 Cache-Control 의 no-transform 을 보면 압축을 건너뛰지만,
   * next.config 의 headers() 는 rewrite 로 프록시되는 경로에는 적용되지 않아 그 길이 막혀 있습니다.
   * 배포 때 앞단(nginx 등)에서 압축한다면 이 설정이 손해가 아니고,
   * 그렇지 않다면 SSE 만 압축에서 빼 주도록 앞단에서 처리하세요.
   */
  compress: false,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL}/api/:path*`,
        //예를 들면 브라우저가 /api/v1/posts를 부르면, Next가 http://localhost:8080/api/v1/posts로 대신 전달
      },
    ];
  },
};

export default nextConfig;