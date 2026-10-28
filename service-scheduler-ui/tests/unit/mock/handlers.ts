import { http, HttpResponse } from 'msw';

export interface DealershipMock {
  id: string;
  name: string;
  timezone: string;
  slotIntervalMinutes: number;
  openTime: string;
  closeTime: string;
  autoConfirm: boolean;
}

export interface VehicleCategoryMock {
  id: string;
  dealershipId: string;
  brand: string;
  model: string;
  type: string;
}

export interface ServiceTypeMock {
  id: string;
  dealershipId: string;
  code: string;
  name: string;
  description: string;
  estimatedMinutes: number;
  allowedBayTypes: string[];
}

export interface PricingEstimateItemMock {
  serviceTypeId: string;
  cost: number;
}

export interface PricingEstimateMock {
  dealershipId: string;
  vehicleCategoryId: string;
  items: PricingEstimateItemMock[];
  totalCost: number;
}

export interface SlotAvailabilityMock {
  startTime: string;
  endTime: string;
  available: boolean;
  capacity: number;
  bookedCount: number;
}

export interface ServiceJobMock {
  id: string;
  serviceTypeId: string;
  bayId: string;
  startTime: string;
  endTime: string;
  status: string;
  baseMinutes: number;
  estimateCost: number;
  notes?: string | null;
}

export interface AppointmentMock {
  id: string;
  dealershipId: string;
  customerId: string;
  vehicleCategoryId: string;
  vehicleVin: string;
  startTime: string;
  endTime: string;
  status: string;
  estimateCost: number;
  totalCost?: number;
  idempotencyKey: string;
  serviceJobs: ServiceJobMock[];
}

