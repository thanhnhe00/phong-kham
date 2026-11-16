import { useState, type FormEvent } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Icon, type IconName } from '../components/icons';
import { useAuth } from '../lib/auth';
import { initials, longDate, vnDate } from '../lib/format';
import type { StaffRole } from '../lib/types';

interface Item { to: string; icon: IconName; label: string; end?: boolean }

export const MENU: Record<StaffRole, { label: string; items: Item[] }> = {
  RECEPTIONIST: {
    label: 'Lễ tân',
    items: [
      { to: '/le-tan', icon: 'dash', label: 'Tổng quan', end: true },
      { to: '/le-tan/lich-hen', icon: 'cal', label: 'Lịch hẹn' },
      { to: '/le-tan/tiep-don', icon: 'useradd', label: 'Tiếp đón' },
      { to: '/le-tan/hang-doi', icon: 'list', label: 'Hàng đợi khám' },
      { to: '/le-tan/thu-ngan', icon: 'card', label: 'Thu ngân' },
    ],
  },
  NURSE: {
    label: 'Điều dưỡng',
    items: [
      { to: '/dieu-duong/sinh-hieu', icon: 'heart', label: 'Đo sinh hiệu' },
      { to: '/dieu-duong/xet-nghiem', icon: 'flask', label: 'Xét nghiệm' },
    ],
  },
  DOCTOR: {
    label: 'Bác sĩ',
    items: [
      { to: '/bac-si', icon: 'list', label: 'Khám bệnh', end: true },
      { to: '/bac-si/duyet-ket-qua', icon: 'flask', label: 'Duyệt kết quả' },
      { to: '/bac-si/lich', icon: 'cal', label: 'Lịch làm việc' },
    ],
  },
  ADMIN: {
    label: 'Quản lý',
    items: [
      { to: '/quan-ly/bao-cao', icon: 'chart', label: 'Báo cáo' },
      { to: '/quan-ly/lich', icon: 'cal', label: 'Lịch làm việc' },
      { to: '/quan-ly/nhan-vien', icon: 'users', label: 'Nhân viên' },
      { to: '/quan-ly/thu-chi', icon: 'card', label: 'Thu chi' },
      { to: '/quan-ly/danh-muc', icon: 'box', label: 'Danh mục' },
      { to: '/quan-ly/phan-hoi', icon: 'bell', label: 'Phản hồi' },
    ],
  },
};
const ORDER: StaffRole[] = ['RECEPTIONIST', 'NURSE', 'DOCTOR', 'ADMIN'];

export const staffRoles = (roles: string[]) => ORDER.filter((r) => roles.includes(r));

/** Trang đầu tiên sau đăng nhập theo vai */
export function homeOf(roles: string[]) {
  const r = staffRoles(roles);
  if (r.includes('ADMIN')) return '/quan-ly/bao-cao';
  if (r.includes('DOCTOR')) return '/bac-si';
  if (r.includes('NURSE')) return '/dieu-duong/sinh-hieu';
  return '/le-tan';
}

export function Shell() {
  const { me, logout } = useAuth();
  const nav = useNavigate();
  const [q, setQ] = useState('');
  const roles = staffRoles(me?.roles ?? []);

  const search = (e: FormEvent) => {
    e.preventDefault();
    if (q.trim()) nav(`/benh-nhan?q=${encodeURIComponent(q.trim())}`);
  };

  return (
    <div className="app">
      <nav className="side" aria-label="Điều hướng">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true"><Icon name="plus" size={20} stroke={2.2} /></span>
          <span><span className="brand-name">Phòng khám An Tâm</span><br /><span className="brand-role">{roles.map((r) => MENU[r].label).join(', ')}</span></span>
        </div>
        {roles.map((r) => (
          <div className="nav" key={r}>
            {roles.length > 1 && <span className="xs" style={{ color: '#9cc7bd', padding: '0 12px 4px' }}>{MENU[r].label}</span>}
            {MENU[r].items.map((it) => (
              <NavLink key={it.to} to={it.to} end={it.end} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                <Icon name={it.icon} /><span className="label">{it.label}</span>
              </NavLink>
            ))}
          </div>
        ))}
        <div className="nav">
          <NavLink to="/benh-nhan" className={({ isActive }) => (isActive ? 'active' : undefined)}><Icon name="doc" /><span className="label">Bệnh nhân</span></NavLink>
        </div>
        <div className="me">
          <span className="avatar" aria-hidden="true">{initials(me?.fullName)}</span>
          <span style={{ minWidth: 0 }}><span className="me-name">{me?.fullName}</span><br /><span className="me-sub">{me?.phone}</span></span>
          <button type="button" aria-label="Đổi mật khẩu" title="Đổi mật khẩu" onClick={() => nav('/doi-mat-khau')}><Icon name="key" /></button>
          <button type="button" aria-label="Đăng xuất" title="Đăng xuất" onClick={logout} style={{ marginLeft: 0 }}><Icon name="logout" /></button>
        </div>
      </nav>
      <main className="main">
        <header className="top">
          <form className="search" onSubmit={search} role="search">
            <Icon name="search" />
            <input type="search" aria-label="Tìm bệnh nhân" placeholder="Tìm theo tên, số điện thoại, mã bệnh nhân" value={q} onChange={(e) => setQ(e.target.value)} />
          </form>
          <span className="muted">{longDate(vnDate())}</span>
        </header>
        <Outlet />
      </main>
    </div>
  );
}
