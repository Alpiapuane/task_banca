import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.removeItem('demo-token');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.removeItem('demo-token');
  });

  it('shows the demo login without a session', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Accedi al tuo spazio');
    expect(fixture.nativeElement.textContent).toContain('Ambiente dimostrativo');
  });

  it('authenticates, stores the token, and loads only authenticated account data', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    app.login();
    const loginRequest = http.expectOne('/api/auth/login');
    expect(loginRequest.request.method).toBe('POST');
    expect(loginRequest.request.body.username).toBe('alice');
    loginRequest.flush({ accessToken: 'demo-jwt', username: 'alice' });

    const accountsRequest = http.expectOne('/api/accounts');
    expect(accountsRequest.request.headers.get('Authorization')).toBe('Bearer demo-jwt');
    accountsRequest.flush([{ id: 1, iban: 'DEMO-1001', label: 'Conto principale', currency: 'EUR', balance: 300 }]);

    expect(app.accounts()).toHaveLength(1);
    expect(app.signedIn).toBe(true);
    expect(sessionStorage.getItem('demo-token')).toBe('demo-jwt');
  });

  it('keeps the login visible and reports failed authentication', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.login();
    http.expectOne('/api/auth/login').flush(
      { message: 'Credenziali non valide' },
      { status: 401, statusText: 'Unauthorized' }
    );
    expect(app.error()).toBe('Credenziali non valide');
    expect(app.signedIn).toBe(false);
  });

  it('loads movements with the selected date range and authorization header', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-jwt');
    app.selectedAccountId.set(7);
    app.fromDate = '2026-01-01';
    app.toDate = '2026-01-31';

    app.loadMovements();
    const request = http.expectOne('/api/accounts/7/movements?from=2026-01-01&to=2026-01-31');
    expect(request.request.headers.get('Authorization')).toBe('Bearer demo-jwt');
    request.flush([{ id: 9, occurredAt: '2026-01-12T10:00:00Z', description: 'Accredito', type: 'DEPOSIT', amount: 80 }]);
    expect(app.movements()[0].description).toBe('Accredito');
    expect(app.movements()[0].amount).toBe(80);
  });

  it('submits a confirmed transfer and refreshes account balances', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-jwt');
    app.accounts.set([
      { id: 1, code: 'DEMO-1001', label: 'Checking', holder: 'alice', currency: 'EUR', balance: 300 },
      { id: 2, code: 'DEMO-1002', label: 'Savings', holder: 'alice', currency: 'EUR', balance: 50 }
    ]);
    app.sourceAccountId = 1;
    app.targetAccountId = 2;
    app.transferAmount = 25;
    app.confirmTransfer = true;

    app.submitTransfer();
    const transferRequest = http.expectOne('/api/transfers');
    expect(transferRequest.request.headers.get('Authorization')).toBe('Bearer demo-jwt');
    expect(transferRequest.request.body).toEqual({
      sourceAccountId: 1,
      destinationAccountId: 2,
      amount: 25
    });
    transferRequest.flush({ status: 'COMPLETED' });

    const refreshRequest = http.expectOne('/api/accounts');
    refreshRequest.flush([
      { id: 1, iban: 'DEMO-1001', label: 'Checking', currency: 'EUR', balance: 275 },
      { id: 2, iban: 'DEMO-1002', label: 'Savings', currency: 'EUR', balance: 75 }
    ]);
    expect(app.accounts().map((account) => account.balance)).toEqual([275, 75]);
    expect(app.notice()).toContain('completato');
  });
});