export interface CommunicationLogMock {
  id: string;
  customerId: string;
  phone: string;
  channel: string;
  eventType: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface ScheduleJobBlockMock {
  jobId: string;
  appointmentId: string;
  status: string;
  startTime: string;
  endTime: string;
  customerName: string;
  customerPhone: string;
  vehicleVin: string;
  serviceName: string;
}

export interface ScheduleBayColumnMock {
  bayId: string;
  bayName: string;
  bayTypes: string[];
  active: boolean;
  jobs: ScheduleJobBlockMock[];
}

export interface ScheduleDayMock {
  dealershipId: string;
  date: string;
  bays: ScheduleBayColumnMock[];
}

export interface ScheduleHistoryItemMock {
  id: string;
  dealershipId: string;
  customerId: string;
  customerPhone: string;
  customerName: string;
  vehicleVin: string;
  status: string;
  startTime: string;
  endTime: string;
  estimateCost: number;
  totalCost?: number;
}

export const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

export const mockDealerships: DealershipMock[] = [
  {
    id: DEMO_DEALERSHIP_ID,
    name: 'Downtown Service Center',
    timezone: 'Asia/Ho_Chi_Minh',
    slotIntervalMinutes: 30,
    openTime: '08:00',
    closeTime: '17:30',
    autoConfirm: false
  }
];

export const mockVehicleCategories: VehicleCategoryMock[] = [
  {
    id: '00000000-0000-0000-0000-000000000101',
    dealershipId: DEMO_DEALERSHIP_ID,
    brand: 'Toyota',
    model: 'Camry',
    type: 'SEDAN'
  },
  {
    id: '00000000-0000-0000-0000-000000000102',
    dealershipId: DEMO_DEALERSHIP_ID,
    brand: 'Toyota',
    model: 'RAV4',
    type: 'SUV'
  },
  {
    id: '00000000-0000-0000-0000-000000000103',
    dealershipId: DEMO_DEALERSHIP_ID,
    brand: 'Ford',
    model: 'F-150',
    type: 'TRUCK'
  },
  {
    id: '00000000-0000-0000-0000-000000000104',
    dealershipId: DEMO_DEALERSHIP_ID,
    brand: 'Tesla',
    model: 'Model 3',
    type: 'EV'
  }
];

export const mockServiceTypes: ServiceTypeMock[] = [
  {
    id: '00000000-0000-0000-0000-000000000301',
    dealershipId: DEMO_DEALERSHIP_ID,
    code: 'OIL-CHANGE',
    name: 'Standard Oil & Filter Service',
    description: 'Engine oil drainage, filter replacement, fluid top-up',
    estimatedMinutes: 60,
    allowedBayTypes: ['EXPRESS', 'GENERAL']
  },
  {
    id: '00000000-0000-0000-0000-000000000302',
    dealershipId: DEMO_DEALERSHIP_ID,
    code: 'BRAKE-INSPECT',
    name: 'Brake System Inspection & Pad Service',
    description: 'Pad wear check, rotor resurfacing, hydraulic brake fluid flush',
    estimatedMinutes: 90,
    allowedBayTypes: ['GENERAL', 'HEAVY_DUTY']
  }
];

export const mockAppointments: AppointmentMock[] = [
  {
    id: '44444444-0000-0000-0000-000000000001',
    dealershipId: DEMO_DEALERSHIP_ID,
    customerId: '00000000-0000-0000-0000-000000000005',
    vehicleCategoryId: '00000000-0000-0000-0000-000000000101',
    vehicleVin: '1HGCR2F83HA000001',
    startTime: '2026-11-20T08:00:00Z',
    endTime: '2026-11-20T09:00:00Z',
    status: 'CONFIRMED',
    estimateCost: 150.0,
    totalCost: 150.0,
    idempotencyKey: '44444444-0000-0000-0000-100000000001',
    serviceJobs: [
      {
        id: '66666666-0000-0000-0000-000000000001',
        serviceTypeId: '00000000-0000-0000-0000-000000000301',
        bayId: '33333333-0000-0000-0000-000000000001',
        startTime: '2026-11-20T08:00:00Z',
        endTime: '2026-11-20T09:00:00Z',
        status: 'PENDING',
        baseMinutes: 60,
        estimateCost: 150.0
      }
    ]
  },
  {
    id: '44444444-0000-0000-0000-000000000004',
    dealershipId: DEMO_DEALERSHIP_ID,
    customerId: '00000000-0000-0000-0000-000000000005',
    vehicleCategoryId: '00000000-0000-0000-0000-000000000101',
    vehicleVin: '1HGCR2F83HA000004',
    startTime: '2026-11-24T11:00:00Z',
    endTime: '2026-11-24T12:00:00Z',
    status: 'CHECKED_IN',
    estimateCost: 150.0,
    totalCost: 150.0,
    idempotencyKey: '44444444-0000-0000-0000-100000000004',
    serviceJobs: [
      {
        id: '66666666-0000-0000-0000-000000000004',
        serviceTypeId: '00000000-0000-0000-0000-000000000301',
        bayId: '33333333-0000-0000-0000-000000000001',
        startTime: '2026-11-24T11:00:00Z',
        endTime: '2026-11-24T12:00:00Z',
        status: 'IN_PROGRESS',
        baseMinutes: 60,
        estimateCost: 150.0
      }
    ]
  }
];

export const mockCommunicationLogs: CommunicationLogMock[] = [
  {
    id: '88888888-0000-0000-0000-000000000001',
    customerId: '00000000-0000-0000-0000-000000000005',
    phone: '+84900000004',
    channel: 'SSE',
    eventType: 'APPOINTMENT_CONFIRMED',
    payload: {
      appointmentId: '44444444-0000-0000-0000-000000000001',
      status: 'CONFIRMED'
    },
    createdAt: new Date().toISOString()
  }
];

export const mockScheduleBays: ScheduleBayColumnMock[] = [
  {
    bayId: '33333333-0000-0000-0000-000000000001',
    bayName: 'Bay 1 - Express Lube',
    bayTypes: ['QUICK_LUBE'],
    active: true,
    jobs: [
      {
        jobId: '66666666-0000-0000-0000-000000000001',
        appointmentId: '44444444-0000-0000-0000-000000000001',
        status: 'CONFIRMED',
        startTime: '2026-11-20T08:00:00Z',
        endTime: '2026-11-20T09:00:00Z',
        customerName: 'Demo Customer',
        customerPhone: '+84900000004',
        vehicleVin: '1HGCR2F83HA000001',
        serviceName: 'OIL-CHANGE'
      }
    ]
  },
  {
    bayId: '33333333-0000-0000-0000-000000000002',
    bayName: 'Bay 2 - Standard Service',
    bayTypes: ['STANDARD'],
    active: true,
    jobs: []
  }
];

export const mockScheduleHistory: ScheduleHistoryItemMock[] = [
  {
    id: '44444444-0000-0000-0000-000000000001',
    dealershipId: DEMO_DEALERSHIP_ID,
    customerId: '00000000-0000-0000-0000-000000000005',
    customerPhone: '+84900000004',
    customerName: 'Demo Customer',
    vehicleVin: '1HGCR2F83HA000001',
    status: 'CONFIRMED',
    startTime: '2026-11-20T08:00:00Z',
    endTime: '2026-11-20T09:00:00Z',
    estimateCost: 150.0,
    totalCost: 150.0
  },
  {
    id: '44444444-0000-0000-0000-000000000004',
    dealershipId: DEMO_DEALERSHIP_ID,
    customerId: '00000000-0000-0000-0000-000000000005',
    customerPhone: '+84900000004',
    customerName: 'Demo Customer',
    vehicleVin: '1HGCR2F83HA000004',
    status: 'CHECKED_IN',
    startTime: '2026-11-24T11:00:00Z',
    endTime: '2026-11-24T12:00:00Z',
    estimateCost: 150.0,
    totalCost: 150.0
  }
];

export const handlers = [
  http.get('/api/v1/dealerships', () => {
    return HttpResponse.json(mockDealerships);
  }),

  http.get('/api/v1/dealerships/:id/vehicle-categories', ({ params }) => {
    const { id } = params;
    if (id !== DEMO_DEALERSHIP_ID) {
      return HttpResponse.json({ type: 'about:blank', title: 'Dealership Not Found', status: 404 }, { status: 404 });
    }
    const categories = mockVehicleCategories.filter((cat) => cat.dealershipId === id);
    return HttpResponse.json(categories);
  }),

  http.get('/api/v1/dealerships/:id/service-types', ({ params }) => {
    const { id } = params;
    if (id !== DEMO_DEALERSHIP_ID) {
      return HttpResponse.json({ type: 'about:blank', title: 'Dealership Not Found', status: 404 }, { status: 404 });
    }
    const types = mockServiceTypes.filter((st) => st.dealershipId === id);
    return HttpResponse.json(types);
  }),

  http.get('/api/v1/dealerships/:id/pricing/estimate', ({ params, request }) => {
    const { id } = params;
    if (id !== DEMO_DEALERSHIP_ID) {
      return HttpResponse.json({ type: 'about:blank', title: 'Dealership Not Found', status: 404 }, { status: 404 });
    }
    const url = new URL(request.url);
    const serviceTypeIds = url.searchParams.getAll('serviceTypeIds');
    const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const categoryId = url.searchParams.get('categoryId')?.trim();

    if (!categoryId || !UUID_REGEX.test(categoryId) || serviceTypeIds.length === 0) {
      return HttpResponse.json({ type: 'about:blank', title: 'Bad Request', status: 400 }, { status: 400 });
    }

    for (const stId of serviceTypeIds) {
      if (!stId || !UUID_REGEX.test(stId.trim())) {
        return HttpResponse.json({ type: 'about:blank', title: 'Bad Request', status: 400 }, { status: 400 });
      }
    }

    const items = serviceTypeIds.map((stId) => ({
      serviceTypeId: stId.trim(),
      cost: 50.0
    }));
    const totalCost = items.reduce((sum, item) => sum + item.cost, 0);

    const estimate: PricingEstimateMock = {
      dealershipId: String(id),
      vehicleCategoryId: categoryId,
      items,
      totalCost
    };
    return HttpResponse.json(estimate);
  }),

  http.get('/api/v1/slots/available', ({ request }) => {
    const url = new URL(request.url);
    const dealershipId = url.searchParams.get('dealershipId');
    const serviceTypeId = url.searchParams.get('serviceTypeId');
    const date = url.searchParams.get('date');

    if (!dealershipId || !serviceTypeId || !date) {
      return HttpResponse.json({ type: 'about:blank', title: 'Bad Request', status: 400 }, { status: 400 });
    }

    const slots: SlotAvailabilityMock[] = [
      {
        startTime: `${date}T08:00:00Z`,
        endTime: `${date}T09:00:00Z`,
        available: true,
        capacity: 3,
        bookedCount: 0
      },
      {
        startTime: `${date}T09:00:00Z`,
        endTime: `${date}T10:00:00Z`,
        available: false,
        capacity: 3,
        bookedCount: 3
      }
    ];

    return HttpResponse.json(slots);
  }),

  http.post('/api/v1/appointments', async ({ request }) => {
    const idempotencyKey = request.headers.get('X-Idempotency-Key');
    if (!idempotencyKey) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'X-Idempotency-Key header is required' },
        { status: 400 }
      );
    }

