export async function setSquawk(
  callsign: string,
  squawk: string
): Promise<boolean> {
  try {
    const response = await fetch(
      `/api/aircraft/${encodeURIComponent(callsign)}/squawk`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ squawk }),
      }
    );
    return response.ok;
  } catch {
    return false;
  }
}
