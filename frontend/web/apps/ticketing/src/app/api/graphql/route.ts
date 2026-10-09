import { bff } from "@/lib/bff";

// Anonymous reads (public catalogue) are forwarded without a token; the gateway guards the rest.
export const { POST } = bff.upstream.graphql({ allowAnonymous: true });
export const dynamic = "force-dynamic";
