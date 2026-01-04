'use client';

import { useState } from 'react';

interface JsonPreviewProps {
  data: unknown;
  title?: string;
  collapsible?: boolean;
  defaultExpanded?: boolean;
}

/**
 * JsonPreview - Component for displaying JSON data with copy functionality
 */
export function JsonPreview({
  data,
  title = 'JSON Preview',
  collapsible = true,
  defaultExpanded = false,
}: JsonPreviewProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [copied, setCopied] = useState(false);

  const jsonString = JSON.stringify(data, null, 2);

  const copyToClipboard = async () => {
    try {
      await navigator.clipboard.writeText(jsonString);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const downloadJson = () => {
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'exercise-config.json';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  if (collapsible) {
    return (
      <div className="bg-gray-900 rounded-lg overflow-hidden">
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="w-full px-4 py-3 flex justify-between items-center text-sm font-medium text-gray-300 hover:bg-gray-800"
        >
          <span>{title}</span>
          <svg
            className={`w-5 h-5 transform transition-transform ${expanded ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        {expanded && (
          <div className="border-t border-gray-700">
            <div className="px-4 py-2 bg-gray-800 border-b border-gray-700 flex justify-end gap-2">
              <button
                type="button"
                onClick={copyToClipboard}
                className="text-sm text-blue-400 hover:text-blue-300 flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
                {copied ? 'Copied!' : 'Copy'}
              </button>
              <button
                type="button"
                onClick={downloadJson}
                className="text-sm text-green-400 hover:text-green-300 flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                </svg>
                Download
              </button>
            </div>
            <pre className="p-4 text-xs text-green-400 overflow-x-auto max-h-96 overflow-y-auto font-mono">
              {jsonString}
            </pre>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="bg-gray-900 rounded-lg overflow-hidden">
      <div className="px-4 py-2 bg-gray-800 border-b border-gray-700 flex justify-between items-center">
        <span className="text-sm font-medium text-gray-300">{title}</span>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={copyToClipboard}
            className="text-sm text-blue-400 hover:text-blue-300 flex items-center gap-1"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            {copied ? 'Copied!' : 'Copy'}
          </button>
          <button
            type="button"
            onClick={downloadJson}
            className="text-sm text-green-400 hover:text-green-300 flex items-center gap-1"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Download
          </button>
        </div>
      </div>
      <pre className="p-4 text-xs text-green-400 overflow-x-auto max-h-96 overflow-y-auto font-mono">
        {jsonString}
      </pre>
    </div>
  );
}

export default JsonPreview;

