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
      providers: [provideHttpClient(), provideHttpClientTesting()],
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
    accountsRequest.flush([
      { id: 1, iban: 'DEMO-1001', label: 'Conto principale', currency: 'EUR', balance: 300 },
    ]);

    expect(app.accounts()).toHaveLength(1);
    expect(app.signedIn).toBe(true);
    expect(sessionStorage.getItem('demo-token')).toBe('demo-jwt');
  });

  it('keeps the login visible and reports failed authentication', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.login();
    http
      .expectOne('/api/auth/login')
      .flush({ message: 'Credenziali non valide' }, { status: 401, statusText: 'Unauthorized' });
    expect(app.error()).toBe('Credenziali non valide');
    expect(app.signedIn).toBe(false);
  });

  it('does not enter the authenticated view if login returns no access token', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.login();
    http.expectOne('/api/auth/login').flush({ username: 'alice' });

    expect(app.signedIn).toBe(false);
    expect(app.loading()).toBe(false);
    expect(app.error()).toContain('token valido');
    http.expectNone('/api/accounts');
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
    request.flush([
      {
        id: 9,
        occurredAt: '2026-01-12T10:00:00Z',
        description: 'Accredito',
        type: 'DEPOSIT',
        amount: 80,
      },
    ]);
    expect(app.movements()[0].description).toBe('Accredito');
    expect(app.movements()[0].amount).toBe(80);
  });

  it('submits a confirmed transfer and refreshes account balances', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-jwt');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-1001',
        label: 'Checking',
        holder: 'alice',
        currency: 'EUR',
        balance: 300,
      },
      { id: 2, code: 'DEMO-1002', label: 'Savings', holder: 'alice', currency: 'EUR', balance: 50 },
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
      amount: 25,
    });
    transferRequest.flush({ status: 'COMPLETED' });

    const refreshRequest = http.expectOne('/api/accounts');
    refreshRequest.flush([
      { id: 1, iban: 'DEMO-1001', label: 'Checking', currency: 'EUR', balance: 275 },
      { id: 2, iban: 'DEMO-1002', label: 'Savings', currency: 'EUR', balance: 75 },
    ]);
    expect(app.accounts().map((account) => account.balance)).toEqual([275, 75]);
    expect(app.notice()).toContain('completato');
  });

  it('restores an existing session and requests only the signed-in customer accounts', () => {
    sessionStorage.setItem('demo-token', 'saved-token');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const request = http.expectOne('/api/accounts');
    expect(request.request.headers.get('Authorization')).toBe('Bearer saved-token');
    request.flush([
      { id: 1, iban: 'DEMO-ALICE-001', label: 'Checking', balance: 100, currency: 'EUR' },
    ]);
    expect(fixture.componentInstance.signedIn).toBe(true);
    expect(fixture.componentInstance.accounts()[0].code).toBe('DEMO-ALICE-001');
  });

  it('keeps account state on a server error and exposes a readable message', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-token');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-ALICE-001',
        label: 'Checking',
        holder: 'alice',
        balance: 100,
        currency: 'EUR',
      },
    ]);

    app.loadAccounts();
    http
      .expectOne('/api/accounts')
      .flush({ error: 'Internal server error' }, { status: 500, statusText: 'Server Error' });

    expect(app.accounts()).toHaveLength(1);
    expect(app.accounts()[0].balance).toBe(100);
    expect(app.error()).toBe('Internal server error');
    expect(app.loading()).toBe(false);
    expect(app.signedIn).toBe(true);
  });

  it('shows the empty-account state when the API returns no accounts', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.token.set('demo-token');
    fixture.detectChanges();
    http.expectOne('/api/accounts').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nessun conto disponibile');
  });

  it('shows the no-movements state for an account with an empty history', () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.token.set('demo-token');
    fixture.detectChanges();
    http
      .expectOne('/api/accounts')
      .flush([{ id: 1, iban: 'DEMO-ALICE-001', label: 'Checking', balance: 100, currency: 'EUR' }]);
    fixture.componentInstance.openAccount(fixture.componentInstance.accounts()[0]);
    http.expectOne('/api/accounts/1/movements').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nessun movimento trovato');
  });

  it('clears an expired session and reports the required re-authentication', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('expired-token');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-ALICE-001',
        label: 'Checking',
        holder: 'alice',
        balance: 100,
        currency: 'EUR',
      },
    ]);

    app.loadAccounts();
    http
      .expectOne('/api/accounts')
      .flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(app.signedIn).toBe(false);
    expect(app.accounts()).toEqual([]);
    expect(sessionStorage.getItem('demo-token')).toBeNull();
    expect(app.error()).toContain('Sessione scaduta');
  });

  it('preserves previously loaded movements when a date-filter request fails', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-token');
    app.selectedAccountId.set(1);
    app.movements.set([
      {
        id: 4,
        happenedAt: '2026-01-01T12:00:00Z',
        description: 'Movimento già caricato',
        type: 'DEPOSIT',
        amount: 10,
      },
    ]);
    app.fromDate = '2026-01-31';
    app.toDate = '2026-01-01';

    app.loadMovements();
    http
      .expectOne('/api/accounts/1/movements?from=2026-01-31&to=2026-01-01')
      .flush(
        { error: 'The from date must be on or before the to date' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(app.movements()).toHaveLength(1);
    expect(app.movements()[0].description).toBe('Movimento già caricato');
    expect(app.error()).toContain('from date');
    expect(app.loading()).toBe(false);
  });

  it('requires transfer confirmation before sending a request', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-token');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-ALICE-001',
        label: 'Checking',
        holder: 'alice',
        balance: 100,
        currency: 'EUR',
      },
      {
        id: 2,
        code: 'DEMO-ALICE-002',
        label: 'Savings',
        holder: 'alice',
        balance: 50,
        currency: 'EUR',
      },
    ]);
    app.sourceAccountId = 1;
    app.targetAccountId = 2;
    app.transferAmount = 10;

    app.submitTransfer();
    http.expectNone('/api/transfers');
    expect(app.error()).toContain('Conferma');
    expect(app.submitting()).toBe(false);
  });

  it('keeps the transfer form and balances when the API rejects the transfer', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-token');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-ALICE-001',
        label: 'Checking',
        holder: 'alice',
        balance: 100,
        currency: 'EUR',
      },
      {
        id: 2,
        code: 'DEMO-ALICE-002',
        label: 'Savings',
        holder: 'alice',
        balance: 50,
        currency: 'EUR',
      },
    ]);
    app.openTransfer();
    app.transferAmount = 1000;
    app.confirmTransfer = true;

    app.submitTransfer();
    http
      .expectOne('/api/transfers')
      .flush({ error: 'Insufficient funds' }, { status: 400, statusText: 'Bad Request' });

    expect(app.view()).toBe('transfer');
    expect(app.accounts().map((account) => account.balance)).toEqual([100, 50]);
    expect(app.error()).toBe('Insufficient funds');
    expect(app.submitting()).toBe(false);
  });

  it('clears private state when logging out', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.token.set('demo-token');
    app.userName.set('alice');
    app.accounts.set([
      {
        id: 1,
        code: 'DEMO-ALICE-001',
        label: 'Checking',
        holder: 'alice',
        balance: 100,
        currency: 'EUR',
      },
    ]);
    app.movements.set([
      {
        id: 1,
        happenedAt: '2026-01-01T00:00:00Z',
        description: 'Demo',
        type: 'DEPOSIT',
        amount: 100,
      },
    ]);

    app.logout();

    expect(app.signedIn).toBe(false);
    expect(app.userName()).toBe('');
    expect(app.accounts()).toEqual([]);
    expect(app.movements()).toEqual([]);
    expect(sessionStorage.getItem('demo-token')).toBeNull();
  });
});
