import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE_URL =
  process.env.BACKEND_BASE_URL || "http://localhost:8080";

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ callsign: string }> }
): Promise<NextResponse> {
  const { callsign } = await context.params;
  const body = await request.json();

  const backendUrl = `${BACKEND_BASE_URL}/api/aircraft/${encodeURIComponent(callsign)}/squawk`;

  const backendResponse = await fetch(backendUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!backendResponse.ok) {
    const errorText = await backendResponse.text();
    return NextResponse.json(
      { error: errorText || "Backend error" },
      { status: backendResponse.status }
    );
  }

  const result = await backendResponse.text();
  return NextResponse.json({ message: result });
}
