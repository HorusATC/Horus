import { NextRequest, NextResponse } from "next/server";

const BACKEND_BASE_URL =
  process.env.BACKEND_BASE_URL || "http://localhost:8080";

function isValidSquawk(squawk: unknown): squawk is string {
  return typeof squawk === "string" && /^[0-7]{4}$/.test(squawk);
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ callsign: string }> }
): Promise<NextResponse> {
  const { callsign } = await context.params;

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON" }, { status: 400 });
  }

  if (
    !body ||
    typeof body !== "object" ||
    !("squawk" in body) ||
    !isValidSquawk(body.squawk)
  ) {
    return NextResponse.json(
      { error: "squawk must be 4 octal digits (0-7)" },
      { status: 400 }
    );
  }

  const backendUrl = `${BACKEND_BASE_URL}/api/aircraft/${encodeURIComponent(callsign)}/squawk`;

  const backendResponse = await fetch(backendUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ squawk: body.squawk }),
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
