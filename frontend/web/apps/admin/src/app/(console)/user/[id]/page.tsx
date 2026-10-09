import { UserProfilePage } from '@/features/users/UserProfilePage';

export default async function UserPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <UserProfilePage id={id} />;
}
