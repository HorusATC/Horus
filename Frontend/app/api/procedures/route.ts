import { proxyToBackend } from "@/utility/api/backendProxy";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const airport = searchParams.get("airport");
  const query =
    airport != null && airport !== ""
      ? `airport=${encodeURIComponent(airport)}`
      : "";
  return proxyToBackend(`/api/procedures?${query}`, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
}
