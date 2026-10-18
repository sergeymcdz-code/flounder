import { PosterPage } from '../features/public/PosterPage'
import { lazy, Suspense } from 'react'
import { ToastProvider } from '../components/ui/Toast'
import { Navigate, Route, Routes } from 'react-router'

import { AppLayout } from '../components/AppLayout'
import { RequireRole } from '../components/RequireRole'

import { LegacyEventRedirect, PublicLayout } from '../features/public/PublicLayout'
import { PublicEventPage } from '../features/public/EventPage'
import { StartListPage } from '../features/public/StartListPage'
import { SchedulePage } from '../features/public/SchedulePage'
import { LivePage } from '../features/public/LivePage'
import { ResultsPage } from '../features/public/ResultsPage'
import { PublicRegistrationPage } from '../features/public/RegistrationPage'
import { HeatPage } from '../features/public/HeatPage'

const OrganizerDiplomaPage = lazy(() => import('../features/organizer/OrganizerDiplomaPage').then(module => ({ default: module.OrganizerDiplomaPage })))

const RegistrationReviewPage = lazy(() =>
  import('../features/admin/RegistrationReviewPage').then(module => ({ default: module.RegistrationReviewPage })),
)
const UsersAdminPage = lazy(() => import('../features/admin/UsersAdminPage').then(module => ({ default: module.UsersAdminPage })))
const OrganizationsAdminPage = lazy(() => import('../features/admin/OrganizationsAdminPage').then(module => ({ default: module.OrganizationsAdminPage })))
const OrganizationAdminPage = lazy(() => import('../features/admin/OrganizationsAdminPage').then(module => ({ default: module.OrganizationAdminPage })))
const OrganizationRegistrationReviewPage = lazy(() => import('../features/admin/RegistrationReviewPage').then(module => ({ default: module.OrganizationRegistrationReviewPage })))
const SportsAdminPage = lazy(() =>
  import('../features/admin/SportsAdminPage').then(module => ({ default: module.SportsAdminPage })),
)

const LoginPage = lazy(() =>
  import('../features/auth/LoginPage').then(module => ({ default: module.LoginPage })),
)
const PasswordPage = lazy(() => import('../features/auth/PasswordPage').then(module => ({ default: module.PasswordPage })))
const RegisterPage = lazy(() =>
  import('../features/auth/RegisterPage').then(module => ({ default: module.RegisterPage })),
)

const EventsPage = lazy(() =>
  import('../features/events/EventsPage').then(module => ({ default: module.EventsPage })),
)

const CreateEventPage = lazy(() =>
  import('../features/organizer/CreateEventPage').then(module => ({ default: module.CreateEventPage })),
)
const OrganizerEventsPage = lazy(() =>
  import('../features/organizer/OrganizerEventsPage').then(module => ({ default: module.OrganizerEventsPage })),
)
const OrganizerLayout = lazy(() =>
  import('../features/organizer/OrganizerLayout').then(module => ({ default: module.OrganizerLayout })),
)
const OrganizerRegistrationsPage = lazy(() =>
  import('../features/organizer/OrganizerRegistrationsPage').then(module => ({ default: module.OrganizerRegistrationsPage })),
)
const OrganizerEventLayout = lazy(() =>
  import('../features/organizer/OrganizerEventLayout').then(module => ({ default: module.OrganizerEventLayout })),
)
const NotificationSettingsPage = lazy(() => import('../features/organizer/NotificationSettingsPage').then(module => ({ default: module.NotificationSettingsPage })))
const OrganizerNotificationsPage = lazy(() => import('../features/organizer/OrganizerNotificationsPage').then(module => ({ default: module.OrganizerNotificationsPage })))
const OrganizerOverviewPage = lazy(() =>
  import('../features/organizer/OrganizerOverviewPage').then(module => ({ default: module.OrganizerOverviewPage })),
)
const OrganizerProgramPage = lazy(() =>
  import('../features/organizer/OrganizerProgramPage').then(module => ({ default: module.OrganizerProgramPage })),
)
const OrganizerEntriesPage = lazy(() =>
  import('../features/organizer/OrganizerEntriesPage').then(module => ({ default: module.OrganizerEntriesPage })),
)
const OrganizerHeatsPage = lazy(() =>
  import('../features/organizer/OrganizerHeatsPage').then(module => ({ default: module.OrganizerHeatsPage })),
)
const OrganizerResultsPage = lazy(() =>
  import('../features/organizer/OrganizerResultsPage').then(module => ({ default: module.OrganizerResultsPage })),
)

