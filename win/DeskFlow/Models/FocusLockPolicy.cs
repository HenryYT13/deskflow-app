using System.Text.Json;

namespace DeskFlow.Models;

/// <summary>
/// Pure rules for the Focus Mode lock. Kept free of WinUI types so the unit
/// tests can exercise the behaviour without a desktop session.
/// </summary>
public static class FocusLockPolicy
{
    /// <summary>
    /// Reads <c>/api/focus/status</c> and decides whether Focus Mode is active.
    /// The backend has used both <c>focus_active</c> and <c>enabled</c> over time.
    /// </summary>
    public static bool IsFocusActive(string? json)
    {
        if (string.IsNullOrWhiteSpace(json))
            return false;

        try
        {
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.ValueKind != JsonValueKind.Object)
                return false;

            return ReadTrue(doc.RootElement, "focus_active")
                || ReadTrue(doc.RootElement, "enabled");
        }
        catch (JsonException)
        {
            return false;
        }
    }

    /// <summary>While locked the window must refuse every close request.</summary>
    public static bool ShouldBlockClose(bool focusLocked) => focusLocked;

    private static bool ReadTrue(JsonElement root, string property)
    {
        if (!root.TryGetProperty(property, out var value))
            return false;

        return value.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.Number => value.TryGetInt32(out var number) && number != 0,
            JsonValueKind.String => bool.TryParse(value.GetString(), out var parsed) && parsed,
            _ => false
        };
    }
}
