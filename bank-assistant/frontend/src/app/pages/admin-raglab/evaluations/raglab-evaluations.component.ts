import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  RaglabService,
  RaglabEvaluateResponse,
  RaglabHarnessResponse,
  parseRaglabError,
  ParsedRaglabError
} from '../../../services/raglab.service';

type EvalTab = 'evaluate' | 'harness' | 'catalog';

@Component({
  selector: 'app-raglab-evaluations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './raglab-evaluations.component.html',
  styleUrl: './raglab-evaluations.component.css'
})
export class RaglabEvaluationsComponent implements OnInit {
  activeTab: EvalTab = 'evaluate';

  // 1. Evaluate Question Set
  selectedQuestionSet = 'questions.json';
  customQuestionPath = '';
  topK = 20;
  evaluating = false;
  evalResult: RaglabEvaluateResponse | null = null;
  evalError: ParsedRaglabError | null = null;

  // 2. Harness 50
  harnessRunning = false;
  harnessResult: RaglabHarnessResponse | null = null;
  harnessError: ParsedRaglabError | null = null;

  // 3. Catalog
  catalogLoading = false;
  catalogResult: unknown = null;
  catalogError: ParsedRaglabError | null = null;

  constructor(public raglab: RaglabService) {}

  ngOnInit(): void {}

  setTab(tab: EvalTab): void {
    this.activeTab = tab;
  }

  onRunEvaluate(): void {
    if (this.raglab.isSealed()) return;

    const path = this.selectedQuestionSet === 'custom'
      ? this.customQuestionPath.trim()
      : this.selectedQuestionSet;

    if (!path) return;

    this.evaluating = true;
    this.evalError = null;
    this.evalResult = null;

    this.raglab.evaluate({
      questions: path,
      top_k: this.topK
    }).subscribe({
      next: (res) => {
        this.evaluating = false;
        this.evalResult = res;
      },
      error: (err) => {
        this.evaluating = false;
        this.evalError = parseRaglabError(err);
      }
    });
  }

  onRunHarness(): void {
    if (this.raglab.isSealed()) return;

    this.harnessRunning = true;
    this.harnessError = null;
    this.harnessResult = null;

    this.raglab.diagnosticsHarness50().subscribe({
      next: (res) => {
        this.harnessRunning = false;
        this.harnessResult = res;
      },
      error: (err) => {
        this.harnessRunning = false;
        this.harnessError = parseRaglabError(err);
      }
    });
  }

  onLoadCatalog(): void {
    this.catalogLoading = true;
    this.catalogError = null;
    this.catalogResult = null;

    this.raglab.diagnosticsCatalog().subscribe({
      next: (res) => {
        this.catalogLoading = false;
        this.catalogResult = res;
      },
      error: (err) => {
        this.catalogLoading = false;
        this.catalogError = parseRaglabError(err);
      }
    });
  }
}
