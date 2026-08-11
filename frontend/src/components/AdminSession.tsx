import { AlertTriangle, CheckCircle2, KeyRound, LogOut, X } from 'lucide-react';
import type { AdminRole } from '../types';

export type AdminFeedback = { kind: 'success' | 'error'; message: string };

export function AdminActionFeedback({ feedback, onDismiss }: { feedback: AdminFeedback | null; onDismiss: () => void }) {
  if (!feedback) return null;
  return <div className={`admin-feedback ${feedback.kind}`} role={feedback.kind === 'error' ? 'alert' : 'status'}>
    {feedback.kind === 'success' ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
    <span>{feedback.message}</span>
    <button type="button" aria-label="关闭提示" title="关闭提示" onClick={onDismiss}><X size={15} /></button>
  </div>;
}

const roleLabel: Record<AdminRole, string> = {
  ADMIN: '系统管理员',
  DATA_EDITOR: '数据编辑员',
  AUDITOR: '只读审计员'
};

export function AdminSession({ loggedIn, adminName, adminRole, loginForm, loginError, passwordForm, passwordError, onLoginFormChange, onPasswordFormChange, onLogin, onLogout, onChangePassword }: {
  loggedIn: boolean;
  adminName: string;
  adminRole: AdminRole | '';
  loginForm: { username: string; password: string };
  loginError: string;
  passwordForm: { currentPassword: string; newPassword: string };
  passwordError: string;
  onLoginFormChange: (patch: Partial<{ username: string; password: string }>) => void;
  onPasswordFormChange: (patch: Partial<{ currentPassword: string; newPassword: string }>) => void;
  onLogin: () => void;
  onLogout: () => void;
  onChangePassword: () => void;
}) {
  return <section className="panel admin-session">
    {loggedIn ? <>
      <div><strong>已登录</strong><span>{adminName} · {adminRole ? roleLabel[adminRole] : '权限待确认'}</span></div>
      <details className="password-change">
        <summary><KeyRound size={16} />修改密码</summary>
        <div>
          <input value={passwordForm.currentPassword} onChange={(event) => onPasswordFormChange({ currentPassword: event.target.value })} placeholder="当前密码" type="password" autoComplete="current-password" />
          <input value={passwordForm.newPassword} onChange={(event) => onPasswordFormChange({ newPassword: event.target.value })} placeholder="新密码（至少 12 位）" type="password" autoComplete="new-password" />
          <button type="button" onClick={onChangePassword}><KeyRound size={16} />确认修改</button>
          {passwordError && <p className="form-error">{passwordError}</p>}
        </div>
      </details>
      <button type="button" onClick={onLogout}><LogOut size={16} />退出登录</button>
    </> : <>
      <div><strong>管理端登录</strong><span>写入和删除数据前需要登录</span></div>
      <input value={loginForm.username} onChange={(event) => onLoginFormChange({ username: event.target.value })} placeholder="用户名" />
      <input value={loginForm.password} onChange={(event) => onLoginFormChange({ password: event.target.value })} placeholder="密码" type="password" />
      <button type="button" onClick={onLogin}><KeyRound size={16} />登录</button>
    </>}
    {!loggedIn && loginError && <p className="form-error">{loginError}</p>}
  </section>;
}
