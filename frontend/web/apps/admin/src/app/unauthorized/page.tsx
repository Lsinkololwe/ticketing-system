/**
 * Unauthorized (403): signed in, but the account holds no platform staff role.
 * The proxy and requireSession() send non-staff here.
 */
import { Button } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';
import { SignOutForm } from '@/components/auth/SignOutForm';

export default function UnauthorizedPage() {
  return (
    <AuthLayout
      product="MyTicketZM"
      console="Platform admin"
      title="You do not have access"
      description="This account is not assigned a platform staff role. Sign in with a staff account, or ask a super admin to grant access."
      footer={
        <>
          Need help? <a href="mailto:support@pml.tickets">Contact support</a>
        </>
      }
    >
      <SignOutForm>
        <Button variant="filled" type="submit" data-testid="unauthorized-switch-account-button">
          Sign in with a different account
        </Button>
      </SignOutForm>
    </AuthLayout>
  );
}
