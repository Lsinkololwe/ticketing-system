/**
 * Infrastructure module for the Booking Service.
 *
 * Contains external integrations like payment gateways and messaging.
 * Open module allows access from all other modules.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.pml.booking.infrastructure;
