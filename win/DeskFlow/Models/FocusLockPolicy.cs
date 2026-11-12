using System;
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

    /// <summary>
    /// Decides whether a polled backend state should be applied.
    ///
    /// A status request fired just before the unlock can land afterwards and
    /// still report an active session. Applying it would re-lock the window the
    /// user just unlocked, which is the "solved the maths but cannot leave"
    /// bug. While a suppression window is open we ignore "active" responses;
    /// de-activating responses are always honoured so the window can never get
    /// stuck locked.
    /// </summary>
    public static bool ShouldApplyPolledState(bool backendActive, DateTime nowUtc, DateTime suppressRelockUntilUtc)
    {
        if (!backendActive)
            return true;

        return nowUtc >= suppressRelockUntilUtc;
    }

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
