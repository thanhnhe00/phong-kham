import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/icons';
import { Button, ErrorNote, Field, Note } from '../components/ui';
import { errorText } from '../lib/api';
import { useAuth } from '../lib/auth';

export function LoginPage() {
  const { login } = useAuth();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(phone, password);
    } catch (err) {
      setError(err instanceof Error && !('status' in err) ? err.message : errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <div className="login-box">
        <div className="wrap" style={{ justifyContent: 'center', gap: 12 }}>
          <span className="brand-mark" style={{ width: 40, height: 40, background: 'var(--brand)' }} aria-hidden="true"><Icon name="plus" size={22} stroke={2.2} /></span>
          <span style={{ fontWeight: 700, fontSize: 18 }}>Phòng khám An Tâm</span>
        </div>
        <form onSubmit={submit}>
          <div><h1 style={{ fontSize: 22 }}>Đăng nhập</h1><p className="muted" style={{ marginTop: 4 }}>Dành cho lễ tân, điều dưỡng, bác sĩ và quản lý</p></div>
          <Field label="Số điện thoại" inputMode="tel" autoComplete="username" value={phone} onChange={(e) => setPhone(e.target.value)} required autoFocus />
          <Field label="Mật khẩu" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <ErrorNote text={error} />
          <Button type="submit" big loading={busy}>Đăng nhập</Button>
          <p className="muted small">Quên mật khẩu? Báo quản lý cấp mật khẩu tạm. Đăng nhập bằng mật khẩu tạm thì phải đổi mật khẩu mới trước khi làm việc.</p>
        </form>
        <p className="muted small" style={{ textAlign: 'center' }}>Bệnh nhân dùng ứng dụng trên điện thoại.</p>
      </div>
    </div>
  );
}

export function ChangePasswordPage({ forced }: { forced?: boolean }) {
  const { changePassword, logout } = useAuth();
  const nav = useNavigate();
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (next.length < 8) return setError('Mật khẩu mới cần ít nhất 8 ký tự.');
    if (next !== again) return setError('Hai lần nhập mật khẩu mới chưa khớp.');
    setBusy(true);
    setError(null);
    try {
      await changePassword(cur, next);
      setDone(true);
      if (!forced) setTimeout(() => nav(-1), 800);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const form = (
    <form onSubmit={submit} style={forced ? undefined : { background: '#fff', border: '1px solid var(--line)', borderRadius: 14, padding: 28, display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 420 }}>
      <div>
        <h1 style={{ fontSize: 22 }}>Đổi mật khẩu</h1>
        {forced && <p className="muted" style={{ marginTop: 4 }}>Bạn đang dùng mật khẩu tạm. Đặt mật khẩu mới để bắt đầu làm việc.</p>}
      </div>
      <Field label={forced ? 'Mật khẩu tạm' : 'Mật khẩu hiện tại'} type="password" autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} required />
      <Field label="Mật khẩu mới (ít nhất 8 ký tự)" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required />
      <Field label="Nhập lại mật khẩu mới" type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required />
      <ErrorNote text={error} />
      {done && <Note tone="teal" icon="check">Đã đổi mật khẩu. Các máy khác đang đăng nhập tài khoản này sẽ bị đăng xuất.</Note>}
      <Button type="submit" big loading={busy}>Đổi mật khẩu</Button>
      {forced && <button type="button" className="link" onClick={logout}>Đăng xuất</button>}
    </form>
  );
  return forced ? <div className="login"><div className="login-box">{form}</div></div> : <div className="page">{form}</div>;
}
