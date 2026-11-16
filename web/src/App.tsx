import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ToastProvider, Loading } from './components/ui';
import { AuthProvider, useAuth } from './lib/auth';
import type { StaffRole } from './lib/types';
import { homeOf, Shell } from './layout/shell';
import { ChangePasswordPage, LoginPage } from './pages/auth';
import { PatientsPage } from './pages/patients';
import { ReceptionHome } from './pages/reception/home';
import { AppointmentsPage } from './pages/reception/appointments';
import { CheckInPage } from './pages/reception/check-in';
import { QueuePage } from './pages/reception/queue';
import { CashierPage } from './pages/reception/cashier';
import { VitalsPage } from './pages/nurse/vitals';
import { LabPage } from './pages/nurse/lab';
import { DoctorTodayPage } from './pages/doctor/today';
import { ExamPage } from './pages/doctor/exam';
import { ReviewPage } from './pages/doctor/review';
import { DoctorSchedulePage } from './pages/doctor/schedule';
import { ReportPage } from './pages/admin/report';
import { SchedulePage } from './pages/admin/schedule';
import { StaffPage } from './pages/admin/staff';
import { MoneyPage } from './pages/admin/money';
import { CatalogPage } from './pages/admin/catalog';
import { FeedbackPage } from './pages/admin/feedback';

const qc = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true, staleTime: 5_000 } } });

/** Chỉ vai được phép mới vào trang; vai khác về trang đầu của mình */
function Only({ roles, children }: { roles: StaffRole[]; children: ReactNode }) {
  const { me } = useAuth();
  if (!me) return null;
  return roles.some((r) => me.roles.includes(r)) ? <>{children}</> : <Navigate to={homeOf(me.roles)} replace />;
}

function Gate() {
  const { me, loading } = useAuth();
  if (loading) return <Loading />;
  if (!me) return <Routes><Route path="*" element={<LoginPage />} /></Routes>;
  if (me.mustChangePassword) return <ChangePasswordPage forced />;
  const R = (path: string, roles: StaffRole[], el: ReactNode) => <Route path={path} element={<Only roles={roles}>{el}</Only>} />;
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Navigate to={homeOf(me.roles)} replace />} />
        <Route path="dang-nhap" element={<Navigate to={homeOf(me.roles)} replace />} />
        <Route path="doi-mat-khau" element={<ChangePasswordPage />} />
        <Route path="benh-nhan" element={<PatientsPage />} />
        {R('le-tan', ['RECEPTIONIST', 'ADMIN'], <ReceptionHome />)}
        {R('le-tan/lich-hen', ['RECEPTIONIST', 'ADMIN'], <AppointmentsPage />)}
        {R('le-tan/tiep-don', ['RECEPTIONIST', 'ADMIN'], <CheckInPage />)}
        {R('le-tan/hang-doi', ['RECEPTIONIST', 'ADMIN'], <QueuePage />)}
        {R('le-tan/thu-ngan', ['RECEPTIONIST', 'ADMIN'], <CashierPage />)}
        {R('dieu-duong/sinh-hieu', ['NURSE', 'DOCTOR'], <VitalsPage />)}
        {R('dieu-duong/xet-nghiem', ['NURSE'], <LabPage />)}
        {R('bac-si', ['DOCTOR'], <DoctorTodayPage />)}
        {R('bac-si/kham/:id', ['DOCTOR'], <ExamPage />)}
        {R('bac-si/duyet-ket-qua', ['DOCTOR'], <ReviewPage />)}
        {R('bac-si/lich', ['DOCTOR'], <DoctorSchedulePage />)}
        {R('quan-ly/bao-cao', ['ADMIN'], <ReportPage />)}
        {R('quan-ly/lich', ['ADMIN'], <SchedulePage />)}
        {R('quan-ly/nhan-vien', ['ADMIN'], <StaffPage />)}
        {R('quan-ly/thu-chi', ['ADMIN'], <MoneyPage />)}
        {R('quan-ly/danh-muc', ['ADMIN'], <CatalogPage />)}
        {R('quan-ly/phan-hoi', ['ADMIN'], <FeedbackPage />)}
        <Route path="*" element={<Navigate to={homeOf(me.roles)} replace />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <AuthProvider>
          <BrowserRouter>
            <Gate />
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  );
}
