"""Windows endpoint volume via Core Audio; never starts PowerShell.

COM must be initialized in the HTTP handler's own thread.
Returns unavailable, rather than pretending a media-key press changed volume.
"""

def _endpoint():
    import comtypes
    from comtypes import CLSCTX_ALL, POINTER, cast
    from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
    comtypes.CoInitialize()
    try:
        speakers = AudioUtilities.GetSpeakers()
        endpoint = getattr(speakers, "EndpointVolume", None)
        if endpoint is None:
            endpoint = cast(speakers.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None),
                            POINTER(IAudioEndpointVolume))
        return endpoint
    except Exception:
        comtypes.CoUninitialize()
        raise


def normalize_percent(value):
    number = float(value)
    if not 0.0 <= number <= 100.0:
        raise ValueError("Volume must be between 0 and 100")
    return number / 100.0


def audio_state():
    import comtypes
    endpoint = _endpoint()
    try:
        return {"available": True, "volume": round(float(endpoint.GetMasterVolumeLevelScalar()) * 100),
                "muted": bool(endpoint.GetMute())}
    finally:
        del endpoint
        comtypes.CoUninitialize()


def set_audio(percent=None, muted=None):
    import comtypes
    if percent is None and muted is None:
        raise ValueError("No volume change supplied")
    level = None if percent is None else normalize_percent(percent)
    if muted is not None and not isinstance(muted, bool):
        raise ValueError("Muted must be true or false")
    endpoint = _endpoint()
    try:
        if level is not None:
            endpoint.SetMasterVolumeLevelScalar(level, None)
        if muted is not None:
            endpoint.SetMute(bool(muted), None)
        return {"available": True, "volume": round(float(endpoint.GetMasterVolumeLevelScalar()) * 100),
                "muted": bool(endpoint.GetMute())}
    finally:
        del endpoint
        comtypes.CoUninitialize()