    const body = (await request.json()) as any;
    if (!body || !body.dealershipId || !body.vehicleCategoryId || !body.vehicleVin || !body.serviceJobs) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Invalid request body' },
        { status: 400 }
      );
    }

    // Check duplicate key
    const existing = mockAppointments.find((a) => a.idempotencyKey === idempotencyKey);
    if (existing) {
      return HttpResponse.json(existing, { status: 201 });
    }

    const jobs = body.serviceJobs as Array<{ serviceTypeId: string; bayId: string; startTime: string; endTime: string }>;
    // Duration check: if > 60 min for standard service
    for (const job of jobs) {
      const dur = (new Date(job.endTime).getTime() - new Date(job.startTime).getTime()) / 60000;
      if (dur > 60) {
        return HttpResponse.json(
          { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Service job duration exceeds base duration' },
          { status: 400 }
        );
      }
    }

    // Intra-job overlap check
    const sorted = [...jobs].sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
    for (let i = 0; i < sorted.length - 1; i++) {
      if (new Date(sorted[i].endTime) > new Date(sorted[i + 1].startTime)) {
        return HttpResponse.json(
          { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Service job slots overlap within appointment' },
          { status: 400 }
        );
      }
    }

    // Concurrency conflict check
    const vinConflict = mockAppointments.some(
      (a) =>
        a.vehicleVin === body.vehicleVin &&
        a.status !== 'CANCELLED' &&
        new Date(sorted[0].startTime) < new Date(a.endTime) &&
        new Date(sorted[sorted.length - 1].endTime) > new Date(a.startTime)
    );
    if (vinConflict) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Vehicle schedule conflict', status: 409, detail: 'A booking already exists for this vehicle' },
        { status: 409 }
      );
    }

    const newAppt: AppointmentMock = {
      id: `44444444-0000-0000-0000-${String(mockAppointments.length + 1).padStart(12, '0')}`,
      dealershipId: body.dealershipId,
      customerId: '00000000-0000-0000-0000-000000000005',
      vehicleCategoryId: body.vehicleCategoryId,
      vehicleVin: body.vehicleVin,
      startTime: sorted[0].startTime,
      endTime: sorted[sorted.length - 1].endTime,
      status: 'CONFIRMED',
      estimateCost: jobs.length * 60.0,
      totalCost: jobs.length * 60.0,
      idempotencyKey,
      serviceJobs: jobs.map((j, idx) => ({
        id: `66666666-0000-0000-0000-${String(idx + 1).padStart(12, '0')}`,
        serviceTypeId: j.serviceTypeId,
        bayId: j.bayId,
        startTime: j.startTime,
        endTime: j.endTime,
        status: 'PENDING',
        baseMinutes: 30,
        estimateCost: 60.0
      }))
    };
    mockAppointments.push(newAppt);
    return HttpResponse.json(newAppt, { status: 201 });
  }),

  http.get('/api/v1/appointments', () => {
    return HttpResponse.json(mockAppointments);
  }),

  http.get('/api/v1/appointments/:id', ({ params }) => {
    const appt = mockAppointments.find((a) => a.id === params.id);
    if (!appt) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Not Found', status: 404, detail: 'Appointment not found' },
        { status: 404 }
      );
    }
    return HttpResponse.json(appt);
  }),

  http.delete('/api/v1/appointments/:id', ({ params }) => {
    const appt = mockAppointments.find((a) => a.id === params.id);
    if (!appt) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Not Found', status: 404, detail: 'Appointment not found' },
        { status: 404 }
      );
    }
    if (appt.status === 'CHECKED_IN') {
      return HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Unprocessable Content',
          status: 422,
          detail: 'Cannot cancel appointment in status CHECKED_IN. Cancellation permitted prior to check-in only.'
        },
        { status: 422 }
      );
    }
    if (appt.status === 'CANCELLED') {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Appointment is already in terminal state CANCELLED' },
        { status: 400 }
      );
    }
    appt.status = 'CANCELLED';
    return HttpResponse.json({ id: appt.id, status: 'CANCELLED' });
  }),

  http.get('/api/v1/notifications/stream', () => {
    return new HttpResponse(': ping\n\n', {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      }
    });
  }),

  http.get('/api/v1/notifications/my', () => {
    return HttpResponse.json(mockCommunicationLogs);
  }),

  http.get('/api/v1/schedule/day', ({ request }) => {
    const url = new URL(request.url);
    const date = url.searchParams.get('date');
    if (!date) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'date is required for the day schedule' },
        { status: 400 }
      );
    }
    const schedule: ScheduleDayMock = {
      dealershipId: DEMO_DEALERSHIP_ID,
      date,
      bays: mockScheduleBays
    };
    return HttpResponse.json(schedule);
  }),

  http.get('/api/v1/schedule/history', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const customerPhone = url.searchParams.get('customerPhone');
    const items = mockScheduleHistory.filter(
      (item) =>
        (!status || item.status === status) && (!customerPhone || item.customerPhone === customerPhone)
    );
    return HttpResponse.json(items);
  }),

  http.post('/api/v1/jobs/:id/notes', async ({ params, request }) => {
    const body = (await request.json()) as { note?: string };
    if (!body?.note || body.note.trim() === '') {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'note must not be blank' },
        { status: 400 }
      );
    }
    return HttpResponse.json({
      jobId: params.id,
      notes: body.note
    });
  })
];
