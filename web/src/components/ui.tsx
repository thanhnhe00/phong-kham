import { createContext, useCallback, useContext, useState, type ButtonHTMLAttributes, type ComponentProps, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { Icon, type IconName } from './icons';

type BtnKind = 'primary' | 'outline' | 'ghost' | 'danger';
export function Button({ kind = 'primary', sm, big, icon, loading, children, className, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { kind?: BtnKind; sm?: boolean; big?: boolean; icon?: IconName; loading?: boolean }) {
  const cls = ['btn', kind !== 'primary' && kind, sm && 'sm', big && 'big', className].filter(Boolean).join(' ');
  return (
    <button type="button" className={cls} disabled={rest.disabled || loading} {...rest}>
      {icon && <Icon name={icon} />}
      {loading ? 'Đang xử lý…' : children}
    </button>
  );
}

export type Tone = 'grey' | 'teal' | 'amber' | 'red' | 'blue';
export const Chip = ({ tone = 'grey', children }: { tone?: Tone; children: ReactNode }) => <span className={`chip ${tone}`}>{children}</span>;

export function Card({ title, sub, right, sheet, stub, children, pad = true, className }: {
  title: ReactNode; sub?: ReactNode; right?: ReactNode; sheet?: boolean; stub?: number; children: ReactNode; pad?: boolean; className?: string;
}) {
  const head = stub != null ? (
    <div className="card-h with-stub">
      <Stub n={stub} />
      <div className="t"><h2>{title}</h2>{sub && <span className="sub">{sub}</span>}</div>
      {right && <div style={{ padding: '0 20px', alignSelf: 'center' }}>{right}</div>}
    </div>
  ) : (
    <div className="card-h">
      <div className="stack tight" style={{ gap: 2 }}><h2>{title}</h2>{sub && <span className="sub">{sub}</span>}</div>
      {right}
    </div>
  );
  return (
    <section className={['card', sheet && 'sheet', className].filter(Boolean).join(' ')}>
      {head}
      {pad ? <div className="card-b">{children}</div> : children}
    </section>
  );
}

export const Stub = ({ n }: { n: number }) => (
  <div className="stub" aria-label={`Số ${n}`}><span className="s">Số</span><span className="n">{n}</span></div>
);

export interface Col<T> { head: ReactNode; cell: (row: T) => ReactNode; right?: boolean; width?: number | string }
export function Table<T>({ cols, rows, rowKey, onRow, selected, empty = 'Chưa có dữ liệu' }: {
  cols: Col<T>[]; rows: T[]; rowKey: (r: T) => string | number; onRow?: (r: T) => void; selected?: string | number | null; empty?: ReactNode;
}) {
  if (rows.length === 0) return <div className="empty">{empty}</div>;
  return (
    <div className="tbl-wrap">
      <table className="tbl">
        <thead><tr>{cols.map((c, i) => <th key={i} className={c.right ? 'r' : undefined} style={c.width ? { width: c.width } : undefined}>{c.head}</th>)}</tr></thead>
        <tbody>
          {rows.map((r) => {
            const k = rowKey(r);
            return (
              <tr key={k} className={[onRow && 'click', selected === k && 'sel'].filter(Boolean).join(' ') || undefined}
                onClick={onRow ? () => onRow(r) : undefined} aria-selected={onRow ? selected === k : undefined}>
                {cols.map((c, i) => <td key={i} className={c.right ? 'r' : undefined}>{c.cell(r)}</td>)}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export const Who = ({ name, sub }: { name: ReactNode; sub?: ReactNode }) => (
  <div className="who"><span className="n">{name}</span>{sub && <span className="s">{sub}</span>}</div>
);

export function Field({ label, suffix, warn, className, ...rest }: ComponentProps<'input'> & { label: string; suffix?: string; warn?: boolean }) {
  return (
    <label className={['field', warn && 'warn', className].filter(Boolean).join(' ')}>
      <span>{label}</span>
      <span className="input"><input aria-label={label} {...rest} />{suffix && <span className="sfx">{suffix}</span>}</span>
    </label>
  );
}

export function Area({ label, className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string }) {
  return (
    <label className={['field', className].filter(Boolean).join(' ')}>
      <span>{label}</span>
      <textarea className="input" aria-label={label} {...rest} />
    </label>
  );
}

export function Select({ label, children, className, ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  return (
    <label className={['field', className].filter(Boolean).join(' ')}>
      <span>{label}</span>
      <span className="input"><select aria-label={label} {...rest}>{children}</select></span>
    </label>
  );
}

export function Note({ tone = 'amber', icon = 'alert', children }: { tone?: 'amber' | 'red' | 'teal'; icon?: IconName; children: ReactNode }) {
  return <div className={`note ${tone}`} role={tone === 'red' ? 'alert' : undefined}><Icon name={icon} /><span>{children}</span></div>;
}

export const ErrorNote = ({ text }: { text: string | null | undefined }) => (text ? <Note tone="red">{text}</Note> : null);

export function Tabs<K extends string>({ items, value, onChange }: { items: { key: K; label: ReactNode }[]; value: K; onChange: (k: K) => void }) {
  return (
    <div className="tabs" role="tablist">
      {items.map((t) => (
        <button key={t.key} type="button" role="tab" aria-selected={t.key === value} onClick={() => onChange(t.key)}>{t.label}</button>
      ))}
    </div>
  );
}

export function Seg<K extends string>({ items, value, onChange, label }: { items: { key: K; label: ReactNode }[]; value: K | null; onChange: (k: K) => void; label: string }) {
  return (
    <div className="seg" role="radiogroup" aria-label={label}>
      {items.map((t) => (
        <button key={t.key} type="button" role="radio" aria-checked={t.key === value} onClick={() => onChange(t.key)}>{t.label}</button>
      ))}
    </div>
  );
}

export const Kv = ({ k, v }: { k: ReactNode; v: ReactNode }) => <div className="kv"><span>{k}</span><span>{v}</span></div>;

export function PageHead({ title, sub, actions }: { title: ReactNode; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="head">
      <div><h1>{title}</h1>{sub && <div className="head-sub">{sub}</div>}</div>
      {actions && <div className="wrap" style={{ gap: 10 }}>{actions}</div>}
    </div>
  );
}

export const Loading = () => <div className="empty" role="status">Đang tải…</div>;

export function LoadError({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div className="empty">
      <p>{error instanceof Error ? error.message : 'Không tải được dữ liệu.'}</p>
      {retry && <p style={{ marginTop: 10 }}><Button kind="ghost" sm onClick={retry}>Thử lại</Button></p>}
    </div>
  );
}

export function Check({ label, checked, onChange }: { label: ReactNode; checked: boolean; onChange: (v: boolean) => void }) {
  return <label className="check"><input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} /><span>{label}</span></label>;
}

/* Thông báo nhỏ góc màn hình sau thao tác */
const ToastCtx = createContext<(text: string, err?: boolean) => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [t, setT] = useState<{ text: string; err: boolean; id: number } | null>(null);
  const show = useCallback((text: string, err = false) => {
    const id = Date.now();
    setT({ text, err, id });
    setTimeout(() => setT((cur) => (cur?.id === id ? null : cur)), 3500);
  }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      {t && <div className={`toast${t.err ? ' err' : ''}`} role="status">{t.text}</div>}
    </ToastCtx.Provider>
  );
}
export const useToast = () => useContext(ToastCtx);
