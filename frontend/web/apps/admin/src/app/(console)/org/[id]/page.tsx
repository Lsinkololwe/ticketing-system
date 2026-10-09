import { OrgPage } from '@/features/users/OrgPage';

export default async function OrganizationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OrgPage id={id} />;
}
