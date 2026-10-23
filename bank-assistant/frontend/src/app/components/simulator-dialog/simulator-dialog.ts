import { Component, EventEmitter, HostListener, Input, OnChanges, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, takeUntil } from 'rxjs';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { ClientService, FinancingSimulationResult } from '../../services/client.service';

/**
 * Financing simulator dialog (P0). The rate is governed data: only products
 * returned by `simulationProducts()` (active rate fact) are offered, and the
 * math is computed server-side (single source of truth, AGENTS.md §10).
 */
@Component({
  selector: 'app-simulator-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe, TranslatePipe],
  templateUrl: './simulator-dialog.html',
  styleUrl: './simulator-dialog.css'
})
export class SimulatorDialogComponent implements OnInit, OnChanges, OnDestroy {
  private readonly api = inject(ClientService);
  readonly i18n = inject(TranslationService);

  @Input() open = false;
  @Output() readonly openChange = new EventEmitter<boolean>();

  products: string[] = [];
  product = '';
  amount = 100000;
  months = 36;

  loadingProducts = false;
  computing = false;
  result: FinancingSimulationResult | null = null;
  error = false;

  private readonly input$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  /** Slider ceiling — the typed input accepts up to the server max. */
  readonly AMOUNT_SLIDER_MAX = 1000000;

  ngOnInit(): void {
    this.input$
      .pipe(debounceTime(400), takeUntil(this.destroy$))
      .subscribe(() => this.compute());
  }

  ngOnChanges(): void {
    if (this.open) {
      this.openDialog();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open) {
      this.close();
    }
  }

  close(): void {
    if (this.open) {
      this.open = false;
      this.openChange.emit(false);
    }
  }

  private openDialog(): void {
    this.product = '';
    this.amount = 100000;
    this.months = 36;
    this.result = null;
    this.error = false;
    this.loadingProducts = true;
    this.api.simulationProducts().subscribe({
      next: products => {
        this.products = products;
        this.loadingProducts = false;
      },
      error: () => {
        this.products = [];
        this.loadingProducts = false;
      }
    });
  }

  onProductChange(p: string): void {
    this.product = p;
    this.result = null;
    if (p) {
      this.input$.next();
    }
  }

  onAmountChange(v: number | null): void {
    this.amount = this.sanitizeAmount(v);
    this.input$.next();
  }

  onMonthsChange(v: number | null): void {
    this.months = v && v >= 12 ? v : 12;
    this.input$.next();
  }

  private sanitizeAmount(v: number | null): number {
    if (v == null || Number.isNaN(v)) {
      return 500;
    }
    return Math.max(500, Math.min(5000000, Math.round(v)));
  }

  private compute(): void {
    if (!this.product || this.computing) {
      return;
    }
    this.computing = true;
    this.error = false;
    this.api.simulateFinancement({ product: this.product, amount: this.amount, months: this.months })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: r => {
          this.result = r;
          this.computing = false;
        },
        error: () => {
          this.error = true;
          this.computing = false;
        }
      });
  }
}
