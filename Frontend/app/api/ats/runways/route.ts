import { proxyToBackend } from "@/utility/api/backendProxy";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const airport = url.searchParams.get("airport");
  
  if (airport && airport.length > 0) {
    return proxyToBackend(`/ats/runways?airport=${encodeURIComponent(airport)}`, {
      method: "GET",
      headers: { Accept: "application/json" },
    });
  }
  
  return proxyToBackend("/ats/runways", {
    method: "GET",
    headers: { Accept: "application/json" },
  });
}
