import axios from "axios";
import { clearStoredAuthentication, readAccessToken } from "../auth/session";

export function createApiClient(options = {}) {
  const client = axios.create({
    baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
    timeout: 15000,
    ...options,
  });

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const config = error.config;
      const authorization = config?.headers?.Authorization;
      const token = authorization?.startsWith("Bearer ")
        ? authorization.slice(7)
        : null;
      if (error.response?.status === 401 && token) {
        // 이전 요청의 늦은 응답이 새로 로그인한 사용자를 로그아웃시키지 않도록 합니다.
        if (token === readAccessToken()) clearStoredAuthentication();
        // 공개 조회만 비로그인으로 한 번 재시도합니다. 쓰기 요청은 반복하지 않습니다.
        if (
          config.optionalAuth &&
          config.method === "get" &&
          !config.anonymousRetry
        ) {
          const headers = axios.AxiosHeaders.from(config.headers);
          headers.delete("Authorization");
          return client.request({ ...config, headers, anonymousRetry: true });
        }
      }
      return Promise.reject(error);
    },
  );

  return client;
}
