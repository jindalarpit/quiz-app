'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { useAuthStore } from '@/stores/authStore';

export function Header() {
  const pathname = usePathname();
  const { user, logout } = useAuthStore();

  // Hide header on session pages (host/participant views)
  const hideOnPaths = ['/sessions/', '/join'];
  const shouldHide = hideOnPaths.some((p) => pathname?.startsWith(p) && pathname !== '/join');

  if (shouldHide) return null;

  return (
    <header className="border-b border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href="/" className="text-xl font-bold text-primary-600">
          QuizPlatform
        </Link>

        <nav className="flex items-center gap-2 sm:gap-4">
          <ThemeToggle />
          {user ? (
            <>
              <Link
                href="/dashboard"
                className="min-h-[44px] min-w-[44px] flex items-center justify-center text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
              >
                Dashboard
              </Link>
              <button
                onClick={logout}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link
                href="/login"
                className="min-h-[44px] min-w-[44px] flex items-center justify-center text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
              >
                Login
              </Link>
              <Link
                href="/register"
                className="btn-primary min-h-[44px] min-w-[44px] flex items-center justify-center text-sm"
              >
                Sign Up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
