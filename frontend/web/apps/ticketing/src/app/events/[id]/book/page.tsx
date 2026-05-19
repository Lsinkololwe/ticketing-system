'use client';

import React, { useState, useEffect } from 'react';
import { useQuery } from '@apollo/client/react';
import { useRouter } from 'next/navigation';
import {
  Card,
  Box,
  Flex,
  Text,
  Heading,
  Button,
  TextField,
  Select,
  Checkbox,
  Callout,
  Spinner,
  Badge,
  Grid,
  Container,
  Separator,
} from '@radix-ui/themes';
import {
  Calendar,
  MapPin,
  User,
  WarningTriangle,
  Page as TicketIcon,
  Dollar,
  Check,
} from 'iconoir-react';
import { GET_EVENT_BY_ID, useAuth } from '@pml.tickets/shared';
import { Event, EventTicketCategory } from '@/types/event';
import { PaymentMethod, ZambianMobileProvider } from '@/types/payment';
import { usePayment, usePaymentMethods } from '@/hooks/usePayment';

interface TicketBookingPageProps {
  params: {
    id: string;
  };
}

const TicketBookingPage: React.FC<TicketBookingPageProps> = ({ params }) => {
  const router = useRouter();
  const { user, authenticated: isAuthenticated } = useAuth();
  const {
    purchaseTicket,
    isLoading: paymentLoading,
    error: paymentError,
    calculateFees,
    validatePhoneNumber,
    formatPhoneNumber,
    paymentConfig,
  } = usePayment();
  const { getPaymentMethodInfo, getMobileProviderInfo, mobileProviders } = usePaymentMethods();

  const [activeStep, setActiveStep] = useState(0);
  const [selectedCategory, setSelectedCategory] = useState<EventTicketCategory | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(PaymentMethod.MOBILE_MONEY);
  const [mobileProvider, setMobileProvider] = useState<ZambianMobileProvider>(
    ZambianMobileProvider.MTN
  );
  const [phoneNumber, setPhoneNumber] = useState('');
  const [cardDetails, setCardDetails] = useState({
    number: '',
    expiry: '',
    cvv: '',
    name: '',
  });
  // Bank transfer details - not currently implemented but kept for future use
  const [_bankDetails, _setBankDetails] = useState({
    accountNumber: '',
    accountName: '',
    bankName: '',
  });
  const [buyerInfo, setBuyerInfo] = useState({
    name: user?.givenName ? `${user.givenName} ${user.familyName || ''}`.trim() : '',
    email: user?.email || '',
    phone: user?.phoneNumber || '',
  });
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [bookingSuccess, setBookingSuccess] = useState(false);

  // Fetch event data
  const {
    data: eventData,
    loading: eventLoading,
    error: eventError,
  } = useQuery(GET_EVENT_BY_ID, {
    variables: { id: params.id },
    skip: !params.id,
  });

  const event: Event | undefined = (eventData as any)?.event;

  // Calculate totals
  const subtotal = selectedCategory ? selectedCategory.price * quantity : 0;
  const fees = calculateFees(subtotal, paymentMethod);
  const total = subtotal + fees;

  // Handle step navigation
  const handleNext = () => {
    if (activeStep < 2) {
      setActiveStep(activeStep + 1);
    }
  };

  const handlePrev = () => {
    if (activeStep > 0) {
      setActiveStep(activeStep - 1);
    }
  };

  // Handle ticket purchase
  const handlePurchase = async () => {
    if (!selectedCategory || !event) return;

    try {
      const ticketPurchaseData = {
        eventId: event.id,
        ticketCategoryId: selectedCategory.id,
        quantity,
        buyerName: buyerInfo.name,
        buyerEmail: buyerInfo.email,
        buyerPhone: buyerInfo.phone,
        paymentMethod,
        phoneNumber: paymentMethod === PaymentMethod.MOBILE_MONEY ? phoneNumber : undefined,
        mobileProvider: paymentMethod === PaymentMethod.MOBILE_MONEY ? mobileProvider : undefined,
        cardDetails: paymentMethod === PaymentMethod.CARD ? cardDetails : undefined,
        metadata: {
          eventTitle: event.title,
          ticketCategory: selectedCategory.name,
          totalAmount: total,
          fees,
          timestamp: new Date().toISOString(),
        },
      };

      const result = await purchaseTicket(ticketPurchaseData);

      if (result.success) {
        setBookingSuccess(true);
        setActiveStep(3);
      }
    } catch (error) {
      console.error('Purchase error:', error);
    }
  };

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/auth');
    }
  }, [isAuthenticated, router]);

  if (eventLoading) {
    return (
      <Flex
        style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}
        align="center"
        justify="center"
      >
        <Flex direction="column" align="center">
          <Spinner size="3" mb="4" />
          <Text size="4">Loading event details...</Text>
        </Flex>
      </Flex>
    );
  }

  if (eventError || !event) {
    return (
      <Flex
        style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}
        align="center"
        justify="center"
      >
        <Callout.Root color="red" style={{ maxWidth: '400px' }}>
          <Callout.Icon>
            <WarningTriangle />
          </Callout.Icon>
          <Callout.Text>Event not found or error loading event details.</Callout.Text>
        </Callout.Root>
      </Flex>
    );
  }

  const steps = [
    { name: 'Select Tickets', description: 'Choose your ticket category and quantity' },
    { name: 'Payment Method', description: 'Select how you want to pay' },
    { name: 'Review & Pay', description: 'Review your order and complete payment' },
    { name: 'Confirmation', description: 'Your tickets are ready!' },
  ];

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }} py="8">
      <Container size="4">
        <Box style={{ maxWidth: '1200px', margin: '0 auto' }}>
          {/* Header */}
          <Box mb="8">
            <Heading size="7" mb="2">
              Book Tickets
            </Heading>
            <Text size="4" color="gray">
              {event.title}
            </Text>
          </Box>

          {/* Progress Indicator */}
          <Box mb="8">
            <Flex justify="center" align="center" gap="4" wrap="wrap">
              {steps.map((step, index) => (
                <Flex key={step.name} align="center" gap="2">
                  <Flex
                    align="center"
                    justify="center"
                    style={{
                      width: '2rem',
                      height: '2rem',
                      borderRadius: '50%',
                      backgroundColor:
                        index <= activeStep ? 'var(--accent-9)' : 'var(--gray-4)',
                      color: index <= activeStep ? 'white' : 'var(--gray-9)',
                      fontSize: '0.875rem',
                      fontWeight: 500,
                    }}
                  >
                    {index + 1}
                  </Flex>
                  <Box>
                    <Text size="2" weight="medium" style={{ display: 'block' }}>
                      {step.name}
                    </Text>
                  </Box>
                  {index < steps.length - 1 && (
                    <Box
                      style={{
                        width: '2rem',
                        height: '2px',
                        backgroundColor: 'var(--gray-4)',
                      }}
                    />
                  )}
                </Flex>
              ))}
            </Flex>
          </Box>

          <Grid columns={{ initial: '1', lg: '3' }} gap="8">
            {/* Main Content */}
            <Box style={{ gridColumn: 'span 2' }}>
              <Card size="3">
                <Box p="6">
                  {/* Step 1: Select Tickets */}
                  {activeStep === 0 && (
                    <Flex direction="column" gap="6">
                      <Heading size="5">Select Your Tickets</Heading>

                      <Flex direction="column" gap="4">
                        {event.ticketCategories.map((category) => (
                          <Card
                            key={category.id}
                            style={{
                              cursor: 'pointer',
                              border:
                                selectedCategory?.id === category.id
                                  ? '2px solid var(--accent-9)'
                                  : '1px solid var(--gray-4)',
                              backgroundColor:
                                selectedCategory?.id === category.id
                                  ? 'var(--accent-2)'
                                  : 'transparent',
                            }}
                            onClick={() => setSelectedCategory(category)}
                          >
                            <Box p="4">
                              <Flex justify="between" align="start">
                                <Box style={{ flex: 1 }}>
                                  <Flex align="center" gap="3" mb="2">
                                    <Text size="4" weight="bold">
                                      {category.name}
                                    </Text>
                                    {category.isPremium && (
                                      <Badge color="orange">Premium</Badge>
                                    )}
                                  </Flex>

                                  <Text size="2" color="gray" mb="2">
                                    {category.description}
                                  </Text>

                                  <Flex align="center" gap="4">
                                    <Flex align="center" gap="1">
                                      <Dollar style={{ width: '1rem', height: '1rem' }} />
                                      <Text size="2">
                                        {paymentConfig.currency} {category.price}
                                      </Text>
                                    </Flex>
                                    <Flex align="center" gap="1">
                                      <TicketIcon style={{ width: '1rem', height: '1rem' }} />
                                      <Text size="2">{category.available} available</Text>
                                    </Flex>
                                  </Flex>

                                  {category.benefits && category.benefits.length > 0 && (
                                    <Box mt="2">
                                      <Text size="2" color="gray" mb="1">
                                        Benefits:
                                      </Text>
                                      <Flex direction="column" gap="1">
                                        {category.benefits.map((benefit, index) => (
                                          <Flex key={index} align="center" gap="2">
                                            <Check
                                              style={{
                                                width: '0.75rem',
                                                height: '0.75rem',
                                                color: 'var(--green-9)',
                                              }}
                                            />
                                            <Text size="1">{benefit}</Text>
                                          </Flex>
                                        ))}
                                      </Flex>
                                    </Box>
                                  )}
                                </Box>

                                <Box style={{ textAlign: 'right' }}>
                                  <Text size="5" weight="bold" color="iris">
                                    {paymentConfig.currency} {category.price}
                                  </Text>
                                  <Text size="1" color="gray">
                                    per ticket
                                  </Text>
                                </Box>
                              </Flex>
                            </Box>
                          </Card>
                        ))}
                      </Flex>

                      {selectedCategory && (
                        <Box mt="4">
                          <Text size="4" weight="medium" mb="3">
                            Quantity
                          </Text>
                          <Flex align="center" gap="4">
                            <Button
                              variant="outline"
                              onClick={() => setQuantity(Math.max(1, quantity - 1))}
                              disabled={quantity <= 1}
                            >
                              -
                            </Button>
                            <Text size="5" weight="bold" style={{ minWidth: '3rem', textAlign: 'center' }}>
                              {quantity}
                            </Text>
                            <Button
                              variant="outline"
                              onClick={() =>
                                setQuantity(Math.min(selectedCategory.available, quantity + 1))
                              }
                              disabled={quantity >= selectedCategory.available}
                            >
                              +
                            </Button>
                            <Text size="2" color="gray">
                              Max: {selectedCategory.available}
                            </Text>
                          </Flex>
                        </Box>
                      )}

                      <Flex justify="end">
                        <Button size="3" onClick={handleNext} disabled={!selectedCategory}>
                          Continue to Payment
                        </Button>
                      </Flex>
                    </Flex>
                  )}

                  {/* Step 2: Payment Method */}
                  {activeStep === 1 && (
                    <Flex direction="column" gap="6">
                      <Heading size="5">Choose Payment Method</Heading>

                      <Flex direction="column" gap="4">
                        {Object.values(PaymentMethod).map((method) => {
                          const methodInfo = getPaymentMethodInfo(method);
                          return (
                            <Card
                              key={method}
                              style={{
                                cursor: 'pointer',
                                border:
                                  paymentMethod === method
                                    ? '2px solid var(--accent-9)'
                                    : '1px solid var(--gray-4)',
                                backgroundColor:
                                  paymentMethod === method ? 'var(--accent-2)' : 'transparent',
                              }}
                              onClick={() => setPaymentMethod(method)}
                            >
                              <Box p="4">
                                <Flex justify="between" align="center">
                                  <Flex align="center" gap="3">
                                    <Text size="6">{methodInfo.icon}</Text>
                                    <Box>
                                      <Text size="3" weight="bold">
                                        {methodInfo.name}
                                      </Text>
                                      <Text size="2" color="gray">
                                        {methodInfo.description}
                                      </Text>
                                    </Box>
                                  </Flex>
                                  <Box style={{ textAlign: 'right' }}>
                                    <Text size="2" color="gray">
                                      Fee: {(methodInfo.feeRate * 100).toFixed(1)}%
                                    </Text>
                                    {methodInfo.isPopular && (
                                      <Badge color="green" mt="1">
                                        Popular
                                      </Badge>
                                    )}
                                  </Box>
                                </Flex>
                              </Box>
                            </Card>
                          );
                        })}
                      </Flex>

                      {/* Mobile Money Details */}
                      {paymentMethod === PaymentMethod.MOBILE_MONEY && (
                        <Card style={{ backgroundColor: 'var(--gray-2)' }}>
                          <Box p="4">
                            <Text size="4" weight="medium" mb="3">
                              Mobile Money Details
                            </Text>

                            <Grid columns="2" gap="4">
                              <Box>
                                <Text size="2" mb="1">
                                  Mobile Provider
                                </Text>
                                <Select.Root
                                  value={mobileProvider}
                                  onValueChange={(value) =>
                                    setMobileProvider(value as ZambianMobileProvider)
                                  }
                                >
                                  <Select.Trigger />
                                  <Select.Content>
                                    {mobileProviders.map((provider) => {
                                      const providerInfo = getMobileProviderInfo(provider);
                                      return (
                                        <Select.Item key={provider} value={provider}>
                                          {providerInfo.name}
                                        </Select.Item>
                                      );
                                    })}
                                  </Select.Content>
                                </Select.Root>
                              </Box>

                              <Box>
                                <Text size="2" mb="1">
                                  Phone Number
                                </Text>
                                <TextField.Root
                                  placeholder="e.g., 0961234567"
                                  value={phoneNumber}
                                  onChange={(e) => setPhoneNumber(e.target.value)}
                                />
                                {phoneNumber && validatePhoneNumber(phoneNumber, mobileProvider) && (
                                  <Text size="1" color="green" mt="1">
                                    ✓ {formatPhoneNumber(phoneNumber, mobileProvider)}
                                  </Text>
                                )}
                              </Box>
                            </Grid>
                          </Box>
                        </Card>
                      )}

                      {/* Card Details */}
                      {paymentMethod === PaymentMethod.CARD && (
                        <Card style={{ backgroundColor: 'var(--gray-2)' }}>
                          <Box p="4">
                            <Text size="4" weight="medium" mb="3">
                              Card Details
                            </Text>

                            <Flex direction="column" gap="3">
                              <TextField.Root
                                placeholder="Card Number (1234 5678 9012 3456)"
                                value={cardDetails.number}
                                onChange={(e) =>
                                  setCardDetails((prev) => ({ ...prev, number: e.target.value }))
                                }
                              />

                              <Grid columns="2" gap="3">
                                <TextField.Root
                                  placeholder="Expiry (MM/YY)"
                                  value={cardDetails.expiry}
                                  onChange={(e) =>
                                    setCardDetails((prev) => ({ ...prev, expiry: e.target.value }))
                                  }
                                />
                                <TextField.Root
                                  placeholder="CVV (123)"
                                  value={cardDetails.cvv}
                                  onChange={(e) =>
                                    setCardDetails((prev) => ({ ...prev, cvv: e.target.value }))
                                  }
                                />
                              </Grid>

                              <TextField.Root
                                placeholder="Cardholder Name"
                                value={cardDetails.name}
                                onChange={(e) =>
                                  setCardDetails((prev) => ({ ...prev, name: e.target.value }))
                                }
                              />
                            </Flex>
                          </Box>
                        </Card>
                      )}

                      <Flex justify="between">
                        <Button variant="outline" onClick={handlePrev}>
                          Back
                        </Button>
                        <Button size="3" onClick={handleNext}>
                          Continue to Review
                        </Button>
                      </Flex>
                    </Flex>
                  )}

                  {/* Step 3: Review & Pay */}
                  {activeStep === 2 && (
                    <Flex direction="column" gap="6">
                      <Heading size="5">Review Your Order</Heading>

                      {/* Buyer Information */}
                      <Box>
                        <Text size="4" weight="medium" mb="3">
                          Buyer Information
                        </Text>
                        <Grid columns="3" gap="4">
                          <TextField.Root
                            placeholder="Full Name"
                            value={buyerInfo.name}
                            onChange={(e) =>
                              setBuyerInfo((prev) => ({ ...prev, name: e.target.value }))
                            }
                          />
                          <TextField.Root
                            type="email"
                            placeholder="Email"
                            value={buyerInfo.email}
                            onChange={(e) =>
                              setBuyerInfo((prev) => ({ ...prev, email: e.target.value }))
                            }
                          />
                          <TextField.Root
                            placeholder="Phone"
                            value={buyerInfo.phone}
                            onChange={(e) =>
                              setBuyerInfo((prev) => ({ ...prev, phone: e.target.value }))
                            }
                          />
                        </Grid>
                      </Box>

                      {/* Order Summary */}
                      <Box>
                        <Text size="4" weight="medium" mb="3">
                          Order Summary
                        </Text>

                        <Card style={{ backgroundColor: 'var(--gray-2)' }}>
                          <Box p="4">
                            <Flex direction="column" gap="3">
                              <Flex justify="between">
                                <Text>
                                  {selectedCategory?.name} × {quantity}
                                </Text>
                                <Text>
                                  {paymentConfig.currency} {subtotal}
                                </Text>
                              </Flex>
                              <Flex justify="between">
                                <Text>Processing Fee</Text>
                                <Text>
                                  {paymentConfig.currency} {fees}
                                </Text>
                              </Flex>
                              <Separator size="4" />
                              <Flex justify="between">
                                <Text size="4" weight="bold">
                                  Total
                                </Text>
                                <Text size="4" weight="bold">
                                  {paymentConfig.currency} {total}
                                </Text>
                              </Flex>
                            </Flex>
                          </Box>
                        </Card>
                      </Box>

                      {/* Terms and Conditions */}
                      <Flex align="start" gap="2">
                        <Checkbox
                          checked={acceptTerms}
                          onCheckedChange={(checked) => setAcceptTerms(checked as boolean)}
                        />
                        <Text size="2">
                          I agree to the{' '}
                          <Button variant="ghost" size="1" color="iris">
                            Terms of Service
                          </Button>{' '}
                          and{' '}
                          <Button variant="ghost" size="1" color="iris">
                            Refund Policy
                          </Button>
                        </Text>
                      </Flex>

                      {paymentError && (
                        <Callout.Root color="red">
                          <Callout.Icon>
                            <WarningTriangle />
                          </Callout.Icon>
                          <Callout.Text>{paymentError}</Callout.Text>
                        </Callout.Root>
                      )}

                      <Flex justify="between">
                        <Button variant="outline" onClick={handlePrev}>
                          Back
                        </Button>
                        <Button
                          size="3"
                          onClick={handlePurchase}
                          disabled={!acceptTerms || paymentLoading}
                        >
                          {paymentLoading ? (
                            <Flex align="center" gap="2">
                              <Spinner size="1" />
                              Processing Payment...
                            </Flex>
                          ) : (
                            `Pay ${paymentConfig.currency} ${total}`
                          )}
                        </Button>
                      </Flex>
                    </Flex>
                  )}

                  {/* Step 4: Confirmation */}
                  {activeStep === 3 && bookingSuccess && (
                    <Flex direction="column" align="center" gap="6">
                      <Box
                        style={{
                          width: '5rem',
                          height: '5rem',
                          backgroundColor: 'var(--green-3)',
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Check
                          style={{ width: '3rem', height: '3rem', color: 'var(--green-9)' }}
                        />
                      </Box>

                      <Box style={{ textAlign: 'center' }}>
                        <Heading size="5" color="green" mb="2">
                          Payment Successful!
                        </Heading>
                        <Text size="3" color="gray">
                          Your tickets have been booked successfully
                        </Text>
                      </Box>

                      <Card style={{ backgroundColor: 'var(--gray-2)', width: '100%' }}>
                        <Box p="6">
                          <Text size="4" weight="medium" mb="4">
                            Booking Details
                          </Text>
                          <Flex direction="column" gap="2">
                            <Flex justify="between">
                              <Text>Event:</Text>
                              <Text weight="medium">{event.title}</Text>
                            </Flex>
                            <Flex justify="between">
                              <Text>Tickets:</Text>
                              <Text weight="medium">
                                {selectedCategory?.name} × {quantity}
                              </Text>
                            </Flex>
                            <Flex justify="between">
                              <Text>Total Paid:</Text>
                              <Text weight="medium">
                                {paymentConfig.currency} {total}
                              </Text>
                            </Flex>
                          </Flex>
                        </Box>
                      </Card>

                      <Flex gap="4">
                        <Button variant="outline" onClick={() => router.push('/dashboard')}>
                          Go to Dashboard
                        </Button>
                        <Button onClick={() => router.push('/')}>Browse More Events</Button>
                      </Flex>
                    </Flex>
                  )}
                </Box>
              </Card>
            </Box>

            {/* Sidebar */}
            <Box>
              <Card
                size="2"
                style={{
                  position: 'sticky',
                  top: '2rem',
                }}
              >
                <Box
                  p="4"
                  style={{
                    background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
                    borderRadius: 'var(--radius-3) var(--radius-3) 0 0',
                  }}
                >
                  <Text size="4" weight="bold" style={{ color: 'white' }}>
                    Event Details
                  </Text>
                </Box>
                <Box p="4">
                  <Flex direction="column" gap="4">
                    <Flex align="start" gap="3">
                      <Calendar style={{ width: '1.25rem', height: '1.25rem', color: 'var(--gray-9)' }} />
                      <Box>
                        <Text size="2" color="gray">
                          Date & Time
                        </Text>
                        <Text size="2" weight="medium">
                          {new Date(event.eventDateTime).toLocaleDateString('en-US', {
                            weekday: 'long',
                            year: 'numeric',
                            month: 'long',
                            day: 'numeric',
                          })}
                        </Text>
                        <Text size="2" color="gray">
                          {new Date(event.eventDateTime).toLocaleTimeString('en-US', {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </Text>
                      </Box>
                    </Flex>

                    <Flex align="start" gap="3">
                      <MapPin style={{ width: '1.25rem', height: '1.25rem', color: 'var(--gray-9)' }} />
                      <Box>
                        <Text size="2" color="gray">
                          Location
                        </Text>
                        <Text size="2" weight="medium">
                          {event.location.name}
                        </Text>
                        <Text size="2" color="gray">
                          {event.location.city}
                        </Text>
                      </Box>
                    </Flex>

                    <Flex align="start" gap="3">
                      <User style={{ width: '1.25rem', height: '1.25rem', color: 'var(--gray-9)' }} />
                      <Box>
                        <Text size="2" color="gray">
                          Organizer
                        </Text>
                        <Text size="2" weight="medium">
                          {event.organizerName}
                        </Text>
                      </Box>
                    </Flex>

                    {event.images && event.images.length > 0 && (
                      <Box mt="2">
                        <img
                          src={event.images[0]}
                          alt={event.title}
                          style={{
                            width: '100%',
                            height: '8rem',
                            objectFit: 'cover',
                            borderRadius: 'var(--radius-2)',
                          }}
                        />
                      </Box>
                    )}
                  </Flex>
                </Box>
              </Card>
            </Box>
          </Grid>
        </Box>
      </Container>
    </Box>
  );
};

export default TicketBookingPage;
