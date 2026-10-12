import { Navigate, Route, Routes } from 'react-router'

import { AppLayout } from '../components/AppLayout'
import { RequireRole } from '../components/RequireRole'

import { RegistrationReviewPage } from '../features/admin/RegistrationReviewPage'
import { SportsAdminPage } from '../features/admin/SportsAdminPage'

import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'

import { EventDetailsPage } from '../features/events/EventDetailsPage'
import { EventsPage } from '../features/events/EventsPage'

import { CreateEventPage } from '../features/organizer/CreateEventPage'
import { OrganizerDashboardPage } from '../features/organizer/OrganizerDashboardPage'
import { OrganizerEventsPage } from '../features/organizer/OrganizerEventsPage'
import { OrganizerLayout } from '../features/organizer/OrganizerLayout'
import { OrganizerRegistrationsPage } from '../features/organizer/OrganizerRegistrationsPage'

import { OperatorPaymentsPage } from '../features/payments/OperatorPaymentsPage'
import { OperatorRegistrationPage } from '../features/payments/OperatorRegistrationPage'
import { PaymentPage } from '../features/payments/PaymentPage'

import { MyRegistrationsPage } from '../features/registrations/MyRegistrationsPage'
import { RegistrationPage } from '../features/registrations/RegistrationPage'

import { CreateRegulationPage } from '../features/regulations/CreateRegulationPage'

import { CompetitionUnitsPage } from '../features/results/CompetitionUnitsPage'
import { JudgePanelPage } from '../features/results/JudgePanelPage'
import { PublicDisciplineResultsPage } from '../features/results/PublicDisciplineResultsPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<EventsPage />} />

        <Route
          path="events/:eventCode"
          element={<EventDetailsPage />}
        />

        <Route
          path="events/:publicSlug/disciplines/:disciplineId/results"
          element={<PublicDisciplineResultsPage />}
        />

        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

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
              roles={['platformadmin', 'organizer']}
            />
          }
        >
          <Route path="organizer" element={<OrganizerLayout />}>
            <Route index element={<OrganizerDashboardPage />} />

            <Route
              path="events"
              element={<OrganizerEventsPage />}
            />

            <Route
              path="events/new"
              element={<CreateEventPage />}
            />

            <Route
              path="events/:eventId/registrations"
              element={<OrganizerRegistrationsPage />}
            />

            <Route
              path="regulations/create"
              element={<CreateRegulationPage />}
            />
          </Route>

          <Route
            path="admin/registrations/review"
            element={<RegistrationReviewPage />}
          />
        </Route>

        <Route
          element={
            <RequireRole
              roles={[
                'platformadmin',
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
              roles={['platformadmin', 'operator']}
            />
          }
        >
          <Route
            path="operator/payments"
            element={<OperatorPaymentsPage />}
          />

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
            <RequireRole roles={['platformadmin']} />
          }
        >
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
}