import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import localeIt from '@angular/common/locales/it';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { registerLocaleData } from '@angular/common';

registerLocaleData(localeIt);

interface Account {
  id: number | string;
  code: string;
  label: string;
  holder: string;
  currency: string;
  balance: number;
}

interface Movement {
  id: number | string;
  happenedAt: string;
  description: string;
  type: string;
  amount: number;
  transferReference?: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly accounts = signal<Account[]>([]);
  readonly movements = signal<Movement[]>([]);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly view = signal<'dashboard' | 'account' | 'transfer'>('dashboard');
  readonly selectedAccountId = signal<number | string | null>(null);
  readonly userName = signal('');
  readonly token = signal(sessionStorage.getItem('demo-token') ?? '');

  username = 'alice';
  password = 'Demo1234!';
  fromDate = '';
  toDate = '';
  sourceAccountId: number | string = '';
  targetAccountId: number | string = '';
  transferAmount: number | null = null;
  confirmTransfer = false;

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    if (this.token()) this.loadAccounts();
  }

  get signedIn(): boolean {
    return Boolean(this.token());
  }

  get selectedAccount(): Account | undefined {
    return this.accounts().find((account) => account.id === this.selectedAccountId());
  }

  get totalBalance(): number {
    return this.accounts().reduce((total, account) => total + account.balance, 0);
  }

  get welcomeName(): string {
    return this.accounts()[0]?.holder.trim().split(/\s+/)[0] || this.userName() || 'Demo';
  }

  login(): void {
    this.error.set('');
    this.loading.set(true);
    this.http
      .post<Record<string, unknown>>('/api/auth/login', {
        username: this.username.trim(),
        password: this.password,
      })
      .subscribe({
        next: (response) => {
          const token = String(response['token'] ?? response['accessToken'] ?? '');
          if (!token) {
            this.error.set('La risposta di accesso non contiene un token valido.');
            this.loading.set(false);
            return;
          }
          this.token.set(token);
          this.userName.set(String(response['username'] ?? this.username));
          sessionStorage.setItem('demo-token', token);
          this.loadAccounts();
        },
        error: (error: HttpErrorResponse) =>
          this.fail(error, 'Accesso non riuscito. Controlla le credenziali demo.'),
      });
  }

  logout(): void {
    sessionStorage.removeItem('demo-token');
    this.token.set('');
    this.userName.set('');
    this.accounts.set([]);
    this.movements.set([]);
    this.view.set('dashboard');
    this.error.set('');
    this.notice.set('');
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.error.set('');
    this.http.get<unknown[]>('/api/accounts', { headers: this.authHeaders() }).subscribe({
      next: (items) => {
        this.accounts.set((items ?? []).map((item) => this.toAccount(item)));
        this.loading.set(false);
        if (this.accounts().length === 0)
          this.notice.set('Non ci sono conti associati a questo profilo.');
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'Non è stato possibile caricare i conti.'),
    });
  }

  openAccount(account: Account): void {
    this.selectedAccountId.set(account.id);
    this.view.set('account');
    this.fromDate = '';
    this.toDate = '';
    this.loadMovements();
  }

  loadMovements(): void {
    const accountId = this.selectedAccountId();
    if (accountId === null) return;
    this.loading.set(true);
    this.error.set('');
    const params: Record<string, string> = {};
    if (this.fromDate) params['from'] = this.fromDate;
    if (this.toDate) params['to'] = this.toDate;
    this.http
      .get<unknown[]>(`/api/accounts/${encodeURIComponent(String(accountId))}/movements`, {
        headers: this.authHeaders(),
        params,
      })
      .subscribe({
        next: (items) => {
          this.movements.set((items ?? []).map((item) => this.toMovement(item)));
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) =>
          this.fail(error, 'Non è stato possibile caricare i movimenti.'),
      });
  }

  openTransfer(): void {
    this.error.set('');
    this.notice.set('');
    this.sourceAccountId = this.accounts()[0]?.id ?? '';
    this.targetAccountId = this.accounts()[1]?.id ?? '';
    this.transferAmount = null;
    this.confirmTransfer = false;
    this.view.set('transfer');
  }

  submitTransfer(): void {
    this.error.set('');
    this.notice.set('');
    if (!this.confirmTransfer) {
      this.error.set('Conferma di aver verificato i dati prima di procedere.');
      return;
    }
    this.submitting.set(true);
    this.http
      .post(
        '/api/transfers',
        {
          sourceAccountId: this.sourceAccountId,
          destinationAccountId: this.targetAccountId,
          amount: this.transferAmount,
        },
        { headers: this.authHeaders() },
      )
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.confirmTransfer = false;
          this.notice.set(
            'Trasferimento simulato completato. I saldi e i movimenti sono stati aggiornati.',
          );
          this.view.set('dashboard');
          this.loadAccounts();
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.fail(error, 'Trasferimento non riuscito. Nessuna modifica è stata salvata.');
        },
      });
  }

  backToDashboard(): void {
    this.view.set('dashboard');
    this.error.set('');
  }

  movementLabel(type: string): string {
    const normalized = type.toUpperCase();
    if (
      normalized.includes('DEPOSIT') ||
      normalized.includes('CREDIT') ||
      normalized.includes('IN')
    )
      return 'Entrata';
    if (
      normalized.includes('WITHDRAW') ||
      normalized.includes('DEBIT') ||
      normalized.includes('OUT')
    )
      return 'Uscita';
    return type;
  }

  isCredit(type: string): boolean {
    return /DEPOSIT|CREDIT|IN/i.test(type);
  }

  private authHeaders(): Record<string, string> {
    return { Authorization: `Bearer ${this.token()}` };
  }

  private toAccount(value: unknown): Account {
    const item = value as Record<string, unknown>;
    return {
      id: (item['id'] ?? item['accountId'] ?? '') as number | string,
      code: String(
        item['iban'] ?? item['code'] ?? item['accountCode'] ?? item['accountNumber'] ?? '',
      ),
      label: String(item['label'] ?? item['name'] ?? 'Conto demo'),
      holder: String(
        item['holder'] ?? item['accountHolder'] ?? item['ownerName'] ?? this.userName(),
      ),
      currency: String(item['currency'] ?? 'EUR'),
      balance: Number(item['balance'] ?? 0),
    };
  }

  private toMovement(value: unknown): Movement {
    const item = value as Record<string, unknown>;
    return {
      id: (item['id'] ?? '') as number | string,
      happenedAt: String(
        item['occurredAt'] ?? item['happenedAt'] ?? item['dateTime'] ?? item['date'] ?? '',
      ),
      description: String(item['description'] ?? ''),
      type: String(item['type'] ?? ''),
      amount: Number(item['amount'] ?? 0),
      transferReference: String(item['transferReference'] ?? item['transferId'] ?? ''),
    };
  }

  private fail(error: HttpErrorResponse, fallback: string): void {
    const body = error.error as Record<string, unknown> | null;
    const hadSession = Boolean(this.token());
    this.error.set(String(body?.['message'] ?? body?.['detail'] ?? body?.['error'] ?? fallback));
    this.loading.set(false);
    this.submitting.set(false);
    if (error.status === 401 && hadSession) {
      this.logout();
      this.error.set('Sessione scaduta. Accedi nuovamente per continuare.');
    }
  }
}
