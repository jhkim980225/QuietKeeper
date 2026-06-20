interface AudioPlayerStubProps {
  url?: string
}

/**
 * Placeholder audio player. The device's local web server (which streams the
 * captured WAV over the home LAN) isn't built yet, so this is a no-op stub.
 * When that server exists, swap this for a real <audio src={url} controls />.
 */
export function AudioPlayerStub({ url }: AudioPlayerStubProps) {
  return (
    <div className="audio-stub" title={url}>
      <span aria-hidden>🔊</span>
      <span>기기 로컬 네트워크 연결 시 재생</span>
    </div>
  )
}