const OperatorPaymentsPage = lazy(() =>
  import('../features/payments/OperatorPaymentsPage').then(module => ({ default: module.OperatorPaymentsPage })),
)
const OperatorRegistrationPage = lazy(() =>
  import('../features/payments/OperatorRegistrationPage').then(module => ({ default: module.OperatorRegistrationPage })),
)
const PaymentPage = lazy(() =>
  import('../features/payments/PaymentPage').then(module => ({ default: module.PaymentPage })),
)

const MyRegistrationsPage = lazy(() =>
  import('../features/registrations/MyRegistrationsPage').then(module => ({ default: module.MyRegistrationsPage })),
)
const RegistrationPage = lazy(() =>
  import('../features/registrations/RegistrationPage').then(module => ({ default: module.RegistrationPage })),
)

const CreateRegulationPage = lazy(() =>
  import('../features/regulations/CreateRegulationPage').then(module => ({ default: module.CreateRegulationPage })),
)

const CompetitionUnitsPage = lazy(() =>
  import('../features/results/CompetitionUnitsPage').then(module => ({ default: module.CompetitionUnitsPage })),
)
const JudgePanelPage = lazy(() =>
  import('../features/results/JudgePanelPage').then(module => ({ default: module.JudgePanelPage })),
)
const PublicDisciplineResultsPage = lazy(() =>
  import('../features/results/PublicDisciplineResultsPage').then(module => ({ default: module.PublicDisciplineResultsPage })),
)

const EventAnalyticsPage = lazy(() => import('../features/analytics/AnalyticsPages').then(m => ({ default: m.EventAnalyticsPage })))
const OrganizationAnalyticsPage = lazy(() => import('../features/analytics/AnalyticsPages').then(m => ({ default: m.OrganizationAnalyticsPage })))
const OrganizationsAnalyticsListPage = lazy(() => import('../features/analytics/AnalyticsPages').then(m => ({ default: m.OrganizationsAnalyticsListPage })))

