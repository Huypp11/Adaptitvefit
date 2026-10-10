import { isAxiosError } from 'axios';

export interface ApiErrorInfo {
  status?: number;
  message: string;
  fields: Record<string, string>;
}

export function parseError(e: unknown): ApiErrorInfo {
  if (isAxiosError(e)) {
    if (!e.response) {
      return { message: 'Không kết nối được máy chủ. Hãy kiểm tra backend đã chạy chưa.', fields: {} };
    }
    const data = e.response.data as { message?: string; fieldErrors?: Record<string, string> } | undefined;
    return {
      status: e.response.status,
      message: data?.message ?? 'Có lỗi xảy ra, vui lòng thử lại.',
      fields: data?.fieldErrors ?? {},
    };
  }
  return { message: 'Có lỗi xảy ra, vui lòng thử lại.', fields: {} };
}

/** Tra ve null neu server tra 404 (vi du chua co profile hoac chua check-in hom nay). */
export async function orNull<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch (e) {
    if (isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}
