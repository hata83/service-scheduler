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
    const categoryId = url.searchParams.get('categoryId');

    if (!categoryId || serviceTypeIds.length === 0) {
      return HttpResponse.json({ type: 'about:blank', title: 'Bad Request', status: 400 }, { status: 400 });
    }

    const items = serviceTypeIds.map((stId) => ({
      serviceTypeId: stId,
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
  })
];
