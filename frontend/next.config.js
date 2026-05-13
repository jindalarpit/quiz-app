/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone', // Required for Docker/Podman deployment
  reactStrictMode: true,
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '**',
      },
    ],
  },
  // Security headers
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: "default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' ws: wss: http://localhost:*; font-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self';",
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'X-XSS-Protection',
            value: '1; mode=block',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()',
          },
        ],
      },
    ];
  },
  // Proxy API requests to backend services in development
  async rewrites() {
    return [
      {
        source: '/api/auth/:path*',
        destination: 'http://localhost:8080/api/auth/:path*',
      },
      {
        source: '/api/quizzes/:path*',
        destination: 'http://localhost:8081/api/quizzes/:path*',
      },
      {
        source: '/api/sessions/:path*',
        destination: 'http://localhost:8082/api/sessions/:path*',
      },
      {
        source: '/api/analytics/:path*',
        destination: 'http://localhost:8083/api/analytics/:path*',
      },
      {
        source: '/ws/:path*',
        destination: 'http://localhost:8084/ws/:path*',
      },
    ];
  },
};

module.exports = nextConfig;
