'use client';

import React, { useState } from 'react';

interface QuestionImageProps {
  mediaUrl: string;
}

export function QuestionImage({ mediaUrl }: QuestionImageProps) {
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);

  if (error) {
    return null;
  }

  return (
    <div className="mb-4 w-full">
      {!loaded && (
        <div className="h-[200px] w-full animate-pulse rounded-lg bg-slate-200 dark:bg-slate-700" />
      )}
      <img
        src={mediaUrl}
        alt="Question image"
        className="max-h-[400px] w-full object-contain"
        style={!loaded ? { display: 'none' } : undefined}
        onLoad={() => setLoaded(true)}
        onError={() => setError(true)}
      />
    </div>
  );
}
