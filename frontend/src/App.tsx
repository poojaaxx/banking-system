import { Route, Routes } from 'react-router-dom'
import { AdminLayout } from './components/AdminLayout'
import { CustomerLayout } from './components/CustomerLayout'
import { RequireAdmin, RequireCustomer } from './components/RequireAuth'
import { AdminLoginPage } from './pages/auth/AdminLoginPage'
import { CustomerLoginPage } from './pages/auth/CustomerLoginPage'
import { RecoveryLoginPage } from './pages/auth/RecoveryLoginPage'
import { RegisterPage } from './pages/auth/RegisterPage'
import { AccountDetailPage } from './pages/customer/AccountDetailPage'
import { AccountsPage } from './pages/customer/AccountsPage'
import { AboutPage } from './pages/AboutPage'
import { AiAssistantPage } from './pages/customer/AiAssistantPage'
import { BeneficiariesPage } from './pages/customer/BeneficiariesPage'
import { BillsPage } from './pages/customer/BillsPage'
import { BudgetsPage } from './pages/customer/BudgetsPage'
import { DashboardPage } from './pages/customer/DashboardPage'
import { InsightsPage } from './pages/customer/InsightsPage'
import { MoneyRequestsPage } from './pages/customer/MoneyRequestsPage'
import { NotificationsPage } from './pages/customer/NotificationsPage'
import { SavingsGoalsPage } from './pages/customer/SavingsGoalsPage'
import { SecuritySettingsPage } from './pages/customer/SecuritySettingsPage'
import { SupportDetailPage } from './pages/customer/SupportDetailPage'
import { SupportListPage } from './pages/customer/SupportListPage'
import { TransferPage } from './pages/customer/TransferPage'
import { AdminAccountsPage } from './pages/admin/AdminAccountsPage'
import { AdminAlertsPage } from './pages/admin/AdminAlertsPage'
import { AdminAuditPage } from './pages/admin/AdminAuditPage'
import { AdminCustomerDetailPage } from './pages/admin/AdminCustomerDetailPage'
import { AdminCustomersPage } from './pages/admin/AdminCustomersPage'
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminSupportDetailPage } from './pages/admin/AdminSupportDetailPage'
import { AdminSupportListPage } from './pages/admin/AdminSupportListPage'
import { AdminTransactionsPage } from './pages/admin/AdminTransactionsPage'
import { HomeRedirect } from './pages/HomeRedirect'
import { NotFoundPage } from './pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<CustomerLoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/recovery-login" element={<RecoveryLoginPage />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route path="/about" element={<AboutPage />} />

      <Route
        element={
          <RequireCustomer>
            <CustomerLayout />
          </RequireCustomer>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/assistant" element={<AiAssistantPage />} />
        <Route path="/insights" element={<InsightsPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/accounts/:id" element={<AccountDetailPage />} />
        <Route path="/transfer" element={<TransferPage />} />
        <Route path="/money-requests" element={<MoneyRequestsPage />} />
        <Route path="/bills" element={<BillsPage />} />
        <Route path="/savings-goals" element={<SavingsGoalsPage />} />
        <Route path="/budgets" element={<BudgetsPage />} />
        <Route path="/beneficiaries" element={<BeneficiariesPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/support" element={<SupportListPage />} />
        <Route path="/support/:id" element={<SupportDetailPage />} />
        <Route path="/settings/security" element={<SecuritySettingsPage />} />
      </Route>

      <Route
        element={
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/admin/customers" element={<AdminCustomersPage />} />
        <Route path="/admin/customers/:id" element={<AdminCustomerDetailPage />} />
        <Route path="/admin/accounts" element={<AdminAccountsPage />} />
        <Route path="/admin/transactions" element={<AdminTransactionsPage />} />
        <Route path="/admin/support" element={<AdminSupportListPage />} />
        <Route path="/admin/support/:id" element={<AdminSupportDetailPage />} />
        <Route path="/admin/alerts" element={<AdminAlertsPage />} />
        <Route path="/admin/audit" element={<AdminAuditPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
