import { NextRequest } from "next/server";
import { POST } from "./route";
import { proxyToBackend } from "@/utility/api/backendProxy";

jest.mock("@/utility/api/backendProxy");

const mockedProxyToBackend = proxyToBackend as jest.MockedFunction<
  typeof proxyToBackend
>;

describe("POST /api/aircraft/[callsign]/squawk", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("returns 200 with {message} when backend returns text/plain 200", async () => {
    mockedProxyToBackend.mockResolvedValue(
      new Response("Squawk assigned: ANA404", {
        status: 200,
        headers: { "Content-Type": "text/plain" },
      })
    );

    const request = new NextRequest("http://localhost:3000/api/aircraft/ANA404/squawk", {
      method: "POST",
      body: JSON.stringify({ squawk: "1234" }),
    });

    const context = { params: Promise.resolve({ callsign: "ANA404" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(200);
    const json = await response.json();
    expect(json).toEqual({ message: "Squawk assigned: ANA404" });
  });

  it("returns error JSON with proper status on backend error", async () => {
    mockedProxyToBackend.mockResolvedValue(
      new Response("Aircraft not found", {
        status: 404,
        headers: { "Content-Type": "text/plain" },
      })
    );

    const request = new NextRequest("http://localhost:3000/api/aircraft/UNKNOWN/squawk", {
      method: "POST",
      body: JSON.stringify({ squawk: "7700" }),
    });

    const context = { params: Promise.resolve({ callsign: "UNKNOWN" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(404);
    const json = await response.json();
    expect(json).toEqual({ error: "Aircraft not found" });
  });

  it("returns 400 for invalid squawk (non-octal)", async () => {
    const request = new NextRequest("http://localhost:3000/api/aircraft/TEST/squawk", {
      method: "POST",
      body: JSON.stringify({ squawk: "8888" }),
    });

    const context = { params: Promise.resolve({ callsign: "TEST" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(400);
    const json = await response.json();
    expect(json).toEqual({ error: "squawk must be 4 octal digits (0-7)" });
    expect(mockedProxyToBackend).not.toHaveBeenCalled();
  });

  it("returns 400 for missing squawk", async () => {
    const request = new NextRequest("http://localhost:3000/api/aircraft/TEST/squawk", {
      method: "POST",
      body: JSON.stringify({}),
    });

    const context = { params: Promise.resolve({ callsign: "TEST" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(400);
    const json = await response.json();
    expect(json).toEqual({ error: "squawk must be 4 octal digits (0-7)" });
    expect(mockedProxyToBackend).not.toHaveBeenCalled();
  });

  it("returns 400 for malformed JSON", async () => {
    const request = new NextRequest("http://localhost:3000/api/aircraft/TEST/squawk", {
      method: "POST",
      body: "not json",
    });

    const context = { params: Promise.resolve({ callsign: "TEST" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(400);
    const json = await response.json();
    expect(json).toEqual({ error: "Invalid JSON" });
    expect(mockedProxyToBackend).not.toHaveBeenCalled();
  });
});
