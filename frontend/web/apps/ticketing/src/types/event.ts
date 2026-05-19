// Event types based on the GraphQL schema from the backend

export interface Event {
  id: string;
  title: string;
  description: string;
  category: string;
  eventDateTime: string;
  endDateTime: string;
  location: Location;
  organizerId: string;
  organizerName: string;
  status: EventStatus;
  published: boolean;
  publishedAt?: string;
  totalCapacity: number;
  availableTickets: number;
  soldTickets: number;
  ticketCategories: EventTicketCategory[];
  images?: string[];
  tags?: string[];
  additionalInfo?: any;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  lastModifiedBy?: string;
}

export interface Location {
  id: string;
  name: string;
  address: string;
  city: string;
  country: string;
  postalCode?: string;
  coordinates?: Coordinates;
  capacity?: number;
  facilities?: string[];
  images?: string[];
  isActive: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  lastModifiedBy?: string;
}

export interface Coordinates {
  latitude: number;
  longitude: number;
}

export interface EventTicketCategory {
  id: string;
  name: string;
  code: string;
  description?: string;
  price: number;
  currency: string;
  totalQuantity: number;
  capacity: number;
  available: number;
  sold: number;
  isPremium: boolean;
  benefits?: string[];
}

export enum EventStatus {
  DRAFT = 'DRAFT',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  PUBLISHED = 'PUBLISHED',
  CANCELLED = 'CANCELLED',
  COMPLETED = 'COMPLETED'
}

export enum TicketCategory {
  GENERAL = 'GENERAL',
  PRE_SALE = 'PRE_SALE',
  VIP = 'VIP',
  VVIP = 'VVIP',
  PREMIUM = 'PREMIUM',
  EARLY_BIRD = 'EARLY_BIRD',
  STUDENT = 'STUDENT',
  SENIOR = 'SENIOR',
  GROUP = 'GROUP',
  CORPORATE = 'CORPORATE',
  SPONSOR = 'SPONSOR',
  FREE = 'FREE'
}

export interface EventFilter {
  category?: string;
  status?: EventStatus;
  organizerId?: string;
  published?: boolean;
  city?: string;
  country?: string;
  eventDateAfter?: string;
  eventDateBefore?: string;
  createdAfter?: string;
  createdBefore?: string;
  limit?: number;
  offset?: number;
}

export interface EventStats {
  totalEvents: number;
  publishedEvents: number;
  draftEvents: number;
  cancelledEvents: number;
  completedEvents: number;
  totalCapacity: number;
  totalSoldTickets: number;
  totalRevenue: number;
  eventsByCategory: EventCategoryStats[];
  eventsByStatus: EventStatusStats[];
  eventsByOrganizer: EventOrganizerStats[];
  recentEvents: Event[];
}

export interface EventCategoryStats {
  category: string;
  count: number;
  percentage: number;
  totalCapacity: number;
  totalSoldTickets: number;
  totalRevenue: number;
}

export interface EventStatusStats {
  status: EventStatus;
  count: number;
  percentage: number;
}

export interface EventOrganizerStats {
  organizerId: string;
  organizerName: string;
  eventCount: number;
  totalCapacity: number;
  totalSoldTickets: number;
  totalRevenue: number;
}
