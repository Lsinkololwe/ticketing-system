import { bff } from '@/lib/bff';

export const { GET, POST, PUT, PATCH, DELETE } = bff.upstream.rest();
export const dynamic = 'force-dynamic';
