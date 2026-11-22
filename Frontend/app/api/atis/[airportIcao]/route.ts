import { proxyToBackend } from "@/utility/api/backendProxy";

export const dynamic = "force-dynamic";

export async function PUT(
  request: Request,
  context: { params: Promise<{ airportIcao: string }> }
) {
  const { airportIcao } = await context.params;
  const body = await request.text();
  return proxyToBackend(`/api/atis/${airportIcao}`, {
    method: "PUT",
    headers: {
      Accept: request.headers.get("Accept") ?? "application/json",
      "Content-Type": request.headers.get("Content-Type") ?? "application/json",
    },
    body,
    cache: "no-store",
  });
}