export default function App() {
  const routes = (
    <Routes>
      <Route path="e/:slug" element={<PublicLayout />}>
        <Route index element={<PublicEventPage />} />
        <Route path="start-list" element={<StartListPage />} />
        <Route path="schedule" element={<SchedulePage />} />
        <Route path="live" element={<LivePage />} />
        <Route path="poster" element={<PosterPage />} />
        <Route path="results" element={<ResultsPage />} />
        <Route path="register" element={<PublicRegistrationPage />} />
        <Route path="heats/:unitId" element={<HeatPage />} />
      </Route>
      <Route element={<AppLayout />}>
        <Route index element={<EventsPage />} />

        <Route
          path="events/:eventCode"
          element={<LegacyEventRedirect />}
        />

        <Route
          path="events/:publicSlug/disciplines/:disciplineId/results"
          element={<PublicDisciplineResultsPage />}
        />

        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="forgot-password" element={<PasswordPage mode="forgot" />} />
        <Route path="reset-password" element={<PasswordPage mode="reset" />} />
        <Route path="account/password" element={<PasswordPage mode="change" />} />

        <Route element={<RequireRole roles={['participant']} />}>
          <Route
            path="events/:eventCode/disciplines/:disciplineId/register"
            element={<RegistrationPage />}
          />

          <Route
            path="my/registrations"
            element={<MyRegistrationsPage />}
          />

          <Route
            path="my/registrations/:registrationId/payment"
            element={<PaymentPage />}
          />

          <Route
            path="payment-return"
            element={<PaymentPage />}
          />
        </Route>

        <Route
          element={
            <RequireRole
              roles={['platform_admin', 'platformadmin', 'organizer', 'operator', 'judge']}
              deniedMessage="Нет доступа к кабинету организатора."
            />
          }
        >
          <Route path="organizer" element={<OrganizerLayout />}>
            <Route index element={<Navigate replace to="/organizer/events" />} />
            <Route path="notification-settings" element={<NotificationSettingsPage />} />
            <Route element={<RequireRole roles={['organizer', 'platform_admin', 'platformadmin']} />}>
              <Route path="organizations" element={<OrganizationsAnalyticsListPage />} />
              <Route path="organizations/:orgId/analytics" element={<OrganizationAnalyticsPage />} />
            </Route>

            <Route
              path="events"
              element={<OrganizerEventsPage />}
            />

            <Route
              path="events/new"
              element={<CreateEventPage />}
            />

            <Route path="events/:eventId" element={<OrganizerEventLayout />}>
              <Route index element={<OrganizerOverviewPage />} />
              <Route path="program" element={<OrganizerProgramPage />} />
              <Route path="entries" element={<OrganizerEntriesPage />} />
              <Route path="heats" element={<OrganizerHeatsPage />} />
              <Route path="results" element={<OrganizerResultsPage />} />
              <Route path="diploma" element={<OrganizerDiplomaPage />} />
              <Route path="notifications" element={<OrganizerNotificationsPage />} />
              <Route element={<RequireRole roles={['organizer', 'platform_admin', 'platformadmin']} />}>
                <Route path="analytics" element={<EventAnalyticsPage />} />
              </Route>
              <Route path="registrations" element={<OrganizerRegistrationsPage />} />
            </Route>

            <Route
              path="regulations/create"
              element={<CreateRegulationPage />}
            />
          </Route>

          <Route
            path="organizer/registrations/review"
            element={<Navigate replace to="/admin/registrations/review" />} />
        </Route>

        <Route
          element={
            <RequireRole
              roles={[
                'platform_admin',
                'organizer',
                'operator',
              ]}
            />
          }
        >
          <Route
            path="operator/registrations/:registrationId"
            element={<OperatorRegistrationPage />}
          />
        </Route>

        <Route
          element={
            <RequireRole
              roles={['platform_admin', 'platformadmin', 'operator']}
            />
          }
        >
          <Route
            path="operator/payments"
            element={<OperatorPaymentsPage />}
          />

        </Route>

        <Route element={<RequireRole roles={['platformadmin', 'platform_admin', 'operator', 'judge', 'organizer']} />}>
          <Route
            path="operator/results"
            element={<CompetitionUnitsPage />}
          />

          <Route
            path="operator/competition-units/:unitId"
            element={<JudgePanelPage />}
          />
        </Route>

        <Route
          element={
            <RequireRole roles={['platform_admin']} />
          }
        >
          <Route path="admin/users" element={<UsersAdminPage />} />
          <Route path="admin/organizations" element={<OrganizationsAdminPage />} />
          <Route path="admin/organizations/:id" element={<OrganizationAdminPage />} />
          <Route path="admin/registration-requests" element={<OrganizationRegistrationReviewPage />} />
          <Route path="admin/registrations/review" element={<RegistrationReviewPage />} />
          <Route
            path="admin/sports"
            element={<SportsAdminPage />}
          />
        </Route>

        <Route
          path="*"
          element={<Navigate to="/" replace />}
        />
      </Route>
    </Routes>
  )
  return <ToastProvider><Suspense fallback={<p role="status">Загружаем страницу…</p>}>{routes}</Suspense></ToastProvider>
}
