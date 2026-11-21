import { POST } from "./route";
import { proxyToBackend } from "@/utility/api/backendProxy";

jest.mock("@/utility/api/backendProxy");

const mockedProxyToBackend = proxyToBackend as jest.MockedFunction<
  typeof proxyToBackend
>;

// Create a mock Response that behaves like actual text/plain (json() throws for non-JSON text)
class TextPlainResponse extends Response {
  private textContent: string;

  constructor(text: string, init: ResponseInit) {
    super(text, init);
    this.textContent = text;
  }

  async text(): Promise<string> {
    return this.textContent;
  }

  async json(): Promise<any> {
    // text/plain that's not JSON will throw
    try {
      return JSON.parse(this.textContent);
    } catch (e) {
      throw new SyntaxError(
        `Unexpected token '${this.textContent.charAt(0)}', "${this.textContent.substring(0, 20)}"... is not valid JSON`
      );
    }
  }
}

describe("POST /api/aircraft/[callsign]/squawk", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("returns 200 with {message} when backend returns text/plain 200", async () => {
    mockedProxyToBackend.mockResolvedValue(
      new TextPlainResponse("Squawk assigned: ANA404", {
        status: 200,
        headers: { "content-type": "text/plain" },
      }) as Response
    );

    const request = {
      json: async () => ({ squawk: "1234" }),
    } as unknown as Request;

    const context = { params: Promise.resolve({ callsign: "ANA404" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(200);
    const text = await response.text();
    const json = JSON.parse(text);
    expect(json).toEqual({ message: "Squawk assigned: ANA404" });
    expect(mockedProxyToBackend).toHaveBeenCalledWith(
      "/api/aircraft/ANA404/squawk",
      expect.objectContaining({
        method: "POST",
      })
    );
  });

  it("returns 404 with {error} when backend returns text/plain 404", async () => {
    mockedProxyToBackend.mockResolvedValue(
      new TextPlainResponse("Aircraft not found", {
        status: 404,
        headers: { "content-type": "text/plain" },
      }) as Response
    );

    const request = {
      json: async () => ({ squawk: "7700" }),
    } as any;

    const context = { params: Promise.resolve({ callsign: "UNKNOWN" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(404);
    const text = await response.text();
    const json = JSON.parse(text);
    expect(json).toEqual({ error: "Aircraft not found" });
  });

  it("returns 400 for invalid squawk without calling backend", async () => {
    const request = {
      json: async () => ({ squawk: "8888" }),
    } as any;

    const context = { params: Promise.resolve({ callsign: "TEST" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(400);
    const text = await response.text();
    const json = JSON.parse(text);
    expect(json).toEqual({ error: "squawk must be 4 octal digits (0-7)" });
    expect(mockedProxyToBackend).not.toHaveBeenCalled();
  });

  it("returns 400 for malformed JSON without calling backend", async () => {
    const request = {
      json: async () => {
        throw new Error("Invalid JSON");
      },
    } as any;

    const context = { params: Promise.resolve({ callsign: "TEST" }) };
    const response = await POST(request, context);

    expect(response.status).toBe(400);
    const text = await response.text();
    const json = JSON.parse(text);
    expect(json).toEqual({ error: "Invalid JSON" });
    expect(mockedProxyToBackend).not.toHaveBeenCalled();
  });
});
