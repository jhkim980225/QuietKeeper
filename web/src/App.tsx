import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { GlassCard } from './components/GlassCard'
import { isMockMode } from './data'
import { Calendar } from './views/Calendar'
import { Dashboard } from './views/Dashboard'
import { Report } from './views/Report'

function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? 'navlink active' : 'navlink'
}

export default function App() {
  return (
    <div className="app-shell">
      <GlassCard className="topnav">
        <span className="brand">
          <span className="dot" />
          QuietKeeper
        </span>
        <NavLink to="/" end className={navClass}>
          대시보드
        </NavLink>
        <NavLink to="/calendar" className={navClass}>
          캘린더
        </NavLink>
        <NavLink to="/report" className={navClass}>
          레포트
        </NavLink>
      </GlassCard>

      {isMockMode && (
        <div className="mock-banner">
          데모 모드 (Firebase 미설정) — 목 데이터 표시 중
        </div>
      )}

      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/calendar" element={<Calendar />} />
        <Route path="/report" element={<Report />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
